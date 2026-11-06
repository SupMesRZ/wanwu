package com.huohuo.scenicaiguide.system.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huohuo.scenicaiguide.common.redis.RedisDomain;
import com.huohuo.scenicaiguide.common.redis.RedisUtils;
import com.huohuo.scenicaiguide.system.enums.SystemRedisFeature;
import com.huohuo.scenicaiguide.system.security.RateLimitRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 接口限流服务
 * <p>
 * 固定窗口计数：窗口号 = 当前秒数 / 窗口时长，计数 key 含窗口号，
 * 窗口滚动自然过期。Redis 故障一律放行——限流是保护性措施，
 * 不能反过来成为可用性单点
 */
@Service
public class RateLimitService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RateLimitService.class);

    public static final String RULES_CONFIG_KEY = "ops-rate-limit-rules";
    public static final String ENABLED_CONFIG_KEY = "ops-rate-limit-enabled";

    private final ConfigService configService;
    private final RedisUtils redisUtils;
    private final ObjectMapper objectMapper;

    /** 原文没变就不必重新解析 JSON，配置中心读一次（有 Redis 缓存）比解析便宜 */
    private volatile String cachedRaw;
    private volatile List<RateLimitRule> cachedRules = List.of();

    public RateLimitService(ConfigService configService, RedisUtils redisUtils, ObjectMapper objectMapper) {
        this.configService = configService;
        this.redisUtils = redisUtils;
        this.objectMapper = objectMapper;
    }

    public boolean enabled() {
        try {
            return configService.getBoolean(ENABLED_CONFIG_KEY, true);
        } catch (Exception e) {
            // 与操作日志同一口径：配置读不到时保护性措施默认保持开启
            return true;
        }
    }

    /**
     * 当前生效的规则。JSON 解析失败按空规则处理：配置写坏了不该把所有请求挡在门外
     */
    public List<RateLimitRule> rules() {
        String raw;
        try {
            raw = configService.getString(RULES_CONFIG_KEY, "[]");
        } catch (Exception e) {
            return List.of();
        }
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        if (raw.equals(cachedRaw)) {
            return cachedRules;
        }
        try {
            List<RateLimitRule> parsed = objectMapper.readValue(raw, new TypeReference<>() { });
            List<RateLimitRule> effective = parsed.stream().filter(RateLimitRule::isEffective).toList();
            cachedRaw = raw;
            cachedRules = effective;
            return effective;
        } catch (Exception e) {
            LOGGER.warn("限流规则 JSON 解析失败，本次按不限流处理：{}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 尝试为指定身份在规则窗口内计一次数
     *
     * @return true=放行，false=超限
     */
    public boolean tryAcquire(RateLimitRule rule, String identity) {
        long window = Instant.now().getEpochSecond() / rule.getWindowSeconds();
        // 身份里可能带冒号（如 ip:1.2.3.4），不影响 key 结构，后缀整体作为一段即可
        String suffix = rule.getName() + ":" + identity + ":" + window;
        try {
            Long count = redisUtils.incrementWithSuffix(
                RedisDomain.SYSTEM, SystemRedisFeature.RATE_LIMIT, suffix,
                rule.getWindowSeconds(), TimeUnit.SECONDS);
            return count == null || count <= rule.getLimit();
        } catch (Exception e) {
            LOGGER.warn("限流计数失败，按放行处理 rule={}：{}", rule.getName(), e.getMessage());
            return true;
        }
    }
}
