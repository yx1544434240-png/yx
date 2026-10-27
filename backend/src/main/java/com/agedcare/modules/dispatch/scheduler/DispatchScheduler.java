package com.agedcare.modules.dispatch.scheduler;

import com.agedcare.common.enums.DispatchStatus;
import com.agedcare.common.enums.OrderStatus;
import com.agedcare.common.config.DispatchClusterLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import com.agedcare.modules.dispatch.entity.DispatchAttempt;
import com.agedcare.modules.dispatch.mapper.DispatchAttemptMapper;
import com.agedcare.modules.dispatch.service.DispatchService;
import com.agedcare.modules.order.entity.ServiceOrder;
import com.agedcare.modules.order.mapper.ServiceOrderMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class DispatchScheduler {

    private final DispatchAttemptMapper dispatchAttemptMapper;
    private final ServiceOrderMapper serviceOrderMapper;
    private final DispatchService dispatchService;
    private final DispatchClusterLock clusterLock;

    /**
     * 每10秒执行一次: 扫描接单超时任务并触发重派
     */
    @Scheduled(fixedDelayString = "${dispatch.scan-interval-seconds:10}000")
    public void scanTimeouts() {
        clusterLock.run(this::scanLocked);
    }

    private void scanLocked() {
        LocalDateTime now = LocalDateTime.now();
        List<DispatchAttempt> expiredAttempts = dispatchAttemptMapper.selectList(new LambdaQueryWrapper<DispatchAttempt>()
                .eq(DispatchAttempt::getStatus, DispatchStatus.PENDING.getCode())
                .le(DispatchAttempt::getDeadline, now).last("LIMIT 200"));

        if (!expiredAttempts.isEmpty()) {
            log.info("检测到 {} 笔接单超时派单任务，开始处理超时与重派...", expiredAttempts.size());
            for (DispatchAttempt attempt : expiredAttempts) {
                try {
                    dispatchService.handleTimeoutAttempt(attempt);
                } catch (Exception e) {
                    log.error("处理超时派单异常: attemptId=" + attempt.getId(), e);
                }
            }
        }

        // 扫描待派单状态(WAIT_DISPATCH)且未指派派单记录的订单，触发自动派单
        List<ServiceOrder> waitingOrders = serviceOrderMapper.selectList(new LambdaQueryWrapper<ServiceOrder>()
                .eq(ServiceOrder::getStatus, OrderStatus.WAIT_DISPATCH.getCode())
                .isNull(ServiceOrder::getCurrentDispatchId)
                .orderByAsc(ServiceOrder::getPlannedStartAt)
                .orderByAsc(ServiceOrder::getCreatedAt).last("LIMIT 200"));

        for (ServiceOrder order : waitingOrders) {
            try {
                log.info("定时任务自动推进待派单订单: orderNo={}", order.getOrderNo());
                dispatchService.dispatchOrder(order.getId(), null, "AUTO");
            } catch (Exception e) {
                log.error("自动派单执行异常: orderId=" + order.getId(), e);
            }
        }
    }
}
