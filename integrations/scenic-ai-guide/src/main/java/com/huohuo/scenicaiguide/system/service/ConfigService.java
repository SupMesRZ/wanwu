package com.huohuo.scenicaiguide.system.service;

import com.huohuo.scenicaiguide.common.redis.RedisDomain;
import com.huohuo.scenicaiguide.system.config.AppConfigProperties;
import com.huohuo.scenicaiguide.system.entity.SysConfig;
import com.huohuo.scenicaiguide.system.enums.SystemRedisFeature;
import com.huohuo.scenicaiguide.system.mapper.SysConfigMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 配置服务
 * <p>
 * 三层读取策略：Redis 缓存 → 数据库 → application.yaml 兜底
 * <p>
 * 热更新机制：修改数据库后删除 Redis key，下次请求自动从数据库刷新
 */
@Service
public class ConfigService {

    private static final String REDIS_PREFIX = "scenic-ai-guide:" + RedisDomain.SYSTEM.getDomain() + ":" + SystemRedisFeature.CONFIG.getValue() + ":";

    private final SysConfigMapper configMapper;
    private final AppConfigProperties appConfigProperties;
    private final StringRedisTemplate redisTemplate;

    @Autowired
    public ConfigService(SysConfigMapper configMapper,
                         AppConfigProperties appConfigProperties,
                         StringRedisTemplate redisTemplate) {
        this.configMapper = configMapper;
        this.appConfigProperties = appConfigProperties;
        this.redisTemplate = redisTemplate;
    }

    /**
     * 获取字符串配置
     *
     * @param key 配置键，支持点分格式（如 fence.radius-meters）或横线格式（如 fence-radius-meters）
     * @return 配置值，不存在时返回 null
     */
    public String getString(String key) {
        String normalizedKey = normalizeKey(key);

        // L1: Redis 缓存
        String cached = redisTemplate.opsForValue().get(redisKey(normalizedKey));
        if (cached != null) {
            return cached;
        }

        // L2: 数据库
        SysConfig config = configMapper.selectByKey(normalizedKey);
        if (config != null && config.getConfigValue() != null) {
            // 写入 Redis，永久有效（靠更新时删除来刷新）
            redisTemplate.opsForValue().set(redisKey(normalizedKey), config.getConfigValue());
            return config.getConfigValue();
        }

        // L3: application.yaml 兜底
        String yamlValue = appConfigProperties.get(normalizedKey);
        if (yamlValue != null) {
            // yaml 值也写入 Redis，避免下次再 miss
            redisTemplate.opsForValue().set(redisKey(normalizedKey), yamlValue);
        }
        return yamlValue;
    }

    /**
     * 获取字符串配置（带默认值）
     *
     * @param key          配置键
     * @param defaultValue 默认值
     * @return 配置值或默认值
     */
    public String getString(String key, String defaultValue) {
        String value = getString(key);
        return value != null ? value : defaultValue;
    }

