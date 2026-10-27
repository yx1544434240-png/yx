package com.agedcare.modules.fulfillment.controller;

import com.agedcare.common.api.Result;
import com.agedcare.common.enums.UserRole;
import com.agedcare.common.security.RequireRole;
import com.agedcare.modules.fulfillment.entity.ServiceComplaint;
import com.agedcare.modules.fulfillment.entity.ServiceRecord;
import com.agedcare.modules.fulfillment.dto.ServiceRecordDTO;
import com.agedcare.modules.fulfillment.dto.ReviewSubmitDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import com.agedcare.modules.fulfillment.entity.ServiceReview;
import com.agedcare.modules.fulfillment.service.FulfillmentService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "履约跟踪、确认与评价")
@org.springframework.validation.annotation.Validated
@RestController
@RequestMapping("/fulfillment")
@RequiredArgsConstructor
public class FulfillmentController {

    private final FulfillmentService fulfillmentService;

    @Data
    public static class ConfirmDTO {
        @NotNull @Positive
        private Long orderId;
        @Size(max=220)
        private String note;
    }

    @Data
    public static class HandleComplaintDTO {
        @NotNull @Positive
        private Long id;
        @NotBlank @Size(max=4000)
        private String result;
    }

    @Data
    public static class ComplaintIdDTO {
        @NotNull @Positive
        private Long id;
    }

    @Operation(summary = "服务人员开始上门服务(签到)")
    @PostMapping("/start/{orderId}")
    @RequireRole(UserRole.STAFF)
    public Result<Void> startService(@PathVariable Long orderId) {
        fulfillmentService.startService(orderId);
        return Result.success();
    }

    @Operation(summary = "服务人员提交上门履约记录")
    @PostMapping("/submit-record")
    @RequireRole(UserRole.STAFF)
    public Result<Void> submitRecord(@Valid @RequestBody ServiceRecordDTO dto) {
        ServiceRecord record = ServiceRecord.builder().orderId(dto.getOrderId()).serviceSummary(dto.getServiceSummary()).attachmentsJson(dto.getAttachmentsJson()).build();
        fulfillmentService.submitServiceRecord(record);
        return Result.success();
    }

    @Operation(summary = "确认服务完成(老人、家属或管理员核实代办)")
    @PostMapping("/confirm")
    @RequireRole({UserRole.RESIDENT, UserRole.ADMIN})
    public Result<Void> confirm(@Valid @RequestBody ConfirmDTO dto) {
        fulfillmentService.confirmOrder(dto.getOrderId(), dto.getNote());
        return Result.success();
    }

    @Operation(summary = "提交服务评价(仅限居民)")
    @PostMapping("/review")
    @RequireRole(UserRole.RESIDENT)
    public Result<Void> submitReview(@Valid @RequestBody ReviewSubmitDTO dto) {
        ServiceReview review = ServiceReview.builder().orderId(dto.getOrderId()).rating(dto.getRating())
                .content(dto.getContent()).tags(dto.getTags()).build();
        fulfillmentService.submitReview(review, dto.isFollowUp(), dto.getIssueType());
        return Result.success();
    }

    @Operation(summary = "提交服务投诉或建议(仅限居民)")
    @PostMapping("/complaint")
    @RequireRole(UserRole.RESIDENT)
    public Result<Void> submitComplaint(@Valid @RequestBody ServiceComplaint complaint) {
        fulfillmentService.submitComplaint(complaint);
        return Result.success();
    }

    @Operation(summary = "查询我的投诉与建议列表(居民)")
    @GetMapping("/complaints/my")
    @RequireRole(UserRole.RESIDENT)
    public Result<Page<ServiceComplaint>> pageMyComplaints(
            @RequestParam(defaultValue = "1") @jakarta.validation.constraints.Min(1) int pageNum,
            @RequestParam(defaultValue = "10") @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(100) int pageSize
    ) {
        return Result.success(fulfillmentService.pageMyComplaints(pageNum, pageSize));
    }

    @Operation(summary = "分页查询投诉列表(管理员)")
    @GetMapping("/complaints/page")
    @RequireRole(UserRole.ADMIN)
    public Result<Page<ServiceComplaint>> pageComplaints(
            @RequestParam(defaultValue = "1") @jakarta.validation.constraints.Min(1) int pageNum,
            @RequestParam(defaultValue = "10") @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(100) int pageSize,
            @RequestParam(required = false) String status
    ) {
        return Result.success(fulfillmentService.pageComplaints(pageNum, pageSize, status));
    }

    @Operation(summary = "管理员处理投诉")
    @PostMapping("/complaint/start")
    @RequireRole(UserRole.ADMIN)
    public Result<Void> startComplaint(@Valid @RequestBody ComplaintIdDTO dto) {
        fulfillmentService.startComplaint(dto.getId());
        return Result.success();
    }

    @Operation(summary = "管理员办结投诉")
    @PostMapping("/complaint/handle")
    @RequireRole(UserRole.ADMIN)
    public Result<Void> handleComplaint(@Valid @RequestBody HandleComplaintDTO dto) {
        fulfillmentService.handleComplaint(dto.getId(), dto.getResult());
        return Result.success();
    }
}
