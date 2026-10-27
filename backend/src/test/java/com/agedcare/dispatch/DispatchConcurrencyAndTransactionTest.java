package com.agedcare.dispatch;

import com.agedcare.common.enums.BookingStatus;
import com.agedcare.common.enums.DispatchStatus;
import com.agedcare.common.enums.OrderStatus;
import com.agedcare.common.enums.UserRole;
import com.agedcare.common.exception.BusinessException;
import com.agedcare.common.security.LoginUser;
import com.agedcare.common.security.UserContext;
import com.agedcare.modules.dispatch.entity.DispatchAttempt;
import com.agedcare.modules.dispatch.entity.DispatchScore;
import com.agedcare.modules.dispatch.entity.StaffBooking;
import com.agedcare.modules.dispatch.mapper.DispatchAttemptMapper;
import com.agedcare.modules.dispatch.mapper.DispatchScoreMapper;
import com.agedcare.modules.dispatch.mapper.StaffBookingMapper;
import com.agedcare.modules.dispatch.service.DispatchService;
import com.agedcare.modules.order.entity.OrderStatusLog;
import com.agedcare.modules.order.entity.ServiceOrder;
import com.agedcare.modules.order.mapper.OrderStatusLogMapper;
import com.agedcare.modules.order.mapper.ServiceOrderMapper;
import com.agedcare.modules.staff.entity.StaffProfile;
import com.agedcare.modules.staff.mapper.StaffProfileMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

/**
 * 调度系统高并发竞态与事务一致性深度测试:
 * 1. 两人同时抢接同一单 (悲观锁 + 乐观 CAS 双重拦截)
 * 2. 接单获胜场景 (提前接单，超时检查短路跳过并保留确认态)
 * 3. 超时获胜场景 (截止时间已过，超时调度真正执行：置 EXPIRED、释放 StaffBooking 并重派，拦截迟到接单)
 * 4. 临界时刻并发竞态 (接单与超时同时到达，严格互斥排队)
 * 5. 写入后事务回滚 (验证数据写入 dispatch_attempt/staff_booking 后发生异常，全部原子回滚)
 */
@SpringBootTest
public class DispatchConcurrencyAndTransactionTest extends com.agedcare.IsolatedIntegrationTest {

    @Autowired
    private DispatchService dispatchService;

    @Autowired
    private ServiceOrderMapper serviceOrderMapper;

    @Autowired
    private DispatchAttemptMapper dispatchAttemptMapper;

    @Autowired
    private StaffBookingMapper staffBookingMapper;

    @Autowired
    private StaffProfileMapper staffProfileMapper;

    @Autowired
    private DispatchScoreMapper dispatchScoreMapper;

    @SpyBean
    private OrderStatusLogMapper orderStatusLogMapper;

    @SpyBean private com.agedcare.modules.dispatch.service.DispatchScoreEngine engine;

    private final List<Long> createdOrderIds = new ArrayList<>();
    private final List<Long> createdAttemptIds = new ArrayList<>();
    private final List<Long> createdBookingIds = new ArrayList<>();
    private final List<Long> createdStaffIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        UserContext.clear();
        reset(orderStatusLogMapper, engine);

