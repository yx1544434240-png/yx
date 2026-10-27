package com.agedcare.common.security;

import com.agedcare.common.api.ResultCode;
import com.agedcare.common.enums.UserRole;
import com.agedcare.common.exception.BusinessException;
import com.agedcare.modules.user.entity.SysUser;
import com.agedcare.modules.user.mapper.SysUserMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtTokenProvider jwtTokenProvider;
    private final SysUserMapper sysUserMapper;
    private final UploadRateLimiter uploadRateLimiter;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        UserContext.clear();
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        HandlerMethod handlerMethod = handler instanceof HandlerMethod method ? method : null;

        String authHeader = request.getHeader("Authorization");
        String token = null;
        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        }

        LoginUser user = null;
        if (StringUtils.hasText(token)) {
            user = jwtTokenProvider.parseToken(token);
        }

        // 允许公开只读的服务项目详情 GET /service-items/{numericId}
        String uri = request.getRequestURI();
        boolean isPublicGetItemDetail = "GET".equalsIgnoreCase(request.getMethod())
                && uri.matches(".*/service-items/\\d+$");

        // 默认全端强制登录校验: 非白名单请求若无有效凭据直接拒绝 401
        if (user == null) {
            if (isPublicGetItemDetail) {
                return true;
            }
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }

        // 实时复核账号状态: 被停用账号即使持有未过期 Token 也立即失效 (B9)
        SysUser dbUser = sysUserMapper.selectById(user.getId());
        if (dbUser == null || (dbUser.getStatus() != null && dbUser.getStatus() == 0)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED.getCode(), "账号已被停用或注销，请联系社区工作人员");
        }

        user.setPhone(dbUser.getPhone());
        user.setOpenid(dbUser.getOpenid());
        user.setAvatar(dbUser.getAvatar());
        if (!Arrays.asList("ADMIN", "STAFF", "RESIDENT").contains(dbUser.getRole()))
            throw new BusinessException(ResultCode.FORBIDDEN);
        user.setRole(dbUser.getRole());
        if ("POST".equals(request.getMethod()) && (uri.endsWith("/upload/image") || uri.endsWith("/service-items/image") || uri.endsWith("/staff/credentials/upload"))) {
            uploadRateLimiter.check(user.getId());
        }

        // 检查方法或类上的角色权限约束
        RequireRole requireRole = handlerMethod == null ? null : handlerMethod.getMethodAnnotation(RequireRole.class);
        if (requireRole == null && handlerMethod != null) {
            requireRole = handlerMethod.getBeanType().getAnnotation(RequireRole.class);
        }

        if (requireRole != null) {
            UserRole[] allowedRoles = requireRole.value();
            if (allowedRoles.length > 0) {
                final String userRole = user.getRole();
                boolean hasPermission = Arrays.stream(allowedRoles)
                        .anyMatch(r -> r.getCode().equalsIgnoreCase(userRole));
                if (!hasPermission) {
                    throw new BusinessException(ResultCode.FORBIDDEN);
                }
            }
        }

        UserContext.setUser(user);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }
}
