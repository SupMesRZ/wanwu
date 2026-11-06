package com.huohuo.scenicaiguide.common.redis;

/**
 * Redis 功能标识接口
 * <p>
 * 各业务域的功能枚举需实现此接口，用于 Redis key 的 {功能} 段拼接
 */
public interface RedisFeature {

    /**
     * 功能标识值
     *
     * @return 功能标识字符串
     */
    String getValue();
}
