package com.huohuo.scenicaiguide.system.enums;

import com.huohuo.scenicaiguide.common.redis.RedisFeature;

/**
 * 系统支撑域 Redis 功能标识枚举
 */
public enum SystemRedisFeature implements RedisFeature {

    /**
     * Session 会话
     */
    SESSION("session"),

    /**
     * 管理员设备信任
     */
    ADMIN_TRUST("admin-trust"),

    /**
     * 短信验证码
     */
    SMS_CODE("sms-code"),

    /**
     * 游客位置
     */
    LOCATION("location"),

    /**
     * 封禁状态缓存
     */
    BAN("ban"),

    /**
     * 短信验证码发送限流
     */
    SMS_LIMIT("sms-limit"),

    /**
     * 全局配置缓存
     */
    CONFIG("config"),

    /**
     * 管理员权限集缓存
     */
    USER_PERMISSION("user-permission"),

    /**
     * 接口限流计数
     */
    RATE_LIMIT("rate-limit");

    private final String value;

    SystemRedisFeature(String value) {
        this.value = value;
    }

    @Override
    public String getValue() {
        return value;
    }
}
