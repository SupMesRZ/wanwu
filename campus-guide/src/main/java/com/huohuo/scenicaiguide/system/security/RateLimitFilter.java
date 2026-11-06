package com.huohuo.scenicaiguide.system.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huohuo.scenicaiguide.common.api.ApiResponse;
import com.huohuo.scenicaiguide.common.enums.ErrorCode;
import com.huohuo.scenicaiguide.system.service.RateLimitService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 接口限流过滤器
 * <p>
 * 注册在 {@code JwtAuthFilter} 之后，已认证请求按身份计数、匿名按 IP。
 * 命中多条规则时全部都要通过——规则可以叠加（如"全局限流 + 登录专项限流"），
 * 先命中先过会制造绕过路径
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String API_PREFIX = "/api/";

    private final RateLimitService rateLimitService;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(RateLimitService rateLimitService, ObjectMapper objectMapper) {
        this.rateLimitService = rateLimitService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(API_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!rateLimitService.enabled()) {
            filterChain.doFilter(request, response);
            return;
        }
        List<RateLimitRule> matched = rateLimitService.rules().stream()
            .filter(rule -> rule.matches(request.getRequestURI(), request.getMethod()))
            .toList();
        if (matched.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }
        String identity = resolveIdentity(request);
        for (RateLimitRule rule : matched) {
            if (!rateLimitService.tryAcquire(rule, identity)) {
                writeRejection(response, rule);
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    /**
     * 已认证按 subject 计数，匿名按客户端 IP
     */
    private String resolveIdentity(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthPrincipal principal) {
            return principal.subject();
        }
        return "ip:" + resolveIp(request);
    }

    private String resolveIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return comma > 0 ? forwarded.substring(0, comma).trim() : forwarded.trim();
        }
        return request.getRemoteAddr();
    }

    private void writeRejection(HttpServletResponse response, RateLimitRule rule) throws IOException {
        response.setStatus(429);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        ApiResponse<Void> body = new ApiResponse<>(
            ErrorCode.RATE_LIMIT.name(),
            "请求过于频繁（" + rule.getName() + "），请稍后再试",
            null, null);
        objectMapper.writeValue(response.getWriter(), body);
    }
}
