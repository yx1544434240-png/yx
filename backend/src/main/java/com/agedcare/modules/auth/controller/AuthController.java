package com.agedcare.modules.auth.controller;

import com.agedcare.common.api.Result;
import com.agedcare.common.security.LoginUser;
import com.agedcare.common.security.UserContext;
import com.agedcare.modules.auth.dto.AuthResponse;
import com.agedcare.modules.auth.dto.LoginRequest;
import com.agedcare.modules.auth.dto.WechatLoginRequest;
import com.agedcare.modules.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "认证授权中心")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "账号密码登录")
    @PostMapping("/login")
    public Result<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(authService.login(request));
    }

    @Operation(summary = "微信小程序一键授权登录")
    @PostMapping("/wechat-login")
    public Result<AuthResponse> wechatLogin(@Valid @RequestBody WechatLoginRequest request) {
        return Result.success(authService.wechatLogin(request));
    }

    @Operation(summary = "获取当前登录用户上下文")
    @GetMapping("/current-user")
    public Result<LoginUser> getCurrentUser() {
        return Result.success(UserContext.getUser());
    }

    @PostMapping("/refresh")
    public Result<AuthResponse> refresh() { return Result.success(authService.refresh()); }
}
