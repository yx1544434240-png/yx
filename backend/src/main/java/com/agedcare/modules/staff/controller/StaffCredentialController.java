package com.agedcare.modules.staff.controller;

import com.agedcare.common.api.Result;
import com.agedcare.common.enums.UserRole;
import com.agedcare.common.security.RequireRole;
import com.agedcare.common.storage.CredentialStorage;
import com.agedcare.modules.staff.dto.CredentialSubmitDTO;
import com.agedcare.modules.staff.entity.StaffSkill;
import com.agedcare.modules.staff.service.StaffCredentialService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.Map;

@RestController
@RequestMapping("/staff/credentials")
@RequiredArgsConstructor
@Validated
public class StaffCredentialController {
    private final StaffCredentialService service;
    private final CredentialStorage storage;
    @Data public static class AuditDTO {
        @NotNull @Positive private Long id;
        @Min(1) private int revision;
        private boolean approve;
        @Size(max=200) private String reason;
    }
    @PostMapping("/upload") @RequireRole(UserRole.STAFF)
    public Result<Map<String,String>> upload(@RequestParam("file") MultipartFile file) { return Result.success(Map.of("url", storage.upload(file))); }
    @PostMapping("/submit") @RequireRole(UserRole.STAFF)
    public Result<Void> submit(@Valid @RequestBody CredentialSubmitDTO dto) { service.submit(dto); return Result.success(); }
    @PostMapping("/audit") @RequireRole(UserRole.ADMIN)
    public Result<Void> audit(@Valid @RequestBody AuditDTO dto) { service.audit(dto.getId(), dto.getRevision(), dto.isApprove(), dto.getReason()); return Result.success(); }
    @GetMapping("/page") @RequireRole(UserRole.ADMIN)
    public Result<Page<StaffSkill>> page(@RequestParam(defaultValue="1") @Min(1) int pageNum,
            @RequestParam(defaultValue="20") @Min(1) @Max(100) int pageSize, @RequestParam(required=false) String status) {
        return Result.success(service.page(pageNum, pageSize, status));
    }
    @GetMapping("/{id}") @RequireRole({UserRole.STAFF, UserRole.ADMIN})
    public Result<StaffSkill> detail(@PathVariable Long id) { return Result.success(service.detail(id)); }
}
