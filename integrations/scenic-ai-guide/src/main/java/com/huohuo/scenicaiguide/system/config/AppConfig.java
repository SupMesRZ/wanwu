package com.huohuo.scenicaiguide.system.config;

import com.huohuo.scenicaiguide.system.service.ConfigService;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 应用配置静态工具类
 *
 * <p>提供类型安全的全局配置读取接口，各业务域可直接通过静态方法获取配置
 * <p>读取顺序：Redis 缓存 → 数据库 → application.yaml 兜底
 * <p>配置热更新：管理后台修改后自动刷新，无需重启应用
 *
 * <p>使用示例：
 * <pre>
 *   int radius = AppConfig.getInt("fence.radius-meters", 2000);
 *   boolean enabled = AppConfig.getBoolean("fence.enabled", true);
 *   String mode = AppConfig.getString("auth.mode", "STANDARD");
 * </pre>
 */
@Component
public class AppConfig {

    private static ConfigService configService;

    private final ConfigService injectedConfigService;

    @Autowired
    public AppConfig(ConfigService configService) {
        this.injectedConfigService = configService;
    }

    @PostConstruct
    public void init() {
        AppConfig.configService = injectedConfigService;
    }

    /**
     * 获取字符串配置
     *
     * @param key 配置键
     * @return 配置值，不存在返回 null
     */
    public static String getString(String key) {
        return configService.getString(key);
    }

    /**
     * 获取字符串配置（带默认值）
     *
     * @param key          配置键
     * @param defaultValue 默认值
     * @return 配置值或默认值
     */
    public static String getString(String key, String defaultValue) {
        return configService.getString(key, defaultValue);
    }

    /**
     * 获取 int 配置
     *
     * @param key          配置键
     * @param defaultValue 默认值
     * @return 配置值或默认值
     */
    public static int getInt(String key, int defaultValue) {
        return configService.getInt(key, defaultValue);
    }

    /**
     * 获取 long 配置
     *
     * @param key          配置键
     * @param defaultValue 默认值
     * @return 配置值或默认值
     */
    public static long getLong(String key, long defaultValue) {
        return configService.getLong(key, defaultValue);
    }

    /**
     * 获取 double 配置
     *
     * @param key          配置键
     * @param defaultValue 默认值
     * @return 配置值或默认值
     */
    public static double getDouble(String key, double defaultValue) {
        return configService.getDouble(key, defaultValue);
    }

    /**
     * 获取 boolean 配置
     *
     * @param key          配置键
     * @param defaultValue 默认值
     * @return 配置值或默认值
     */
    public static boolean getBoolean(String key, boolean defaultValue) {
        return configService.getBoolean(key, defaultValue);
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
    public static <T extends Enum<T>> T getEnum(String key, Class<T> enumClass, T defaultValue) {
        return configService.getEnum(key, enumClass, defaultValue);
    }
}
