package com.huohuo.scenicaiguide.system.config;

import com.huohuo.scenicaiguide.common.AbstractIntegrationTest;

import com.huohuo.scenicaiguide.system.service.ConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ConfigService 集成测试
 */
class ConfigServiceTest extends AbstractIntegrationTest {

    @Autowired
    private ConfigService configService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void cleanTestKey() {
        redisTemplate.delete("scenic-ai-guide:system:config:test-key");
    }

    @Test
    void getString_fromYamlFallback_shouldWork() {
        String value = configService.getString("ops-rate-limit-enabled");
        assertNotNull(value);
        assertEquals("true", value);
    }

    @Test
    void getString_withDefault_shouldReturnDefault_whenKeyNotExists() {
        String value = configService.getString("non-existent-key", "default-val");
        assertEquals("default-val", value);
    }

    @Test
    void getInt_shouldParseInteger() {
        int value = configService.getInt("ops-operation-log-retention-days", 9999);
        assertEquals(90, value);
    }

    @Test
    void getInt_withInvalidValue_shouldReturnDefault() {
        int value = configService.getInt("ops-rate-limit-enabled", 42);
        assertEquals(42, value);
    }

    @Test
    void getBoolean_shouldParseTrue() {
        boolean value = configService.getBoolean("system-feature-enabled", true);
        assertTrue(value);
    }

    @Test
    void getDouble_shouldParseDecimal() {
        double value = configService.getDouble("ops-operation-log-retention-days", 0.0);
        assertEquals(90.0, value, 0.001);
    }

    @Test
    void normalizeKey_shouldSupportDotNotation() {
        String value = configService.getString("ops.operation.log.retention.days");
        assertNotNull(value);
        assertEquals("90", value);
    }

    @Test
    void getLong_shouldParseLong() {
        long value = configService.getLong("ops-operation-log-retention-days", 0L);
        assertEquals(90L, value);
    }
}
