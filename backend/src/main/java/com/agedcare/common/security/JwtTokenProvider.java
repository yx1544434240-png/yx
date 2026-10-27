package com.agedcare.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class JwtTokenProvider {

    @Value("${jwt.secret:}")
    private String secret;

    @Value("${jwt.expiration:604800000}")
    private long expiration;

    @Value("${app.dev-mode:false}")
    private boolean devMode;

    private volatile SecretKey signingKey;

    private SecretKey getSigningKey() {
        if (signingKey != null) {
            return signingKey;
        }
        synchronized (this) {
            if (signingKey == null) {
                if (StringUtils.hasText(secret) && secret.getBytes(StandardCharsets.UTF_8).length >= 32) {
                    signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
                } else if (devMode) {
                    log.warn("JWT Secret 未配置或长度不足32字节，开发模式下生成临时内存密钥");
                    signingKey = Jwts.SIG.HS256.key().build();
                } else {
                    throw new IllegalStateException("生产环境必须通过环境变量 JWT_SECRET 配置至少256位（32字节）密钥！");
                }
            }
        }
        return signingKey;
    }

    public String generateToken(LoginUser user) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        claims.put("username", user.getUsername());
        claims.put("realName", user.getRealName());
        claims.put("role", user.getRole());
        claims.put("avatar", user.getAvatar());

        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claims(claims)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    public LoginUser parseToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            Object userIdObj = claims.get("userId");
            Long userId = null;
            if (userIdObj instanceof Number) {
                userId = ((Number) userIdObj).longValue();
            } else if (userIdObj != null) {
                userId = Long.parseLong(userIdObj.toString());
            }

            return LoginUser.builder()
                    .id(userId)
                    .username((String) claims.get("username"))
                    .realName((String) claims.get("realName"))
                    .role((String) claims.get("role"))
                    .avatar((String) claims.get("avatar"))
                    .build();
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("JWT token 解析失败或已失效: {}", e.getMessage());
            return null;
        }
    }
}
