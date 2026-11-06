package com.huohuo.scenicaiguide.system.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * JWT 认证过滤器
 * <p>
 * 解析 JWT token + 查 Redis Session → 注入 SecurityContext
 * <p>
 * dev 模式下只解析不拦截，strict 模式下 Spring Security 鉴权规则接管
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(JwtAuthFilter.class);

    private final JwtUtil jwtUtil;
    private final RedisSessionUtil sessionUtil;

    @Autowired
    public JwtAuthFilter(JwtUtil jwtUtil, RedisSessionUtil sessionUtil) {
        this.jwtUtil = jwtUtil;
        this.sessionUtil = sessionUtil;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String token = extractToken(request);
        if (token != null) {
            try {
                Claims claims = jwtUtil.parseToken(token);
                String sessionKey = buildSessionKey(claims);
                if (sessionUtil.exists(sessionKey)) {
                    Authentication auth = buildAuthentication(claims);
                    SecurityContextHolder.getContext().setAuthentication(auth);

                    String sub = claims.getSubject();
                    if (sub.startsWith("anon_")) {
                        MDC.put("deviceId", sub.substring(5));
                    } else {
                        MDC.put("userId", sub);
                    }
                }
            } catch (Exception exception) {
                LOGGER.debug("JWT token parsing failed: method={}, uri={}, remoteAddr={}, message={}",
                    request.getMethod(), request.getRequestURI(), request.getRemoteAddr(), exception.getMessage(), exception);
            }
        }
        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }

    private String buildSessionKey(Claims claims) {
        return claims.getSubject();
    }

    private Authentication buildAuthentication(Claims claims) {
        String role = claims.get("role", String.class);
        String authLevel = claims.get("authLevel", String.class);
        Boolean isRoot = claims.get("isRoot", Boolean.class);
        Long userId = extractUserId(claims.getSubject());
        List<GrantedAuthority> authorities = Collections.singletonList(
            new SimpleGrantedAuthority("ROLE_" + role));
        return new UsernamePasswordAuthenticationToken(
            new AuthPrincipal(claims.getSubject(), role, authLevel, isRoot, userId),
            null, authorities);
    }

    /**
     * 从 JWT subject 中提取数字用户ID
     * <p>
     * 支持格式：admin_1、user_1、temp_admin_1 等末尾数字格式
     * 匿名游客 anon_{deviceId} 返回 null
     */
    private Long extractUserId(String subject) {
        if (subject == null) {
            return null;
        }
        int lastUnderscore = subject.lastIndexOf('_');
        if (lastUnderscore == -1 || lastUnderscore == subject.length() - 1) {
            return null;
        }
        String suffix = subject.substring(lastUnderscore + 1);
        try {
            return Long.parseLong(suffix);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
