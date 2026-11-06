package com.huohuo.scenicaiguide.system.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huohuo.scenicaiguide.system.service.RateLimitService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 限流过滤器测试
 * <p>
 * 纯逻辑离线测试，MockServlet 上下文 + mock 服务。
 * 钉住过滤器的四条边界：开关关闭不拦、非 API 路径不拦、
 * 超限返回 429 且业务链路不再执行、叠加规则全部要过
 */
class RateLimitFilterTest {

    private final RateLimitService rateLimitService = mock(RateLimitService.class);
    private final FilterChain filterChain = mock(FilterChain.class);
    private final RateLimitFilter filter = new RateLimitFilter(rateLimitService, new ObjectMapper());

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private RateLimitRule rule(String name, String pattern) {
        RateLimitRule rule = new RateLimitRule();
        rule.setName(name);
        rule.setPattern(pattern);
        rule.setLimit(5);
        rule.setWindowSeconds(60);
        rule.setEnabled(true);
        return rule;
    }

    private MockHttpServletRequest request(String method, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRemoteAddr("192.168.1.8");
        return request;
    }

    @Test
    void switchOffPassesEverything() throws Exception {
        when(rateLimitService.enabled()).thenReturn(false);

        filter.doFilter(request("POST", "/api/v1/admin/roles"), new MockHttpServletResponse(), filterChain);

        verify(filterChain).doFilter(any(), any());
        verify(rateLimitService, never()).tryAcquire(any(), anyString());
    }

    @Test
    void nonApiPathIsSkipped() throws Exception {
        when(rateLimitService.enabled()).thenReturn(true);

        filter.doFilter(request("GET", "/swagger-ui/index.html"), new MockHttpServletResponse(), filterChain);

        verify(filterChain).doFilter(any(), any());
        verify(rateLimitService, never()).rules();
    }

    @Test
    void unmatchedRulePassesWithoutCounting() throws Exception {
        when(rateLimitService.enabled()).thenReturn(true);
        when(rateLimitService.rules()).thenReturn(List.of(rule("auth", "/api/v1/system/auth/**")));

        filter.doFilter(request("GET", "/api/v1/admin/roles"), new MockHttpServletResponse(), filterChain);

        verify(filterChain).doFilter(any(), any());
        verify(rateLimitService, never()).tryAcquire(any(), anyString());
    }

    @Test
    void overLimitWrites429AndBreaksChain() throws Exception {
        RateLimitRule auth = rule("auth", "/api/v1/system/auth/**");
        when(rateLimitService.enabled()).thenReturn(true);
        when(rateLimitService.rules()).thenReturn(List.of(auth));
        when(rateLimitService.tryAcquire(eq(auth), anyString())).thenReturn(false);

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request("POST", "/api/v1/system/auth/admin/login"), response, filterChain);

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getContentAsString()).contains("RATE_LIMIT").contains("auth");
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void anonymousRequestsAreCountedByIp() throws Exception {
        RateLimitRule auth = rule("auth", "/api/v1/system/auth/**");
        when(rateLimitService.enabled()).thenReturn(true);
        when(rateLimitService.rules()).thenReturn(List.of(auth));
        when(rateLimitService.tryAcquire(any(), anyString())).thenReturn(true);

        filter.doFilter(request("POST", "/api/v1/system/auth/sms/send"), new MockHttpServletResponse(), filterChain);

        verify(rateLimitService).tryAcquire(auth, "ip:192.168.1.8");
        verify(filterChain).doFilter(any(), any());
    }

    @Test
    void authenticatedRequestsAreCountedBySubject() throws Exception {
        AuthPrincipal principal = new AuthPrincipal("admin_3", "ADMIN", null, false, 3L);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            principal, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        RateLimitRule adminWrite = rule("admin-write", "/api/v1/admin/**");
        when(rateLimitService.enabled()).thenReturn(true);
        when(rateLimitService.rules()).thenReturn(List.of(adminWrite));
        when(rateLimitService.tryAcquire(any(), anyString())).thenReturn(true);

        filter.doFilter(request("POST", "/api/v1/admin/roles"), new MockHttpServletResponse(), filterChain);

        // 按身份计数而不是按 IP：同一办公网出口的多个管理员不该共享一个配额
        verify(rateLimitService).tryAcquire(adminWrite, "admin_3");
    }

    @Test
    void allMatchedRulesMustPass() throws Exception {
        RateLimitRule broad = rule("global", "/api/v1/**");
        RateLimitRule specific = rule("auth", "/api/v1/system/auth/**");
        when(rateLimitService.enabled()).thenReturn(true);
        when(rateLimitService.rules()).thenReturn(List.of(broad, specific));
        when(rateLimitService.tryAcquire(eq(broad), anyString())).thenReturn(true);
        when(rateLimitService.tryAcquire(eq(specific), anyString())).thenReturn(false);

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request("POST", "/api/v1/system/auth/sms/send"), response, filterChain);

        // 宽松规则放行不代表专项规则可以跳过，否则专项限流形同虚设
        assertThat(response.getStatus()).isEqualTo(429);
    }
}
