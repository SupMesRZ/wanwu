package com.huohuo.scenicaiguide.system.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huohuo.scenicaiguide.common.redis.RedisDomain;
import com.huohuo.scenicaiguide.common.redis.RedisUtils;
import com.huohuo.scenicaiguide.system.enums.SystemRedisFeature;
import com.huohuo.scenicaiguide.system.security.RateLimitRule;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 限流服务测试
 * <p>
 * 纯逻辑离线测试。钉住三条保护性措施的底线：规则 JSON 写坏不挡请求、
 * Redis 故障放行、超限判定以"次数大于上限"为准
 */
class RateLimitServiceTest {

    private final ConfigService configService = mock(ConfigService.class);
    private final RedisUtils redisUtils = mock(RedisUtils.class);
    private final RateLimitService service =
        new RateLimitService(configService, redisUtils, new ObjectMapper());

    private RateLimitRule rule(int limit, int windowSeconds) {
        RateLimitRule rule = new RateLimitRule();
        rule.setName("test");
        rule.setPattern("/api/v1/**");
        rule.setLimit(limit);
        rule.setWindowSeconds(windowSeconds);
        rule.setEnabled(true);
        return rule;
    }

    @Test
    void brokenRulesJsonMeansNoLimiting() {
        when(configService.getString(eq(RateLimitService.RULES_CONFIG_KEY), anyString()))
            .thenReturn("{这不是JSON");

        // 配置写坏了不该把所有请求挡在门外
        assertThat(service.rules()).isEmpty();
    }

    @Test
    void ineffectiveRulesAreDropped() {
        when(configService.getString(eq(RateLimitService.RULES_CONFIG_KEY), anyString()))
            .thenReturn("["
                + "{\"name\":\"off\",\"pattern\":\"/a/**\",\"limit\":1,\"windowSeconds\":60,\"enabled\":false},"
                + "{\"name\":\"zero\",\"pattern\":\"/a/**\",\"limit\":0,\"windowSeconds\":60,\"enabled\":true},"
                + "{\"name\":\"ok\",\"pattern\":\"/a/**\",\"limit\":10,\"windowSeconds\":60,\"enabled\":true}"
                + "]");

        List<RateLimitRule> rules = service.rules();

        assertThat(rules).extracting(RateLimitRule::getName).containsExactly("ok");
    }

    @Test
    void enabledDefaultsToTrueWhenConfigReadFails() {
        when(configService.getBoolean(eq(RateLimitService.ENABLED_CONFIG_KEY), anyBoolean()))
            .thenThrow(new IllegalStateException("Redis down"));

        assertThat(service.enabled()).isTrue();
    }

    @Test
    void requestsWithinLimitAreAllowed() {
        when(redisUtils.incrementWithSuffix(
            eq(RedisDomain.SYSTEM), eq(SystemRedisFeature.RATE_LIMIT), anyString(), anyLong(), any()))
            .thenReturn(3L);

        assertThat(service.tryAcquire(rule(5, 60), "user:1")).isTrue();
    }

    @Test
    void requestBeyondLimitIsRejected() {
        when(redisUtils.incrementWithSuffix(
            eq(RedisDomain.SYSTEM), eq(SystemRedisFeature.RATE_LIMIT), anyString(), anyLong(), any()))
            .thenReturn(6L);

        assertThat(service.tryAcquire(rule(5, 60), "user:1")).isFalse();
    }

    @Test
    void redisFailureFailsOpen() {
        when(redisUtils.incrementWithSuffix(
            eq(RedisDomain.SYSTEM), eq(SystemRedisFeature.RATE_LIMIT), anyString(), anyLong(), any()))
            .thenThrow(new IllegalStateException("connection refused"));

        // 限流是保护性措施，不能反过来成为可用性单点
        assertThat(service.tryAcquire(rule(5, 60), "user:1")).isTrue();
    }

    @Test
    void counterKeyContainsRuleIdentityAndWindow() {
        when(redisUtils.incrementWithSuffix(
            eq(RedisDomain.SYSTEM), eq(SystemRedisFeature.RATE_LIMIT), contains("auth:user:9:"), anyLong(), any()))
            .thenReturn(1L);
        RateLimitRule rule = rule(10, 60);
        rule.setName("auth");

        service.tryAcquire(rule, "user:9");

        org.mockito.Mockito.verify(redisUtils).incrementWithSuffix(
            eq(RedisDomain.SYSTEM), eq(SystemRedisFeature.RATE_LIMIT),
            contains("auth:user:9:"), eq(60L), eq(TimeUnit.SECONDS));
    }

    @Test
    void ruleMatchesByPatternAndMethod() {
        RateLimitRule rule = rule(10, 60);
        rule.setPattern("/api/v1/admin/**");
        rule.setMethods(List.of("POST", "PUT"));

        assertThat(rule.matches("/api/v1/admin/roles", "POST")).isTrue();
        assertThat(rule.matches("/api/v1/admin/roles/1", "PUT")).isTrue();
        assertThat(rule.matches("/api/v1/admin/roles", "GET")).isFalse();
        assertThat(rule.matches("/api/v1/tourist/x", "POST")).isFalse();
    }

    @Test
    void emptyMethodListMatchesAnyMethod() {
        RateLimitRule rule = rule(10, 60);
        rule.setPattern("/api/v1/**");
        rule.setMethods(List.of());

        assertThat(rule.matches("/api/v1/anything", "DELETE")).isTrue();
    }
}
