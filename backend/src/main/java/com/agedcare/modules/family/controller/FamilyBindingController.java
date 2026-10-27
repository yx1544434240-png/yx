package com.agedcare.modules.family.controller;

import com.agedcare.common.api.Result;
import com.agedcare.common.enums.UserRole;
import com.agedcare.common.security.RequireRole;
import com.agedcare.modules.family.dto.ElderSearchVO;
import com.agedcare.modules.family.dto.FamilyBindingVO;
import com.agedcare.modules.family.entity.FamilyBinding;
import com.agedcare.modules.family.service.FamilyBindingService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "家属绑定与授权")
@org.springframework.validation.annotation.Validated
@RestController
@RequestMapping("/family-bindings")
@RequiredArgsConstructor
public class FamilyBindingController {

    private final FamilyBindingService familyBindingService;

    @Data
    public static class ApplyDTO {
        private Long elderId;
        @jakarta.validation.constraints.Size(max=20)
        private String elderPhone;
        @jakarta.validation.constraints.Size(max=64)
        private String elderName;
        @jakarta.validation.constraints.Size(max=32)
        private String relation;
        @jakarta.validation.constraints.Size(max=1000)
        private String proofMaterials;
    }

    @Data
    public static class AuditDTO {
        @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Positive
        private Long bindingId;
        private boolean approve;
        @jakarta.validation.constraints.Size(max=200)
        private String reason;
    }

    @Operation(summary = "按电话精确查询社区老人档案(供家属申请绑定前核实)")
    @GetMapping("/search-elder")
    @RequireRole(UserRole.RESIDENT)
    public Result<ElderSearchVO> searchElder(@RequestParam String phone) {
        return Result.success(familyBindingService.searchElderByPhone(phone));
    }

    @Operation(summary = "提交家属代约/监护绑定申请")
    @PostMapping("/apply")
    @RequireRole(UserRole.RESIDENT)
    public Result<FamilyBinding> apply(@jakarta.validation.Valid @RequestBody ApplyDTO dto) {
        return Result.success(familyBindingService.applyBinding(dto.getElderId(), dto.getElderPhone(), dto.getElderName(), dto.getRelation(), dto.getProofMaterials()));
    }

    @Operation(summary = "查询我的家属绑定列表")
    @GetMapping("/my-bindings")
    @RequireRole(UserRole.RESIDENT)
    public Result<List<FamilyBindingVO>> getMyBindings() {
        return Result.success(familyBindingService.getMyBindings());
    }

    @Operation(summary = "分页查询绑定申请列表(管理员)")
    @GetMapping("/page")
    @RequireRole(UserRole.ADMIN)
    public Result<Page<FamilyBindingVO>> page(
            @RequestParam(defaultValue = "1") @jakarta.validation.constraints.Min(1) int pageNum,
            @RequestParam(defaultValue = "10") @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(100) int pageSize,
            @RequestParam(required = false) String status
    ) {
        return Result.success(familyBindingService.pageBindings(pageNum, pageSize, status));
    }

    @Operation(summary = "管理员审核绑定申请")
    @PostMapping("/audit")
    @RequireRole(UserRole.ADMIN)
    public Result<Void> audit(@jakarta.validation.Valid @RequestBody AuditDTO dto) {
        familyBindingService.auditBinding(dto.getBindingId(), dto.isApprove(), dto.getReason());
        return Result.success();
    }

    @Operation(summary = "撤销家属绑定")
    @PostMapping("/{id}/revoke")
    @RequireRole({UserRole.RESIDENT, UserRole.ADMIN})
    public Result<Void> revoke(@PathVariable Long id) {
        familyBindingService.revokeBinding(id);
        return Result.success();
    }
}
