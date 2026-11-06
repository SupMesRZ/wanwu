package com.huohuo.scenicaiguide.system.security;

import com.huohuo.scenicaiguide.common.redis.RedisDomain;
import com.huohuo.scenicaiguide.common.redis.RedisUtils;
import com.huohuo.scenicaiguide.system.enums.SystemRedisFeature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Redis Session 工具类
 * <p>
 * 封装 Session 的创建、查询、删除操作
 */
@Component
public class RedisSessionUtil {

    private final RedisUtils redisUtils;

    @Autowired
    public RedisSessionUtil(RedisUtils redisUtils) {
        this.redisUtils = redisUtils;
    }

    /**
     * 创建 Session
     *
     * @param key           Redis key 后缀（用户标识）
     * @param value         Session 值
     * @param expireMinutes 过期时长（分钟）
     */
    public void create(String key, String value, long expireMinutes) {
        redisUtils.setWithSuffix(RedisDomain.SYSTEM, SystemRedisFeature.SESSION,
            key, value, expireMinutes, TimeUnit.MINUTES);
    }

    /**
     * 判断 Session 是否存在
     *
     * @param key Redis key 后缀
     * @return true 表示存在
     */
    public boolean exists(String key) {
        return Boolean.TRUE.equals(redisUtils.hasKeyWithSuffix(
            RedisDomain.SYSTEM, SystemRedisFeature.SESSION, key));
    }

    /**
     * 删除 Session
     *
     * @param key Redis key 后缀
     */
    public void delete(String key) {
        redisUtils.deleteWithSuffix(RedisDomain.SYSTEM, SystemRedisFeature.SESSION, key);
    }
}