    /**
     * 获取 int 配置
     *
     * @param key          配置键
     * @param defaultValue 默认值
     * @return 配置值或默认值
     */
    public int getInt(String key, int defaultValue) {
        String value = getString(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 获取 long 配置
     *
     * @param key          配置键
     * @param defaultValue 默认值
     * @return 配置值或默认值
     */
    public long getLong(String key, long defaultValue) {
        String value = getString(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 获取 double 配置
     *
     * @param key          配置键
     * @param defaultValue 默认值
     * @return 配置值或默认值
     */
    public double getDouble(String key, double defaultValue) {
        String value = getString(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 获取 boolean 配置
     * <p>
     * 支持 "true"/"false"/"1"/"0"/"yes"/"no"（忽略大小写）
     *
     * @param key          配置键
     * @param defaultValue 默认值
     * @return 配置值或默认值
     */
    public boolean getBoolean(String key, boolean defaultValue) {
        String value = getString(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return switch (value.trim().toLowerCase()) {
            case "true", "1", "yes", "on" -> true;
            case "false", "0", "no", "off" -> false;
            default -> defaultValue;
        };
    }

    /**
     * 获取枚举配置
     *
     * @param key          配置键
     * @param enumClass    枚举类
     * @param defaultValue 默认值
     * @param <T>          枚举类型
     * @return 配置值或默认值
     */
    public <T extends Enum<T>> T getEnum(String key, Class<T> enumClass, T defaultValue) {
        String value = getString(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Enum.valueOf(enumClass, value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return defaultValue;
        }
    }

    // ========== 管理操作 ==========

    /**
     * 更新或插入配置
     *
     * @param key         配置键
     * @param value       配置值
     * @param description 配置说明，null 表示不更新说明
     * @param updatedBy   更新人 ID
     */
    public void set(String key, String value, String description, Long updatedBy) {
        String normalizedKey = normalizeKey(key);

        SysConfig existing = configMapper.selectByKey(normalizedKey);
        if (existing != null) {
            // 更新
            existing.setConfigValue(value);
            if (description != null) {
                existing.setDescription(description);
            }
            existing.setUpdatedAt(LocalDateTime.now());
            existing.setUpdatedBy(updatedBy);
            configMapper.updateById(existing);
        } else {
            // 新增
            SysConfig config = new SysConfig();
            config.setConfigKey(normalizedKey);
            config.setConfigValue(value);
            config.setDescription(description);
            config.setCreatedAt(LocalDateTime.now());
            config.setUpdatedAt(LocalDateTime.now());
            config.setUpdatedBy(updatedBy);
            configMapper.insert(config);
        }

        // 删除 Redis 缓存，下次请求自动刷新
        redisTemplate.delete(redisKey(normalizedKey));
    }

    /**
     * 批量更新配置
     *
     * @param configs   配置键值对
     * @param updatedBy 更新人 ID
     */
    public void batchSet(java.util.Map<String, String> configs, Long updatedBy) {
        for (java.util.Map.Entry<String, String> entry : configs.entrySet()) {
            set(entry.getKey(), entry.getValue(), null, updatedBy);
        }
    }

    /**
     * 查询所有有效配置
     *
     * @return 配置列表
     */
    public List<SysConfig> listAll() {
        return configMapper.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysConfig>()
                .eq(SysConfig::getDeleted, 0)
                .orderByAsc(SysConfig::getConfigKey)
        );
    }

    /**
     * 根据 key 查询配置
     *
     * @param key 配置键
     * @return 配置实体，不存在返回 null
     */
    public SysConfig getByKey(String key) {
        return configMapper.selectByKey(normalizeKey(key));
    }

    // ========== 按域查询 ==========

    /**
     * 查询指定域的所有配置
     * <p>
     * 域通过配置键前缀识别，如 "scenic-" 对应 scenic 域
     *
     * @param domainPrefix 域前缀，如 "auth"、"scenic"、"qa"
     * @return 配置列表
     */
    public List<SysConfig> listByDomain(String domainPrefix) {
        String prefix = normalizeKey(domainPrefix) + "-";
        return configMapper.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysConfig>()
                .eq(SysConfig::getDeleted, 0)
                .likeRight(SysConfig::getConfigKey, prefix)
                .orderByAsc(SysConfig::getConfigKey)
        );
    }

    /**
     * 查询指定前缀的配置
     *
     * @param prefix 前缀，如 "scenic-fence"
     * @return 配置列表
     */
    public List<SysConfig> listByPrefix(String prefix) {
        String normalized = normalizeKey(prefix);
        return configMapper.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysConfig>()
                .eq(SysConfig::getDeleted, 0)
                .likeRight(SysConfig::getConfigKey, normalized)
                .orderByAsc(SysConfig::getConfigKey)
        );
    }

    // ========== 私有方法 ==========

    /**
     * 规范化配置键：将点分格式统一为横线格式（与 yaml 和数据库保持一致）
     * <p>
     * 例如："fence.radiusMeters" → "fence-radius-meters"
     * "fence.radius_meters" → "fence-radius-meters"
     * "fence.radius-meters" → "fence-radius-meters"（不变）
     *
     * @param key 原始配置键
     * @return 规范化后的配置键
     */
    private String normalizeKey(String key) {
        if (key == null) {
            return null;
        }
        // 将点分和下划线统一替换为横线
        return key.replace('.', '-').replace('_', '-');
    }

    private String redisKey(String normalizedKey) {
        return REDIS_PREFIX + normalizedKey;
    }
}
