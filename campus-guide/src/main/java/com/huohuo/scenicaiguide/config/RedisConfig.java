package com.huohuo.scenicaiguide.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 配置类
 * <p>
 * 配置 StringRedisTemplate 序列化方式，统一使用 String 序列化
 */
@Configuration
public class RedisConfig {

    /**
     * 配置 StringRedisTemplate
     * <p>
     * key、value、hashKey、hashValue 全部使用 StringRedisSerializer，避免 JDK 默认序列化的可读性问题
     *
     * @param factory Redis 连接工厂
     * @return 配置好的 StringRedisTemplate
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
        StringRedisTemplate template = new StringRedisTemplate(factory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new StringRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }
}
