package com.agedcare.modules.staff.controller;
import com.agedcare.common.api.Result;
import com.agedcare.common.enums.UserRole;
import com.agedcare.common.security.RequireRole;
import com.agedcare.modules.staff.dto.StaffApplicationDTO;
import com.agedcare.modules.staff.entity.StaffApplication;
import com.agedcare.modules.staff.service.StaffApplicationService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;

@RestController @RequestMapping("/staff/applications") @RequiredArgsConstructor @Validated
public class StaffApplicationController {
    private final StaffApplicationService service;
    @GetMapping("/mine") @RequireRole({UserRole.RESIDENT, UserRole.STAFF})
    public Result<StaffApplication> mine() { return Result.success(service.mine()); }
    @PostMapping("/submit") @RequireRole(UserRole.RESIDENT)
    public Result<Void> submit(@Valid @RequestBody StaffApplicationDTO dto) { service.submit(dto); return Result.success(); }
    @GetMapping("/page") @RequireRole(UserRole.ADMIN)
    public Result<Page<StaffApplication>> page(@RequestParam(defaultValue="1") @Min(1) int pageNum,
            @RequestParam(defaultValue="20") @Min(1) @Max(100) int pageSize, @RequestParam(required=false) String status) { return Result.success(service.page(pageNum,pageSize,status)); }
    @Data public static class AuditDTO {
        @NotNull @Positive private Long id;
        @Min(1) private int revision;
        private boolean approve;
        @Size(max=200) private String reason;
        @Size(max=100) private String stationName;
        @DecimalMin("-180") @DecimalMax("180") private BigDecimal longitude;
        @DecimalMin("-90") @DecimalMax("90") private BigDecimal latitude;
    }
    @PostMapping("/audit") @RequireRole(UserRole.ADMIN)
    public Result<Void> audit(@Valid @RequestBody AuditDTO dto) {
        service.audit(dto.getId(),dto.getRevision(),dto.isApprove(),dto.getReason(),dto.getStationName(),dto.getLongitude(),dto.getLatitude());
        return Result.success();
    }
}
