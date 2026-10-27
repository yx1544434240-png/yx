package com.agedcare.modules.stat.controller;

import com.agedcare.common.api.Result;
import com.agedcare.common.enums.UserRole;
import com.agedcare.common.security.RequireRole;
import com.agedcare.modules.stat.dto.DashboardStatVO;
import com.agedcare.modules.stat.service.StatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "数据统计大屏")
@RestController
@RequestMapping("/stats")
@RequiredArgsConstructor
public class StatController {

    private final StatService statService;

    @Operation(summary = "获取大屏综合运营指标看板数据")
    @GetMapping("/dashboard")
    @RequireRole(UserRole.ADMIN)
    public Result<DashboardStatVO> getDashboard() {
        return Result.success(statService.getDashboardStat());
    }
}
