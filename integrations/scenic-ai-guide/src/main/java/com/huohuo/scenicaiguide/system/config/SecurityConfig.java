package com.huohuo.scenicaiguide.system.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huohuo.scenicaiguide.common.api.ApiResponse;
import com.huohuo.scenicaiguide.common.enums.ErrorCode;
import com.huohuo.scenicaiguide.system.security.JwtAuthFilter;
import com.huohuo.scenicaiguide.system.security.RateLimitFilter;
import com.huohuo.scenicaiguide.system.service.RateLimitService;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 配置类，定义HTTP请求的安全策略、会话管理和JWT过滤器链。
 * 支持严格模式与宽松模式切换：严格模式下所有请求需认证，宽松模式下所有请求均允许访问。
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * 是否启用严格安全模式。
     * 严格模式下除白名单路径外的所有请求均需认证；宽松模式下所有请求均允许匿名访问。
     * 默认值为 false。
     */
    @Value("${security.strict-mode:false}")
    private boolean strictMode;

    private final ObjectMapper objectMapper;

    @Autowired
    public SecurityConfig(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 配置Spring Security过滤器链，定义请求授权规则、会话策略及JWT过滤器插入位置。
     *
     * @param http         HttpSecurity构建器，用于配置CSRF、会话管理和请求授权等安全策略
     * @param jwtAuthFilter JWT认证过滤器，用于在请求进入UsernamePasswordAuthenticationFilter之前校验JWT令牌
     * @return 构建完成的SecurityFilterChain实例，由Spring Security框架在请求处理时调用
     * @throws Exception HttpSecurity配置过程中可能抛出的异常
     */
    @Bean
    public RateLimitFilter rateLimitFilter(RateLimitService rateLimitService) {
        return new RateLimitFilter(rateLimitService, objectMapper);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           JwtAuthFilter jwtAuthFilter,
                                           RateLimitFilter rateLimitFilter) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            /* 配置请求授权规则：白名单路径允许匿名访问，其余路径根据strictMode决定是否需要认证 */
            .authorizeHttpRequests(auth -> {
                auth.requestMatchers("/api/v1/system/auth/**").permitAll();
                auth.requestMatchers("/api/v1/tourist/session/init").permitAll();
                auth.requestMatchers("/api/v1/tourist/guide/**").permitAll();
                auth.requestMatchers("/api/v1/tourist/scenic-areas/*/map").permitAll();
                auth.requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**").permitAll();
                auth.requestMatchers("/error", "/actuator/**").permitAll();
                if (strictMode) {
                    auth.anyRequest().authenticated();
                } else {
                    auth.anyRequest().permitAll();
                }
            })
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setCharacterEncoding("UTF-8");
                    String traceId = resolveTraceId(request);
                    ApiResponse<Void> body = new ApiResponse<>(
                        ErrorCode.UNAUTHORIZED.name(), "未登录或令牌无效", null, traceId);
                    objectMapper.writeValue(response.getWriter(), body);
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setCharacterEncoding("UTF-8");
                    String traceId = resolveTraceId(request);
                    ApiResponse<Void> body = new ApiResponse<>(
                        ErrorCode.FORBIDDEN.name(), "无权限访问该资源", null, traceId);
                    objectMapper.writeValue(response.getWriter(), body);
                })
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            // 限流排在 JWT 之后：已认证请求按身份计数，匿名按 IP
            .addFilterAfter(rateLimitFilter, JwtAuthFilter.class);
        return http.build();
    }

    /**
     * 创建密码编码器实例，用于用户密码的加密与校验。
     *
     * @return BCryptPasswordEncoder实例，基于BCrypt算法实现密码哈希编码
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 获取 traceId：优先从 MDC 取，fallback 到 request attribute
     *
     * @param request HTTP 请求
     * @return traceId 或 null
     */
    private String resolveTraceId(jakarta.servlet.http.HttpServletRequest request) {
        String traceId = MDC.get("traceId");
        if (traceId == null) {
            traceId = (String) request.getAttribute("traceId");
        }
        return traceId;
    }
}
