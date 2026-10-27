package com.agedcare.modules.elder.controller;

import com.agedcare.common.api.Result;
import com.agedcare.common.enums.UserRole;
import com.agedcare.common.security.RequireRole;
import com.agedcare.modules.elder.entity.ElderProfile;
import com.agedcare.modules.elder.dto.ElderSaveDTO;
import com.agedcare.modules.elder.dto.ElderAdminVO;
import jakarta.validation.Valid;
import org.springframework.beans.BeanUtils;
import com.agedcare.modules.elder.service.ElderProfileService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "老人档案管理")
@org.springframework.validation.annotation.Validated
@RestController
@RequestMapping("/elders")
@RequiredArgsConstructor
public class ElderProfileController {

    private final ElderProfileService elderProfileService;

    @Operation(summary = "分页查询老人档案列表(管理员)")
    @GetMapping("/page")
    @RequireRole(UserRole.ADMIN)
    public Result<Page<ElderAdminVO>> page(
            @RequestParam(defaultValue = "1") @jakarta.validation.constraints.Min(1) int pageNum,
            @RequestParam(defaultValue = "10") @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(100) int pageSize,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String community
    ) {
        Page<ElderProfile> source = elderProfileService.pageElders(pageNum, pageSize, name, community);
        Page<ElderAdminVO> result = new Page<>(source.getCurrent(), source.getSize(), source.getTotal());
        result.setRecords(source.getRecords().stream().map(ElderAdminVO::from).toList());
        return Result.success(result);
    }

    @Operation(summary = "获取当前用户可访问的老人列表(本人及已授权家属)")
    @GetMapping("/my-list")
    @RequireRole({UserRole.RESIDENT, UserRole.ADMIN})
    public Result<List<ElderProfile>> getAccessibleElders() {
        return Result.success(elderProfileService.getAccessibleElders());
    }

    @Operation(summary = "根据ID获取老人档案详情")
    @GetMapping("/{id}")
    public Result<ElderProfile> getById(@PathVariable Long id) {
        return Result.success(elderProfileService.getById(id));
    }

    @Operation(summary = "新增或编辑老人档案")
    @PostMapping("/save")
    @RequireRole({UserRole.RESIDENT, UserRole.ADMIN})
    public Result<ElderProfile> saveOrUpdate(@Valid @RequestBody ElderSaveDTO dto) {
        ElderProfile profile = new ElderProfile();
        BeanUtils.copyProperties(dto, profile);
        return Result.success(elderProfileService.saveOrUpdate(profile));
    }
}
