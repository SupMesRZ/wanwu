package com.huohuo.scenicaiguide.system.service;

import com.huohuo.scenicaiguide.common.exception.BusinessException;
import com.huohuo.scenicaiguide.system.security.AuthPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * ConfigAdminService 单元测试
 *
 * <p>验证更新配置时 updatedBy（adminId）能从 AuthPrincipal.userId() 正确解析
 */
class ConfigAdminServiceTest {

    private ConfigService configService;
    private ConfigAdminService configAdminService;

    @BeforeEach
    void setUp() {
        configService = mock(ConfigService.class);
        configAdminService = new ConfigAdminService(configService);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    /**
     * 模拟管理员登录态：subject = "admin_1"，userId = 1
     */
    void mockAdminAuthentication(Long userId) {
        String subject = userId != null ? "admin_" + userId : "admin_unknown";
        AuthPrincipal principal = new AuthPrincipal(
            subject, "ADMIN", null, false, userId);
        Authentication auth = new UsernamePasswordAuthenticationToken(
            principal, null,
            Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    void update_shouldPassAdminIdAsUpdatedBy() {
        mockAdminAuthentication(1L);

        configAdminService.updateByDomain("system", "auth-test-key", "test-value", "test desc");

        ArgumentCaptor<Long> adminIdCaptor = ArgumentCaptor.forClass(Long.class);
        verify(configService).set(eq("auth-test-key"), eq("test-value"), eq("test desc"), adminIdCaptor.capture());
        assertEquals(1L, adminIdCaptor.getValue());
    }

    @Test
    void batchUpdate_shouldPassAdminIdAsUpdatedBy() {
        mockAdminAuthentication(42L);

        java.util.Map<String, String> configs = java.util.Map.of("auth-k1", "v1", "ops-k2", "v2");
        configAdminService.batchUpdateByDomain("system", configs);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.Map<String, String>> configsCaptor = ArgumentCaptor.forClass(java.util.Map.class);
        ArgumentCaptor<Long> adminIdCaptor = ArgumentCaptor.forClass(Long.class);
        verify(configService).batchSet(configsCaptor.capture(), adminIdCaptor.capture());
        assertEquals(42L, adminIdCaptor.getValue());
    }

    @Test
    void updateByDomain_shouldNormalizeBusinessDomainKey() {
        mockAdminAuthentication(7L);

        configAdminService.updateByDomain("qa", "rag-top-k", "6", null);

        verify(configService).set(eq("qa-rag-top-k"), eq("6"), isNull(), eq(7L));
    }

    @Test
    void updateByDomain_shouldKeepExistingBusinessDomainPrefix() {
        mockAdminAuthentication(7L);

        configAdminService.updateByDomain("route", "route-max-targets", "12", null);

        verify(configService).set(eq("route-max-targets"), eq("12"), isNull(), eq(7L));
    }

    @Test
    void updateByDomain_shouldNormalizeKeySeparatorsBeforePrefixCheck() {
        mockAdminAuthentication(7L);

        configAdminService.updateByDomain("qa", "qa_rag_top_k", "6", null);

        verify(configService).set(eq("qa-rag-top-k"), eq("6"), isNull(), eq(7L));
    }

    @Test
    void updateByDomain_shouldMapDigitalHumanToDhPrefix() {
        mockAdminAuthentication(7L);

        configAdminService.updateByDomain("digitalhuman", "tts-cache-ttl-hours", "72", null);

        verify(configService).set(eq("dh-tts-cache-ttl-hours"), eq("72"), isNull(), eq(7L));
    }

    @Test
    void updateByDomain_shouldNotPrefixSystemDomainKey() {
        mockAdminAuthentication(7L);

        configAdminService.updateByDomain("system", "ops-rate-limit-enabled", "false", null);

        verify(configService).set(eq("ops-rate-limit-enabled"), eq("false"), isNull(), eq(7L));
    }

    @Test
    void updateByDomain_shouldRejectBusinessKeyInSystemDomain() {
        mockAdminAuthentication(7L);

        assertThrows(BusinessException.class,
            () -> configAdminService.updateByDomain("system", "qa-rag-top-k", "6", null));
        verify(configService, never()).set(anyString(), anyString(), any(), any());
    }

    @Test
    void batchUpdateByDomain_shouldNormalizeKeys() {
        mockAdminAuthentication(8L);

        java.util.Map<String, String> configs = java.util.Map.of("top-k", "5", "qa-context-max-rounds", "10");
        configAdminService.batchUpdateByDomain("qa", configs);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.Map<String, String>> configsCaptor = ArgumentCaptor.forClass(java.util.Map.class);
        verify(configService).batchSet(configsCaptor.capture(), eq(8L));
        assertEquals("5", configsCaptor.getValue().get("qa-top-k"));
        assertEquals("10", configsCaptor.getValue().get("qa-context-max-rounds"));
    }

    @Test
    void update_whenUserIdIsNull_shouldPassNullAsUpdatedBy() {
        // 模拟无法解析 userId 的场景（如 subject 格式异常）
        AuthPrincipal principal = new AuthPrincipal(
            "admin_invalid", "ADMIN", null, false, null);
        Authentication auth = new UsernamePasswordAuthenticationToken(
            principal, null,
            Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        configAdminService.updateByDomain("system", "auth-test-key", "test-value", null);

        verify(configService).set(eq("auth-test-key"), eq("test-value"), isNull(), isNull());
    }

    @Test
    void update_whenNoAuthentication_shouldPassNullAsUpdatedBy() {
        SecurityContextHolder.clearContext();

        configAdminService.updateByDomain("system", "auth-test-key", "test-value", null);

        verify(configService).set(eq("auth-test-key"), eq("test-value"), isNull(), isNull());
    }
}
