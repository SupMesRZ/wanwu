package com.huohuo.scenicaiguide.tourist.enums;

import com.huohuo.scenicaiguide.common.redis.RedisFeature;
import lombok.Getter;

/**
 * 游客会话域 Redis 功能标识枚举
 * <p>
 * 定义 tourist 域下所有 Redis 功能标识，key 格式: scenic-ai-guide:tourist:{功能}
 */
@Getter
public enum TouristRedisFeature implements RedisFeature {

    SESSION("session"),
    LOCATION("location"),
    GEOFENCE("geofence");

    private final String value;

    TouristRedisFeature(String value) {
        this.value = value;
    }
}
