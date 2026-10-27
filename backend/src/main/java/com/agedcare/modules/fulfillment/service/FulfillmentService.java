package com.agedcare.modules.fulfillment.service;

import com.agedcare.common.api.ResultCode;
import com.agedcare.common.enums.BookingStatus;
import com.agedcare.common.enums.OrderStatus;
import com.agedcare.common.enums.UserRole;
import com.agedcare.common.exception.BusinessException;
import com.agedcare.common.storage.ImageUrlPolicy;
import com.agedcare.common.storage.PrivateMediaService;
import com.agedcare.common.security.OrderAccessPolicy;
import com.agedcare.common.security.LoginUser;
import com.agedcare.common.security.UserContext;
import com.agedcare.modules.dispatch.entity.DispatchAttempt;
import com.agedcare.modules.dispatch.entity.StaffBooking;
import com.agedcare.modules.dispatch.mapper.DispatchAttemptMapper;
import com.agedcare.modules.dispatch.mapper.StaffBookingMapper;
import com.agedcare.modules.elder.entity.ElderProfile;
import com.agedcare.modules.elder.mapper.ElderProfileMapper;
import com.agedcare.modules.elder.service.ElderProfileService;
import com.agedcare.modules.fulfillment.entity.ServiceComplaint;
import com.agedcare.modules.fulfillment.entity.ServiceRecord;
import com.agedcare.modules.fulfillment.entity.ServiceReview;
import com.agedcare.modules.fulfillment.mapper.ServiceComplaintMapper;
import com.agedcare.modules.fulfillment.mapper.ServiceRecordMapper;
import com.agedcare.modules.fulfillment.mapper.ServiceReviewMapper;
import com.agedcare.modules.order.entity.OrderStatusLog;
import com.agedcare.modules.order.entity.ServiceOrder;
import com.agedcare.modules.order.mapper.OrderStatusLogMapper;
import com.agedcare.modules.order.mapper.ServiceOrderMapper;
import com.agedcare.modules.staff.entity.StaffProfile;
import com.agedcare.modules.staff.mapper.StaffProfileMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FulfillmentService {

    private final ServiceOrderMapper serviceOrderMapper;
    private final OrderStatusLogMapper orderStatusLogMapper;
    private final DispatchAttemptMapper dispatchAttemptMapper;
    private final StaffBookingMapper staffBookingMapper;
    private final StaffProfileMapper staffProfileMapper;
    private final ElderProfileMapper elderProfileMapper;
    private final ElderProfileService elderProfileService;
    private final ServiceRecordMapper serviceRecordMapper;
    private final ServiceReviewMapper serviceReviewMapper;
    private final ServiceComplaintMapper serviceComplaintMapper;
    private final ObjectMapper objectMapper;
    private final ImageUrlPolicy imageUrlPolicy;
    private final PrivateMediaService privateMedia;

    /**
     * 服务人员开始上门服务
     */
    @Transactional(rollbackFor = Exception.class)
    public void startService(Long orderId) {
        LoginUser currentUser = UserContext.getUser();
        if (currentUser == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }

        ServiceOrder order = serviceOrderMapper.selectByIdForUpdate(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }

        if (!OrderStatus.WAIT_VISIT.getCode().equals(order.getStatus())) {
            throw new BusinessException("订单当前状态不可开始服务: " + order.getStatus());
        }

        if (order.getCurrentDispatchId() == null) {
            throw new BusinessException("订单无有效派单记录，无法开始服务");
        }

        DispatchAttempt attempt = dispatchAttemptMapper.selectById(order.getCurrentDispatchId());
        if (attempt == null) {
            throw new BusinessException("未找到订单对应派单信息");
        }

        // 权限校验：仅允许当前被指派的服务人员开始服务
        StaffProfile staff = staffProfileMapper.selectOne(new LambdaQueryWrapper<StaffProfile>()
                .eq(StaffProfile::getUserId, currentUser.getId()));
        if (staff == null || !staff.getId().equals(attempt.getStaffId())) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "只有当前接单的服务人员才能开始服务");
        }

        order.setStatus(OrderStatus.IN_SERVICE.getCode());
        order.setUpdatedAt(LocalDateTime.now());
        serviceOrderMapper.updateById(order);

        // 记录实际开始服务时间
        ServiceRecord existingRecord = serviceRecordMapper.selectOne(new LambdaQueryWrapper<ServiceRecord>()
                .eq(ServiceRecord::getOrderId, order.getId()));
        if (existingRecord == null) {
            ServiceRecord newRecord = ServiceRecord.builder()
                    .orderId(order.getId())
                    .staffId(attempt.getStaffId())
                    .actualStartAt(LocalDateTime.now().withNano(0))
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            serviceRecordMapper.insert(newRecord);
        } else {
            existingRecord.setStaffId(attempt.getStaffId());
            existingRecord.setActualStartAt(LocalDateTime.now().withNano(0));
            existingRecord.setUpdatedAt(LocalDateTime.now());
            serviceRecordMapper.updateById(existingRecord);
        }

        recordStatusLog(order.getId(), OrderStatus.WAIT_VISIT.getCode(), OrderStatus.IN_SERVICE.getCode(),
                String.format("服务人员#%d已到达现场，开始提供上门服务", attempt.getStaffId()));
    }

    /**
     * 服务人员完成服务并提交服务履约记录
     */
    @Transactional(rollbackFor = Exception.class)
    public void submitServiceRecord(ServiceRecord record) {
        LoginUser currentUser = UserContext.getUser();
        if (currentUser == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }

        ServiceOrder order = serviceOrderMapper.selectByIdForUpdate(record.getOrderId());
        if (order == null) {
            throw new BusinessException("订单不存在");
        }

        if (!OrderStatus.IN_SERVICE.getCode().equals(order.getStatus())) {
            throw new BusinessException("只有服务中状态的订单才能提交服务记录");
        }

        if (order.getCurrentDispatchId() == null) {
            throw new BusinessException("订单无有效派单记录");
        }

        DispatchAttempt attempt = dispatchAttemptMapper.selectById(order.getCurrentDispatchId());
        if (attempt == null) {
            throw new BusinessException("未找到订单派单信息");
        }

        // 权限校验：仅允许当前被派单的服务人员提交履约记录
        StaffProfile staff = staffProfileMapper.selectOne(new LambdaQueryWrapper<StaffProfile>()
                .eq(StaffProfile::getUserId, currentUser.getId()));
        if (staff == null || !staff.getId().equals(attempt.getStaffId())) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "只有当前接单的服务人员才能提交履约记录");
        }

        // 补齐 staffId
        record.setStaffId(attempt.getStaffId());

        ServiceRecord existing = serviceRecordMapper.selectOne(new LambdaQueryWrapper<ServiceRecord>()
                .eq(ServiceRecord::getOrderId, order.getId()));

        if (existing == null || existing.getActualStartAt() == null) throw new BusinessException("请先签到开始服务");
        record.setActualStartAt(existing.getActualStartAt());
        record.setActualEndAt(LocalDateTime.now().withNano(0));

        if (record.getActualEndAt().isBefore(record.getActualStartAt())) {
            throw new BusinessException("实际结束时间不能早于开始时间");
        }

        if (!StringUtils.hasText(record.getServiceSummary())) {
            throw new BusinessException("请填写服务情况与工作说明");
        }

        // 校验现场照片凭证格式与域名合法性 (防任意恶意 URL 伪造注入)
        if (StringUtils.hasText(record.getAttachmentsJson())) {
            try {
                JsonNode arrayNode = objectMapper.readTree(record.getAttachmentsJson());
                if (!arrayNode.isArray()) {
                    throw new BusinessException("现场照片凭证必须是图片列表");
                }
                if (arrayNode.size() > 4) {
                    throw new BusinessException("现场照片凭证最多上传4张");
                }
                for (JsonNode item : arrayNode) {
                    String imgUrl = item.asText();
                    if (!item.isTextual()) throw new BusinessException("照片地址必须为字符串");
                    privateMedia.requireOwned(imgUrl, order.getId(), currentUser.getId());
                }
            } catch (IOException e) {
                throw new BusinessException("现场照片凭证格式无效");
            }
        }

        // 保存或更新履约记录
        if (existing == null) {
            record.setCreatedAt(LocalDateTime.now());
            record.setUpdatedAt(LocalDateTime.now());
            serviceRecordMapper.insert(record);
        } else {
            record.setId(existing.getId());
            record.setUpdatedAt(LocalDateTime.now());
            serviceRecordMapper.updateById(record);
        }

        // 释放或完结排班预留
        StaffBooking booking = staffBookingMapper.selectOne(new LambdaQueryWrapper<StaffBooking>()
                .eq(StaffBooking::getDispatchId, order.getCurrentDispatchId()));
        if (booking != null) {
            booking.setStatus(BookingStatus.FINISHED.getCode());
            booking.setUpdatedAt(LocalDateTime.now());
            staffBookingMapper.updateById(booking);
        }

        // 订单状态流转至 WAIT_CONFIRM (待老人或家属确认)
        order.setStatus(OrderStatus.WAIT_CONFIRM.getCode());
        order.setUpdatedAt(LocalDateTime.now());
        serviceOrderMapper.updateById(order);

        recordStatusLog(order.getId(), OrderStatus.IN_SERVICE.getCode(), OrderStatus.WAIT_CONFIRM.getCode(),
                String.format("服务人员#%d已提交服务记录，等待老人或家属确认完成", attempt.getStaffId()));
    }

    /**
     * 老人、家属或管理员确认服务完成
     */
    @Transactional(rollbackFor = Exception.class)
    public void confirmOrder(Long orderId, String note) {
        LoginUser currentUser = UserContext.getUser();
        if (currentUser == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }

        ServiceOrder order = serviceOrderMapper.selectByIdForUpdate(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }

        if (!OrderStatus.WAIT_CONFIRM.getCode().equals(order.getStatus())) {
            throw new BusinessException("订单非待确认状态");
        }
        if ("ADMIN".equals(currentUser.getRole()) && (note == null || note.trim().length() < 10 || note.length() > 220)) {
            throw new BusinessException("管理员代确认必须填写10至220字的核验依据及代办原因");
        }

        OrderAccessPolicy.customer(order, elderProfileMapper, elderProfileService, true);

        order.setStatus(OrderStatus.COMPLETED.getCode());
        order.setConfirmedByUserId(currentUser.getId());
        order.setConfirmedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        serviceOrderMapper.updateById(order);

        // 累加服务人员累计完成订单数
        if (order.getCurrentDispatchId() != null) {
            DispatchAttempt attempt = dispatchAttemptMapper.selectById(order.getCurrentDispatchId());
            if (attempt != null) {
                StaffProfile staff = staffProfileMapper.selectById(attempt.getStaffId());
                if (staff != null) {
                    staff.setTotalOrders((staff.getTotalOrders() != null ? staff.getTotalOrders() : 0) + 1);
                    staff.setUpdatedAt(LocalDateTime.now());
                    staffProfileMapper.updateById(staff);
                }
            }
        }

        String confirmDesc = UserRole.ADMIN.getCode().equalsIgnoreCase(currentUser.getRole())
                ? "管理员核验代为确认完成: " + (note != null ? note : "情况属实")
                : "老人/家属确认服务圆满完成";

        recordStatusLog(order.getId(), OrderStatus.WAIT_CONFIRM.getCode(), OrderStatus.COMPLETED.getCode(), confirmDesc);
    }

    /**
     * 评价服务
     */
    @Transactional(rollbackFor = Exception.class)
    public void submitReview(ServiceReview review) {
        submitReview(review, false, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public void submitReview(ServiceReview review, boolean followUp, String issueType) {
        LoginUser currentUser = UserContext.getUser();
        if (currentUser == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }

        ServiceOrder order = serviceOrderMapper.selectById(review.getOrderId());
        if (order == null) {
            throw new BusinessException("订单不存在");
        }

        if (!OrderStatus.COMPLETED.getCode().equals(order.getStatus())) {
            throw new BusinessException("订单尚未完成，暂无法评价");
        }

        OrderAccessPolicy.customer(order, elderProfileMapper, elderProfileService, false);

        Long existingCount = serviceReviewMapper.selectCount(new LambdaQueryWrapper<ServiceReview>()
                .eq(ServiceReview::getOrderId, order.getId()));
        if (existingCount > 0) {
            throw new BusinessException("该订单已完成评价，每单仅限一条评价");
        }

        if (review.getRating() == null || review.getRating() < 1 || review.getRating() > 5) {
            throw new BusinessException("评分必须在1到5星之间");
        }

        String content = review.getContent() == null ? "" : review.getContent().trim();
        if (content.length() > 200) throw new BusinessException("评价内容最多200字");
        review.setContent(content);
        java.util.Set<String> allowedTags = java.util.Set.of("准时上门", "态度耐心", "服务细致", "沟通顺畅", "整体满意", "上门迟到", "服务未完成", "沟通不清", "服务态度需改进", "其他问题");
        java.util.List<String> tags = StringUtils.hasText(review.getTags())
                ? java.util.Arrays.stream(review.getTags().split(",")).map(String::trim).distinct().toList()
                : java.util.List.of();
        if (tags.size() > 5 || !allowedTags.containsAll(tags)) throw new BusinessException("请选择有效的评价标签，最多5项");
        review.setTags(String.join(",", tags));
        if (followUp && (review.getRating() > 2 || !java.util.Set.of("上门迟到", "服务未完成", "沟通不清", "服务态度需改进", "其他问题").contains(issueType == null ? "" : issueType))) {
            throw new BusinessException("请为低分评价选择需要社区跟进的问题类型");
        }

        review.setElderId(order.getElderId());
        review.setReviewerUserId(currentUser.getId());
        review.setCreatedAt(LocalDateTime.now());
        serviceReviewMapper.insert(review);

        if (followUp) {
            submitComplaint(ServiceComplaint.builder().orderId(order.getId())
                    .complaintTitle("[评价跟进] " + issueType)
                    .complaintContent("用户主动申请社区联系并跟进。\n评分：" + review.getRating() + "星\n问题：" + issueType
                            + "\n标签：" + String.join("、", tags) + "\n补充意见：" + (content.isEmpty() ? "未填写" : content)).build());
        }

        // 更新服务人员评分
        if (order.getCurrentDispatchId() != null) {
            DispatchAttempt attempt = dispatchAttemptMapper.selectById(order.getCurrentDispatchId());
            if (attempt != null) {
                updateStaffRating(attempt.getStaffId());
            }
        }
    }

    private void updateStaffRating(Long staffId) {
        List<ServiceReview> reviews = serviceReviewMapper.selectList(new LambdaQueryWrapper<ServiceReview>()
                .apply("order_id in (select order_id from dispatch_attempt where staff_id = {0} and status = 'ACCEPTED')", staffId));

        if (!reviews.isEmpty()) {
            double sum = reviews.stream().mapToInt(ServiceReview::getRating).sum();
            int n = reviews.size();
            // 平滑评分公式: (s + 5 * 4) / (n + 5)
            double smoothed = (sum + 5 * 4.0) / (n + 5);
            StaffProfile staff = staffProfileMapper.selectById(staffId);
            if (staff != null) {
                staff.setPriorRating(BigDecimal.valueOf(smoothed).setScale(2, RoundingMode.HALF_UP));
                staff.setUpdatedAt(LocalDateTime.now());
                staffProfileMapper.updateById(staff);
            }
        }
    }

    /**
     * 提交投诉或建议
     */
    @Transactional(rollbackFor = Exception.class)
    public void submitComplaint(ServiceComplaint complaint) {
        LoginUser currentUser = UserContext.getUser();
        if (currentUser == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        if (complaint.getOrderId() != null && complaint.getOrderId() > 0) {
            ServiceOrder order = serviceOrderMapper.selectById(complaint.getOrderId());
            if (order == null) {
                throw new BusinessException("目标订单不存在");
            }
            OrderAccessPolicy.customer(order, elderProfileMapper, elderProfileService, false);
        } else {
            complaint.setOrderId(0L);
        }

        complaint.setComplainantId(currentUser.getId());
        complaint.setStatus("PENDING");
        complaint.setCreatedAt(LocalDateTime.now());
        complaint.setUpdatedAt(LocalDateTime.now());
        serviceComplaintMapper.insert(complaint);
    }

    public Page<ServiceComplaint> pageMyComplaints(int pageNum, int pageSize) {
        LoginUser currentUser = UserContext.getUser();
        if (currentUser == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        Page<ServiceComplaint> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<ServiceComplaint> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ServiceComplaint::getComplainantId, currentUser.getId());
        wrapper.orderByDesc(ServiceComplaint::getCreatedAt);
        return serviceComplaintMapper.selectPage(page, wrapper);
    }

    public Page<ServiceComplaint> pageComplaints(int pageNum, int pageSize, String status) {
        Page<ServiceComplaint> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<ServiceComplaint> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(status)) {
            wrapper.eq(ServiceComplaint::getStatus, status);
        }
        wrapper.orderByDesc(ServiceComplaint::getCreatedAt);
        return serviceComplaintMapper.selectPage(page, wrapper);
    }

    @Transactional(rollbackFor = Exception.class)
    public void startComplaint(Long id) {
        int changed = serviceComplaintMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<ServiceComplaint>()
                .eq(ServiceComplaint::getId, id).eq(ServiceComplaint::getStatus, "PENDING")
                .set(ServiceComplaint::getStatus, "PROCESSING").set(ServiceComplaint::getHandleUserId, UserContext.getUser().getId())
                .set(ServiceComplaint::getUpdatedAt, LocalDateTime.now()));
        if (changed == 0) throw new BusinessException("该反馈已被受理或不存在，请刷新列表");
    }

    @Transactional(rollbackFor = Exception.class)
    public void handleComplaint(Long id, String result) {
        LoginUser currentUser = UserContext.getUser();
        ServiceComplaint complaint = serviceComplaintMapper.selectById(id);
        if (complaint == null) {
            throw new BusinessException("投诉记录不存在");
        }
        if (!java.util.Set.of("PENDING", "PROCESSING").contains(complaint.getStatus())) throw new BusinessException("该反馈已办结，请勿重复处理");
        if (!StringUtils.hasText(result) || result.trim().length() > 4000) throw new BusinessException("请填写4000字以内的处理答复");
        LocalDateTime handledAt = LocalDateTime.now();
        int changed = serviceComplaintMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<ServiceComplaint>()
                .eq(ServiceComplaint::getId, id).in(ServiceComplaint::getStatus, "PENDING", "PROCESSING")
                .set(ServiceComplaint::getStatus, "RESOLVED").set(ServiceComplaint::getHandleUserId, currentUser.getId())
                .set(ServiceComplaint::getHandleResult, result.trim()).set(ServiceComplaint::getHandledAt, handledAt)
                .set(ServiceComplaint::getUpdatedAt, handledAt));
        if (changed == 0) throw new BusinessException("该反馈已办结，请刷新列表");
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
                .reason(reason)
                .createdAt(LocalDateTime.now())
                .build();
        orderStatusLogMapper.insert(logEntity);
    }

}
