package com.agedcare.modules.dispatch.controller;

import com.agedcare.common.api.Result;
import com.agedcare.common.enums.UserRole;
import com.agedcare.common.security.RequireRole;
import com.agedcare.modules.dispatch.entity.DispatchAttempt;
import com.agedcare.modules.dispatch.entity.DispatchScore;
import com.agedcare.modules.dispatch.mapper.DispatchScoreMapper;
import com.agedcare.modules.dispatch.service.DispatchService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "派单与调度中心")
@RestController
@RequestMapping("/dispatch")
@RequiredArgsConstructor
public class DispatchController {

    private final DispatchService dispatchService;
    private final DispatchScoreMapper dispatchScoreMapper;

    @Data
    public static class ManualDispatchDTO {
        @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Positive
        private Long orderId;
        @jakarta.validation.constraints.Positive
        private Long staffId; // 为空表示触发自动派单，有值表示管理员人工指定派单
    }

    @Data
    public static class ReassignDTO {
        @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Positive
        private Long orderId;
        private Long newStaffId;
        @jakarta.validation.constraints.Size(max=200)
        private String reason;
    }

    @Data
    public static class RejectDTO {
        private Long dispatchId;
        @jakarta.validation.constraints.Size(max=200)
        private String reason;
    }

    @Operation(summary = "发起派单(管理员可指定人员，也可触发自动算法派单)")
    @PostMapping("/assign")
    @RequireRole(UserRole.ADMIN)
    public Result<DispatchAttempt> assign(@jakarta.validation.Valid @RequestBody ManualDispatchDTO dto) {
        String dispatchType = dto.getStaffId() != null ? "MANUAL" : "AUTO";
        return Result.success(dispatchService.dispatchOrder(dto.getOrderId(), dto.getStaffId(), dispatchType));
    }

    @Operation(summary = "服务人员接单确认")
    @PostMapping("/accept/{dispatchId}")
    @RequireRole(UserRole.STAFF)
    public Result<Void> accept(@PathVariable Long dispatchId) {
        dispatchService.acceptDispatch(dispatchId);
        return Result.success();
    }

    @Operation(summary = "服务人员拒单(释放并自动重派)")
    @PostMapping("/reject")
    @RequireRole(UserRole.STAFF)
    public Result<Void> reject(@jakarta.validation.Valid @RequestBody RejectDTO dto) {
        dispatchService.rejectDispatch(dto.getDispatchId(), dto.getReason());
        return Result.success();
    }

    @Operation(summary = "管理员人工改派调度")
    @PostMapping("/reassign")
    @RequireRole(UserRole.ADMIN)
    public Result<Void> reassign(@jakarta.validation.Valid @RequestBody ReassignDTO dto) {
        dispatchService.manualReassign(dto.getOrderId(), dto.getNewStaffId(), dto.getReason());
        return Result.success();
    }

    @Operation(summary = "查询订单历史各轮次算法候选人评分与解释明细(管理员)")
    @GetMapping("/scores/{orderId}")
    @RequireRole(UserRole.ADMIN)
    public Result<List<DispatchScore>> getScores(@PathVariable Long orderId) {
        return Result.success(dispatchScoreMapper.selectList(new LambdaQueryWrapper<DispatchScore>()
                .eq(DispatchScore::getOrderId, orderId)
                .orderByDesc(DispatchScore::getDispatchRound)
                .orderByDesc(DispatchScore::getTotalScore)));
    }
}