        for (Long bId : createdBookingIds) {
            staffBookingMapper.deleteById(bId);
        }
        for (Long aId : createdAttemptIds) {
            dispatchAttemptMapper.deleteById(aId);
        }
        for (Long oId : createdOrderIds) {
            dispatchScoreMapper.delete(new LambdaQueryWrapper<DispatchScore>().eq(DispatchScore::getOrderId, oId));
            orderStatusLogMapper.delete(new LambdaQueryWrapper<OrderStatusLog>().eq(OrderStatusLog::getOrderId, oId));
            serviceOrderMapper.deleteById(oId);
        }
        for (Long sId : createdStaffIds) {
            staffProfileMapper.deleteById(sId);
        }
    }

    /**
     * 动态创建隔离的测试服务人员，避免依赖生产/演示数据库预设数据
     */
    private StaffProfile createIsolatedTestStaff() {
        long testUserId = 880000L + System.currentTimeMillis() % 100000L + ThreadLocalRandom.current().nextInt(1000);
        StaffProfile staff = StaffProfile.builder()
                .userId(testUserId)
                .serviceArea("全网格")
                .baseStationName("测试服务站")
                .baseLatitude(BigDecimal.valueOf(39.9042))
                .baseLongitude(BigDecimal.valueOf(116.4074))
                .currentStatus("AVAILABLE")
                .auditStatus("APPROVED")
                .priorRating(BigDecimal.valueOf(4.80))
                .totalOrders(5)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        staffProfileMapper.insert(staff);
        createdStaffIds.add(staff.getId());
        return staff;
    }

    private ServiceOrder createTestOrder(String orderNo, String status) {
        ServiceOrder order = ServiceOrder.builder()
                .orderNo(orderNo)
                .requesterUserId(5L)
                .elderId(201L)
                .serviceItemId(301L)
                .serviceNameSnapshot("居家照料并发与事务测试")
                .priceSnapshot(new BigDecimal("99.00"))
                .durationMinutesSnapshot(60)
                .plannedStartAt(LocalDateTime.now().plusHours(2))
                .plannedEndAt(LocalDateTime.now().plusHours(3))
                .addressSnapshot("北京市海淀区中关村南大街测试路")
                .contactNameSnapshot("张大爷")
                .contactPhoneSnapshot("13800000001")
                .status(status)
                .dispatchRound(1)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        serviceOrderMapper.insert(order);
        createdOrderIds.add(order.getId());
        return order;
    }

    private DispatchAttempt createTestAttempt(Long orderId, Long staffId, LocalDateTime deadline) {
        DispatchAttempt attempt = DispatchAttempt.builder()
                .orderId(orderId)
                .dispatchRound(1)
                .attemptNo(1)
                .staffId(staffId)
                .dispatchType("AUTO")
                .deadline(deadline)
                .status(DispatchStatus.PENDING.getCode())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        dispatchAttemptMapper.insert(attempt);
        createdAttemptIds.add(attempt.getId());
        return attempt;
    }

    private StaffBooking createTestBooking(Long staffId, Long orderId, Long dispatchId) {
        StaffBooking booking = StaffBooking.builder()
                .staffId(staffId)
                .orderId(orderId)
                .dispatchId(dispatchId)
                .startTime(LocalDateTime.now().plusHours(1).plusMinutes(45))
                .endTime(LocalDateTime.now().plusHours(3))
                .status(BookingStatus.HELD.getCode())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        staffBookingMapper.insert(booking);
        createdBookingIds.add(booking.getId());
        return booking;
    }

    @Test
    @DisplayName("并发测试1: 两人同时确认接单，行级排他锁与CAS确保有且仅有1人成功，另1人被安全拦截")
    public void testConcurrentAcceptDispatchRaceCondition() throws Exception {
        StaffProfile staff = createIsolatedTestStaff();
        ServiceOrder order = createTestOrder("ORD_CONC_" + System.currentTimeMillis(), OrderStatus.WAIT_ACCEPT.getCode());
        DispatchAttempt attempt = createTestAttempt(order.getId(), staff.getId(), LocalDateTime.now().plusMinutes(5));
        StaffBooking booking = createTestBooking(staff.getId(), order.getId(), attempt.getId());

        order.setCurrentDispatchId(attempt.getId());
        serviceOrderMapper.updateById(order);

        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CyclicBarrier barrier = new CyclicBarrier(threads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        List<String> failReasons = new CopyOnWriteArrayList<>();

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    UserContext.setUser(LoginUser.builder()
                            .id(staff.getUserId())
                            .username("staff_" + staff.getId())
                            .role(UserRole.STAFF.getCode())
                            .build());

                    barrier.await(5, TimeUnit.SECONDS);

                    dispatchService.acceptDispatch(attempt.getId());
                    successCount.incrementAndGet();
                } catch (BusinessException be) {
                    failCount.incrementAndGet();
                    failReasons.add(be.getMessage());
                } catch (Exception e) {
                    failCount.incrementAndGet();
                    failReasons.add(e.getClass().getSimpleName() + ": " + e.getMessage());
                } finally {
                    UserContext.clear();
                }
            });
        }

        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));

        assertEquals(1, successCount.get(), "并发接单必须仅有1个请求成功");
        assertEquals(1, failCount.get(), "另一个并发接单必须被拦截");
        assertTrue(failReasons.get(0).contains("非待接单") || failReasons.get(0).contains("并发修改") || failReasons.get(0).contains("无法接单"),
                "被拦截原因应提示并发冲突或状态变更: " + failReasons.get(0));

        ServiceOrder updatedOrder = serviceOrderMapper.selectById(order.getId());
        assertEquals(OrderStatus.WAIT_VISIT.getCode(), updatedOrder.getStatus());
        DispatchAttempt updatedAttempt = dispatchAttemptMapper.selectById(attempt.getId());
        assertEquals(DispatchStatus.ACCEPTED.getCode(), updatedAttempt.getStatus());
        StaffBooking updatedBooking = staffBookingMapper.selectById(booking.getId());
        assertEquals(BookingStatus.CONFIRMED.getCode(), updatedBooking.getStatus());
    }

    @Test
    @DisplayName("超时测试2: 截止时间已过(deadline<=now)，真正进入超时分支：置EXPIRED、释放预留并重派，拦截迟到接单")
    public void testTimeoutBranchExecutionAndResourceRelease() {
        StaffProfile staff = createIsolatedTestStaff();
        ServiceOrder order = createTestOrder("ORD_TIMEOUT_BRANCH_" + System.currentTimeMillis(), OrderStatus.WAIT_ACCEPT.getCode());

        // 构造真正的超时时间：截止时间已在 10 秒前过去
        LocalDateTime pastDeadline = LocalDateTime.now().minusSeconds(10);
        DispatchAttempt attempt = createTestAttempt(order.getId(), staff.getId(), pastDeadline);
        StaffBooking booking = createTestBooking(staff.getId(), order.getId(), attempt.getId());

        order.setCurrentDispatchId(attempt.getId());
        serviceOrderMapper.updateById(order);

        // 调用超时调度逻辑
        dispatchService.handleTimeoutAttempt(attempt);

        // 验证超时真正执行成功:
        // 1. 本次尝试状态必须置为 EXPIRED
        DispatchAttempt expiredAttempt = dispatchAttemptMapper.selectById(attempt.getId());
        assertEquals(DispatchStatus.EXPIRED.getCode(), expiredAttempt.getStatus(), "超时后本次尝试状态必须为 EXPIRED");
        assertNotNull(expiredAttempt.getExpiredAt(), "应记录超时生效时间");

        // 2. 原排班预留必须释放为 RELEASED
        StaffBooking releasedBooking = staffBookingMapper.selectById(booking.getId());
        assertEquals(BookingStatus.RELEASED.getCode(), releasedBooking.getStatus(), "超时后排班预留必须释放为 RELEASED");

        // 3. 验证迟到的接单请求必须被坚决拦截
        UserContext.setUser(LoginUser.builder()
                .id(staff.getUserId())
                .username("staff_" + staff.getId())
                .role(UserRole.STAFF.getCode())
                .build());

        BusinessException be = assertThrows(BusinessException.class, () -> {
            dispatchService.acceptDispatch(attempt.getId());
        }, "已超时的派单绝对不允许被接单");
        assertTrue(be.getMessage().contains("非待接单") || be.getMessage().contains("失效") || be.getMessage().contains("超时"),
                "拒绝接单应提示派单已超时或状态不符: " + be.getMessage());
    }

    @Test
    @DisplayName("接单获胜测试3: 在截止时间前接单，随后超时调度器到达时安全短路跳过，预留保持CONFIRMED")
    public void testAcceptWinsAndShortCircuitsTimeout() {
        StaffProfile staff = createIsolatedTestStaff();
        ServiceOrder order = createTestOrder("ORD_ACCEPT_WINS_" + System.currentTimeMillis(), OrderStatus.WAIT_ACCEPT.getCode());
        DispatchAttempt attempt = createTestAttempt(order.getId(), staff.getId(), LocalDateTime.now().plusMinutes(3));
        StaffBooking booking = createTestBooking(staff.getId(), order.getId(), attempt.getId());

        order.setCurrentDispatchId(attempt.getId());
        serviceOrderMapper.updateById(order);

        // 服务人员在超时前成功接单
        UserContext.setUser(LoginUser.builder()
                .id(staff.getUserId())
                .username("staff_" + staff.getId())
                .role(UserRole.STAFF.getCode())
                .build());
        dispatchService.acceptDispatch(attempt.getId());

        // 模拟超时扫描定时任务随后执行该 attempt
        dispatchService.handleTimeoutAttempt(attempt);

        // 状态必须保持已接单状态，超时调度器绝不可回退或释放已接单预留
        ServiceOrder finalOrder = serviceOrderMapper.selectById(order.getId());
        assertEquals(OrderStatus.WAIT_VISIT.getCode(), finalOrder.getStatus());
        DispatchAttempt finalAttempt = dispatchAttemptMapper.selectById(attempt.getId());
        assertEquals(DispatchStatus.ACCEPTED.getCode(), finalAttempt.getStatus());
        StaffBooking finalBooking = staffBookingMapper.selectById(booking.getId());
        assertEquals(BookingStatus.CONFIRMED.getCode(), finalBooking.getStatus(), "已接单的预留不可被超时逻辑错误释放");
    }

    @Test
    @DisplayName("事务回滚测试4: 派单数据(评分/尝试/预留)写入数据库后发生异常，验证整个事务彻底回滚无残留")
    public void testTransactionalRollbackAfterDatabaseWrites() {
        StaffProfile staff = createIsolatedTestStaff();
        ServiceOrder order = createTestOrder("ORD_WRITE_ROLLBACK_" + System.currentTimeMillis(), OrderStatus.WAIT_DISPATCH.getCode());

        var candidate = com.agedcare.modules.dispatch.dto.CandidateEvaluationResult.builder()
                .staffId(staff.getId()).qualified(true).metricD(BigDecimal.ONE).metricQ(BigDecimal.ONE)
                .metricL(BigDecimal.ONE).metricF(BigDecimal.ZERO).totalScore(BigDecimal.ONE).detailsJson("{}").build();
        org.mockito.Mockito.doReturn(List.of(candidate)).when(engine).evaluateAllCandidates(any(ServiceOrder.class));
        org.mockito.Mockito.doReturn(candidate).when(engine).evaluateSingleCandidate(any(StaffProfile.class), any(ServiceOrder.class));
        AtomicInteger observedWrites = new AtomicInteger();
        org.mockito.Mockito.doAnswer(invocation -> {
            assertTrue(dispatchAttemptMapper.selectCount(new LambdaQueryWrapper<DispatchAttempt>().eq(DispatchAttempt::getOrderId, order.getId())) > 0);
            assertTrue(staffBookingMapper.selectCount(new LambdaQueryWrapper<StaffBooking>().eq(StaffBooking::getOrderId, order.getId())) > 0);
            assertTrue(dispatchScoreMapper.selectCount(new LambdaQueryWrapper<DispatchScore>().eq(DispatchScore::getOrderId, order.getId())) > 0);
            observedWrites.incrementAndGet();
            throw new RuntimeException("Simulated status log disk write failure");
        }).when(orderStatusLogMapper).insert(any(OrderStatusLog.class));

        // 发起人工指派派单，在写入 dispatch_attempt 和 staff_booking 之后，因状态日志异常触发整体事务回滚
        assertThrows(RuntimeException.class, () -> {
            dispatchService.dispatchOrder(order.getId(), staff.getId(), "MANUAL");
        });

        assertEquals(1, observedWrites.get(), "必须先看到三类实际写入，才能验证回滚");

        // 彻底验证数据库持久化层事务回滚:
        // 1. 尝试记录必须回滚为 0 条
        Long attemptCount = dispatchAttemptMapper.selectCount(new LambdaQueryWrapper<DispatchAttempt>()
                .eq(DispatchAttempt::getOrderId, order.getId()));
        assertEquals(0L, attemptCount, "发生异常时，已写入的派单尝试记录必须彻底回滚 (原子性保证)");

        // 2. 排班预留必须回滚为 0 条
        Long bookingCount = staffBookingMapper.selectCount(new LambdaQueryWrapper<StaffBooking>()
                .eq(StaffBooking::getOrderId, order.getId()));
        assertEquals(0L, bookingCount, "发生异常时，已写入的排班预留必须彻底回滚，绝不可残留孤立预约");

        // 3. 评分记录必须回滚为 0 条
        Long scoreCount = dispatchScoreMapper.selectCount(new LambdaQueryWrapper<DispatchScore>()
                .eq(DispatchScore::getOrderId, order.getId()));
        assertEquals(0L, scoreCount, "发生异常时，已持久化的候选打分记录必须彻底回滚");

        // 4. 订单状态必须还原为 WAIT_DISPATCH，未被错误串改为 WAIT_ACCEPT
        ServiceOrder dbOrder = serviceOrderMapper.selectById(order.getId());
        assertEquals(OrderStatus.WAIT_DISPATCH.getCode(), dbOrder.getStatus(), "订单状态必须保持原有 WAIT_DISPATCH");
        assertNull(dbOrder.getCurrentDispatchId(), "订单当前派单关联必须保持为 NULL");
    }
    @Test void expiredDispatchCommitsManualQueueAndLogInMysql() {
        ServiceOrder order = createTestOrder("EXPIRED_" + System.nanoTime(), "WAIT_DISPATCH");
        order.setPlannedEndAt(LocalDateTime.now().minusMinutes(1));
        serviceOrderMapper.updateById(order);
        assertNull(dispatchService.dispatchOrder(order.getId(), null, "AUTO"));
        assertEquals("WAIT_MANUAL", serviceOrderMapper.selectById(order.getId()).getStatus());
        assertEquals(1L, orderStatusLogMapper.selectCount(new LambdaQueryWrapper<OrderStatusLog>().eq(OrderStatusLog::getOrderId, order.getId()).eq(OrderStatusLog::getToStatus, "WAIT_MANUAL")));
        assertNull(dispatchService.dispatchOrder(order.getId(), null, "AUTO"));
        assertEquals(1L, orderStatusLogMapper.selectCount(new LambdaQueryWrapper<OrderStatusLog>().eq(OrderStatusLog::getOrderId, order.getId())));
    }

}
