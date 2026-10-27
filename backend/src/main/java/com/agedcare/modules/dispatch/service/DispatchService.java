package com.agedcare.modules.dispatch.service;

import com.agedcare.common.api.ResultCode;
import com.agedcare.common.enums.BookingStatus;
import com.agedcare.common.enums.DispatchStatus;
import com.agedcare.common.enums.OrderStatus;
import com.agedcare.common.enums.UserRole;
import com.agedcare.common.exception.BusinessException;
import com.agedcare.common.security.LoginUser;
import com.agedcare.common.security.UserContext;
import com.agedcare.modules.dispatch.dto.CandidateEvaluationResult;
import com.agedcare.modules.dispatch.entity.DispatchAttempt;
import com.agedcare.modules.dispatch.entity.DispatchScore;
import com.agedcare.modules.dispatch.entity.StaffBooking;
import com.agedcare.modules.dispatch.mapper.DispatchAttemptMapper;
import com.agedcare.modules.dispatch.mapper.DispatchScoreMapper;
import com.agedcare.modules.dispatch.mapper.StaffBookingMapper;
import com.agedcare.modules.order.entity.OrderStatusLog;
import com.agedcare.modules.order.entity.ServiceOrder;
import com.agedcare.modules.order.mapper.OrderStatusLogMapper;
import com.agedcare.modules.order.mapper.ServiceOrderMapper;
import com.agedcare.modules.staff.entity.StaffProfile;
import com.agedcare.modules.staff.mapper.StaffProfileMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DispatchService {

    private final ServiceOrderMapper serviceOrderMapper;
    private final StaffProfileMapper staffProfileMapper;
    private final DispatchAttemptMapper dispatchAttemptMapper;
    private final DispatchScoreMapper dispatchScoreMapper;
    private final StaffBookingMapper staffBookingMapper;
    private final OrderStatusLogMapper orderStatusLogMapper;
    private final DispatchScoreEngine dispatchScoreEngine;

    @Value("${dispatch.timeout-seconds:120}")
    private int timeoutSeconds;

    @Value("${dispatch.max-attempts:3}")
    private int maxAttempts;

    /**
     * 统一派单入口(自动或人工指定)
     * 遵循锁协议: 先锁订单，再锁人员
     */
    @Transactional(rollbackFor = Exception.class)
    public DispatchAttempt dispatchOrder(Long orderId, Long specifiedStaffId, String dispatchType) {
        // 1. 悲观锁锁定订单
        ServiceOrder order = serviceOrderMapper.selectByIdForUpdate(orderId);
        if (order == null) {
            throw new BusinessException("目标订单不存在: " + orderId);
        }

        String oldStatus = order.getStatus();
        if (!OrderStatus.WAIT_DISPATCH.getCode().equals(oldStatus) && !OrderStatus.WAIT_MANUAL.getCode().equals(oldStatus)) {
            throw new BusinessException("订单当前状态不可派单: " + oldStatus);
        }

        // 检查预约服务时段是否已经结束，若已结束则转入 WAIT_MANUAL 人工处理，不再对外派发
        if (order.getPlannedEndAt() != null && LocalDateTime.now().isAfter(order.getPlannedEndAt())) {
            if (!OrderStatus.WAIT_MANUAL.getCode().equals(oldStatus)) {
                markOrderWaitManual(order, "预约服务时段已过，自动转入人工调度协助");
            }
            // 正常结束以提交状态与日志；抛异常会导致此事务整体回滚。
            return null;
        }

        int currentRound = order.getDispatchRound() != null ? order.getDispatchRound() : 1;

        // 如果订单处于待人工状态，管理员重新触发调度时递增轮次，避免被上一轮历史超限次数阻断
        if (OrderStatus.WAIT_MANUAL.getCode().equals(oldStatus)) {
            currentRound = currentRound + 1;
            order.setDispatchRound(currentRound);
            order.setStatus(OrderStatus.WAIT_DISPATCH.getCode());
            serviceOrderMapper.updateById(order);
            recordStatusLog(order.getId(), OrderStatus.WAIT_MANUAL.getCode(), OrderStatus.WAIT_DISPATCH.getCode(),
                    String.format("管理员重新触发调度，开启第%d轮派单", currentRound));
        }

        // 2. 运行打分引擎评估全部候选
        List<CandidateEvaluationResult> evaluations = dispatchScoreEngine.evaluateAllCandidates(order);

        // 3. 将本轮评分事实持久化到 dispatch_score
        for (CandidateEvaluationResult ev : evaluations) {
            DispatchScore scoreRecord = DispatchScore.builder()
                    .orderId(order.getId())
                    .dispatchRound(currentRound)
                    .staffId(ev.getStaffId())
                    .isQualified(ev.isQualified() ? 1 : 0)
                    .excludeReason(ev.getExcludeReason())
                    .travelMinutes(ev.getTravelMinutes())
                    .metricD(ev.getMetricD())
                    .metricQ(ev.getMetricQ())
                    .metricL(ev.getMetricL())
                    .metricF(ev.getMetricF())
                    .totalScore(ev.getTotalScore())
                    .algorithmVersion("v1.0")
                    .detailsJson(ev.getDetailsJson())
                    .createdAt(LocalDateTime.now())
                    .build();
            dispatchScoreMapper.insert(scoreRecord);
        }

        // 4. 查询本轮已经尝试过的人员列表
        List<DispatchAttempt> previousAttempts = dispatchAttemptMapper.selectList(new LambdaQueryWrapper<DispatchAttempt>()
                .eq(DispatchAttempt::getOrderId, order.getId())
                .eq(DispatchAttempt::getDispatchRound, currentRound));

        Set<Long> attemptedStaffIds = previousAttempts.stream()
                .map(DispatchAttempt::getStaffId)
                .collect(Collectors.toSet());

        CandidateEvaluationResult targetCandidate = null;

        if (specifiedStaffId != null) {
            // 人工指定派单
            Optional<CandidateEvaluationResult> matched = evaluations.stream()
                    .filter(e -> e.getStaffId().equals(specifiedStaffId))
                    .findFirst();
            if (matched.isEmpty() || !matched.get().isQualified()) {
                String reason = matched.map(CandidateEvaluationResult::getExcludeReason).orElse("该服务人员未通过资格审核");
                throw new BusinessException("无法派给该人员，硬约束未通过: " + reason);
            }
            targetCandidate = matched.get();
        } else {
            // 自动评分派单: 检查是否已达尝试上限
            if (previousAttempts.size() >= maxAttempts) {
                log.info("订单 {} 第 {} 轮已达最大尝试次数 {}，转待人工处理", order.getOrderNo(), currentRound, maxAttempts);
                markOrderWaitManual(order, "自动尝试派单已达上限(3次)未成功接单");
                return null;
            }

            // 在未尝试人员中挑选综合得分最高且合格者
            for (CandidateEvaluationResult ev : evaluations) {
                if (ev.isQualified() && !attemptedStaffIds.contains(ev.getStaffId())) {
                    targetCandidate = ev;
                    break;
                }
            }

            if (targetCandidate == null) {
                log.info("订单 {} 第 {} 轮无更多合格候选人员，转待人工处理", order.getOrderNo(), currentRound);
                markOrderWaitManual(order, "暂无符合技能、排班或时间要求的空闲服务人员");
                return null;
            }
        }

        // 5. 悲观锁锁定选中的服务人员记录，复核并发状态
        StaffProfile staff = staffProfileMapper.selectByIdForUpdate(targetCandidate.getStaffId());
        CandidateEvaluationResult recheck = dispatchScoreEngine.evaluateSingleCandidate(staff, order);
        if (!recheck.isQualified()) {
            throw new BusinessException("人员状态发生并发变动，无法派单: " + recheck.getExcludeReason());
        }

        int nextAttemptNo = previousAttempts.size() + 1;
        LocalDateTime deadline = LocalDateTime.now().plusSeconds(timeoutSeconds);

        // 6. 创建派单尝试记录 (PENDING)
        DispatchAttempt attempt = DispatchAttempt.builder()
                .orderId(order.getId())
                .dispatchRound(currentRound)
                .attemptNo(nextAttemptNo)
                .staffId(staff.getId())
                .dispatchType(specifiedStaffId != null ? "MANUAL" : "AUTO")
                .deadline(deadline)
                .status(DispatchStatus.PENDING.getCode())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        dispatchAttemptMapper.insert(attempt);

        // 7. 创建排班时间预留 (HELD)
        int travel = targetCandidate.getTravelMinutes();
        StaffBooking booking = StaffBooking.builder()
                .staffId(staff.getId())
                .orderId(order.getId())
                .dispatchId(attempt.getId())
                .startTime(order.getPlannedStartAt().minusMinutes(travel))
                .endTime(order.getPlannedEndAt())
                .status(BookingStatus.HELD.getCode())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        staffBookingMapper.insert(booking);

        // 8. 推进订单状态为 WAIT_ACCEPT
        order.setStatus(OrderStatus.WAIT_ACCEPT.getCode());
        order.setCurrentDispatchId(attempt.getId());
        order.setUpdatedAt(LocalDateTime.now());
        serviceOrderMapper.updateById(order);

        // 9. 记录状态转换日志
        recordStatusLog(order.getId(), oldStatus, OrderStatus.WAIT_ACCEPT.getCode(),
                String.format("派单给工作人员#%d，等待接单(截止时间: %s)", staff.getId(), deadline));

        log.info("订单 {} 成功发起派单: attemptId={}, staffId={}, type={}", order.getOrderNo(), attempt.getId(), staff.getId(), attempt.getDispatchType());
        return attempt;
    }

    /**
     * 服务人员接单
     */
    @Transactional(rollbackFor = Exception.class)
    public void acceptDispatch(Long dispatchId) {
        LoginUser currentUser = UserContext.getUser();
        if (currentUser == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }

        DispatchAttempt attempt = dispatchAttemptMapper.selectById(dispatchId);
        if (attempt == null) {
            throw new BusinessException("派单记录不存在");
        }

        // 权限校验：必须是管理员或者被派单的服务人员本人
        if (!UserRole.ADMIN.getCode().equals(currentUser.getRole())) {
            StaffProfile staff = staffProfileMapper.selectOne(new LambdaQueryWrapper<StaffProfile>()
                    .eq(StaffProfile::getUserId, currentUser.getId()));
            if (staff == null || !staff.getId().equals(attempt.getStaffId())) {
                throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "您无权操作此派单记录");
            }
        }

        // 悲观锁锁定订单
        ServiceOrder order = serviceOrderMapper.selectByIdForUpdate(attempt.getOrderId());
        if (order == null) {
            throw new BusinessException("目标订单不存在");
        }

        // 订单状态与当前派单ID双重校验
        if (!OrderStatus.WAIT_ACCEPT.getCode().equals(order.getStatus())) {
            throw new BusinessException("订单当前状态非待接单，无法接单");
        }
        if (order.getPlannedEndAt() != null && LocalDateTime.now().isAfter(order.getPlannedEndAt())) {
            throw new BusinessException("该服务订单已过预约服务时段，无法接单");
        }
        if (order.getCurrentDispatchId() == null || !order.getCurrentDispatchId().equals(attempt.getId())) {
            throw new BusinessException("该派单已失效或已被改派，无法接单");
        }

        // 派单状态和超时时间校验
        attempt = dispatchAttemptMapper.selectById(dispatchId);
        if (!DispatchStatus.PENDING.getCode().equals(attempt.getStatus())) {
            throw new BusinessException("该派单非待接单状态，无法接单");
        }
        if (attempt.getDeadline() != null && LocalDateTime.now().isAfter(attempt.getDeadline())) {
            throw new BusinessException("接单已超时失效");
        }

        // 条件更新派单状态为 ACCEPTED（防止并发重复确认）
        int updatedRows = dispatchAttemptMapper.update(null, new LambdaUpdateWrapper<DispatchAttempt>()
                .eq(DispatchAttempt::getId, attempt.getId())
                .eq(DispatchAttempt::getStatus, DispatchStatus.PENDING.getCode())
                .set(DispatchAttempt::getStatus, DispatchStatus.ACCEPTED.getCode())
                .set(DispatchAttempt::getAcceptedAt, LocalDateTime.now())
                .set(DispatchAttempt::getUpdatedAt, LocalDateTime.now()));
        if (updatedRows == 0) {
            throw new BusinessException("接单状态已被并发修改，接单失败");
        }

        // 更新预留状态为 CONFIRMED
        StaffBooking booking = staffBookingMapper.selectOne(new LambdaQueryWrapper<StaffBooking>()
                .eq(StaffBooking::getDispatchId, attempt.getId()));
        if (booking != null) {
            booking.setStatus(BookingStatus.CONFIRMED.getCode());
            booking.setUpdatedAt(LocalDateTime.now());
            staffBookingMapper.updateById(booking);
        }

        // 更新订单状态为 WAIT_VISIT
        String oldStatus = order.getStatus();
        order.setStatus(OrderStatus.WAIT_VISIT.getCode());
        order.setUpdatedAt(LocalDateTime.now());
        serviceOrderMapper.updateById(order);

        recordStatusLog(order.getId(), oldStatus, OrderStatus.WAIT_VISIT.getCode(),
                String.format("服务人员#%d已确认接单，待按时上门", attempt.getStaffId()));
    }

    /**
     * 服务人员拒单并自动重派
     */
    @Transactional(rollbackFor = Exception.class)
    public void rejectDispatch(Long dispatchId, String reason) {
        LoginUser currentUser = UserContext.getUser();
        if (currentUser == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }

        DispatchAttempt attempt = dispatchAttemptMapper.selectById(dispatchId);
        if (attempt == null) {
            throw new BusinessException("派单记录不存在");
        }

        // 权限校验：必须是管理员或者被派单的服务人员本人
        if (!UserRole.ADMIN.getCode().equals(currentUser.getRole())) {
            StaffProfile staff = staffProfileMapper.selectOne(new LambdaQueryWrapper<StaffProfile>()
                    .eq(StaffProfile::getUserId, currentUser.getId()));
            if (staff == null || !staff.getId().equals(attempt.getStaffId())) {
                throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "您无权操作此派单记录");
            }
        }

        // 悲观锁锁定订单
        ServiceOrder order = serviceOrderMapper.selectByIdForUpdate(attempt.getOrderId());
        if (order == null) {
            throw new BusinessException("订单不存在");
        }

        if (!OrderStatus.WAIT_ACCEPT.getCode().equals(order.getStatus())) {
            throw new BusinessException("订单当前状态已不可拒单");
        }
        if (order.getCurrentDispatchId() == null || !order.getCurrentDispatchId().equals(attempt.getId())) {
            throw new BusinessException("该派单已非当前有效派单");
        }

        // 条件更新派单为 REJECTED
        int updatedRows = dispatchAttemptMapper.update(null, new LambdaUpdateWrapper<DispatchAttempt>()
                .eq(DispatchAttempt::getId, attempt.getId())
                .eq(DispatchAttempt::getStatus, DispatchStatus.PENDING.getCode())
                .set(DispatchAttempt::getStatus, DispatchStatus.REJECTED.getCode())
                .set(DispatchAttempt::getRejectedAt, LocalDateTime.now())
                .set(DispatchAttempt::getRejectReason, reason != null ? reason : "人员自主拒单")
                .set(DispatchAttempt::getUpdatedAt, LocalDateTime.now()));
        if (updatedRows == 0) {
            throw new BusinessException("派单状态已被修改，拒单失败");
        }

        // 释放预留
        StaffBooking booking = staffBookingMapper.selectOne(new LambdaQueryWrapper<StaffBooking>()
                .eq(StaffBooking::getDispatchId, attempt.getId()));
        if (booking != null) {
            booking.setStatus(BookingStatus.RELEASED.getCode());
            booking.setUpdatedAt(LocalDateTime.now());
            staffBookingMapper.updateById(booking);
        }

        // 订单回退待派单状态并触发重派
        order.setStatus(OrderStatus.WAIT_DISPATCH.getCode());
        order.setCurrentDispatchId(null);
        order.setUpdatedAt(LocalDateTime.now());
        serviceOrderMapper.updateById(order);

        recordStatusLog(order.getId(), OrderStatus.WAIT_ACCEPT.getCode(), OrderStatus.WAIT_DISPATCH.getCode(),
                String.format("服务人员#%d拒单: %s，系统重新检索派单", attempt.getStaffId(), reason != null ? reason : "人员自主拒单"));

        // 自动选择下一个候选
        dispatchOrder(order.getId(), null, "AUTO");
    }

    /**
     * 处理超时失效
     */
    @Transactional(rollbackFor = Exception.class)
    public void handleTimeoutAttempt(DispatchAttempt attempt) {
        if (attempt == null || attempt.getOrderId() == null) {
            return;
        }

        ServiceOrder order = serviceOrderMapper.selectByIdForUpdate(attempt.getOrderId());
        if (order == null || !OrderStatus.WAIT_ACCEPT.getCode().equals(order.getStatus())) {
            return;
        }

        if (order.getCurrentDispatchId() == null || !order.getCurrentDispatchId().equals(attempt.getId())) {
            return;
        }

        DispatchAttempt latestAttempt = dispatchAttemptMapper.selectById(attempt.getId());
        if (latestAttempt == null || !DispatchStatus.PENDING.getCode().equals(latestAttempt.getStatus())) {
            return;
        }
        if (latestAttempt.getDeadline() != null && LocalDateTime.now().isBefore(latestAttempt.getDeadline())) {
            return;
        }

        int updatedRows = dispatchAttemptMapper.update(null, new LambdaUpdateWrapper<DispatchAttempt>()
                .eq(DispatchAttempt::getId, latestAttempt.getId())
                .eq(DispatchAttempt::getStatus, DispatchStatus.PENDING.getCode())
                .set(DispatchAttempt::getStatus, DispatchStatus.EXPIRED.getCode())
                .set(DispatchAttempt::getExpiredAt, LocalDateTime.now())
                .set(DispatchAttempt::getUpdatedAt, LocalDateTime.now()));
        if (updatedRows == 0) {
            return;
        }

        StaffBooking booking = staffBookingMapper.selectOne(new LambdaQueryWrapper<StaffBooking>()
                .eq(StaffBooking::getDispatchId, latestAttempt.getId()));
        if (booking != null) {
            booking.setStatus(BookingStatus.RELEASED.getCode());
            booking.setUpdatedAt(LocalDateTime.now());
            staffBookingMapper.updateById(booking);
        }

        order.setStatus(OrderStatus.WAIT_DISPATCH.getCode());
        order.setCurrentDispatchId(null);
        order.setUpdatedAt(LocalDateTime.now());
        serviceOrderMapper.updateById(order);

        recordStatusLog(order.getId(), OrderStatus.WAIT_ACCEPT.getCode(), OrderStatus.WAIT_DISPATCH.getCode(),
                String.format("派单给服务人员#%d接单超时，系统自动释放并重派", latestAttempt.getStaffId()));

        dispatchOrder(order.getId(), null, "AUTO");
    }

    /**
     * 管理员改派 / 人工调度
     */
    @Transactional(rollbackFor = Exception.class)
    public void manualReassign(Long orderId, Long newStaffId, String reason) {
        ServiceOrder order = serviceOrderMapper.selectByIdForUpdate(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }

        if (OrderStatus.IN_SERVICE.getCode().equals(order.getStatus())
                || OrderStatus.WAIT_CONFIRM.getCode().equals(order.getStatus())
                || order.getStatusEnum().isTerminal()) {
            throw new BusinessException("订单已开始服务、已提交履约或已结束，无法改派");
        }

        // 撤销原有的有效派单与预留
        if (order.getCurrentDispatchId() != null) {
            DispatchAttempt prev = dispatchAttemptMapper.selectById(order.getCurrentDispatchId());
            if (prev != null && (DispatchStatus.PENDING.getCode().equals(prev.getStatus()) || DispatchStatus.ACCEPTED.getCode().equals(prev.getStatus()))) {
                prev.setStatus(DispatchStatus.REVOKED.getCode());
                prev.setRevokedAt(LocalDateTime.now());
                prev.setRevokeReason(reason != null ? reason : "管理员人工改派撤销");
                prev.setUpdatedAt(LocalDateTime.now());
                dispatchAttemptMapper.updateById(prev);

                StaffBooking b = staffBookingMapper.selectOne(new LambdaQueryWrapper<StaffBooking>()
                        .eq(StaffBooking::getDispatchId, prev.getId()));
                if (b != null) {
                    b.setStatus(BookingStatus.RELEASED.getCode());
                    b.setUpdatedAt(LocalDateTime.now());
                    staffBookingMapper.updateById(b);
                }
            }
        }

        // 增加调度轮次
        String prevStatus = order.getStatus();
        order.setDispatchRound((order.getDispatchRound() != null ? order.getDispatchRound() : 1) + 1);
        order.setStatus(OrderStatus.WAIT_DISPATCH.getCode());
        order.setCurrentDispatchId(null);
        order.setUpdatedAt(LocalDateTime.now());
        serviceOrderMapper.updateById(order);

        recordStatusLog(order.getId(), prevStatus, OrderStatus.WAIT_DISPATCH.getCode(),
                "管理员发起改派调度: " + reason);

        // 如果指定了新人员则直接派给新人员，否则重新自动调度
        dispatchOrder(order.getId(), newStaffId, newStaffId != null ? "MANUAL" : "AUTO");
    }

    private void markOrderWaitManual(ServiceOrder order, String reason) {
        String oldStatus = order.getStatus();
        order.setStatus(OrderStatus.WAIT_MANUAL.getCode());
        order.setUpdatedAt(LocalDateTime.now());
        serviceOrderMapper.updateById(order);

        recordStatusLog(order.getId(), oldStatus, OrderStatus.WAIT_MANUAL.getCode(), reason);
    }

    private void recordStatusLog(Long orderId, String fromStatus, String toStatus, String reason) {
        LoginUser currentUser = UserContext.getUser();
        Long operatorId = currentUser != null ? currentUser.getId() : 0L;
        String operatorRole = currentUser != null ? currentUser.getRole() : "SYSTEM";

        OrderStatusLog logEntity = OrderStatusLog.builder()
                .orderId(orderId)
                .fromStatus(fromStatus)
                .toStatus(toStatus)
                .operatorId(operatorId)
                .operatorRole(operatorRole)
                .reason(reason != null ? reason : "")
                .createdAt(LocalDateTime.now())
                .build();
        orderStatusLogMapper.insert(logEntity);
    }
}
