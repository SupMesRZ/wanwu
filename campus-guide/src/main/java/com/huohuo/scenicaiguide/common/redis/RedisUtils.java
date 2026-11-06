package com.huohuo.scenicaiguide.common.redis;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Redis 工具类
 * <p>
 * 封装常用 Redis 操作，key 格式统一为 scenic-ai-guide:{域}:{功能}
 * <p>
 * 使用示例：
 * <pre>
 *   redisUtils.set(RedisDomain.TOURIST, TouristRedisFeature.SESSION, "value");
 *   redisUtils.set(RedisDomain.TOURIST, TouristRedisFeature.SESSION, "value", 30, TimeUnit.MINUTES);
 * </pre>
 */
@Component
public class RedisUtils {

    private static final String PREFIX = "scenic-ai-guide";

    @Autowired
    private StringRedisTemplate redisTemplate;

    /**
     * 拼接完整 Redis key
     *
     * @param domain  业务域枚举
     * @param feature 功能标识枚举
     * @return 完整 key，格式 scenic-ai-guide:{域}:{功能}
     */
    private String key(RedisDomain domain, RedisFeature feature) {
        return PREFIX + ":" + domain.getDomain() + ":" + feature.getValue();
    }

    // ==================== String ====================

    /**
     * 设置 String 值
     *
     * @param domain  业务域
     * @param feature 功能标识
     * @param value   值
     */
    public void set(RedisDomain domain, RedisFeature feature, String value) {
        redisTemplate.opsForValue().set(key(domain, feature), value);
    }

    /**
     * 设置 String 值并指定过期时间
     *
     * @param domain  业务域
     * @param feature 功能标识
     * @param value   值
     * @param timeout 过期时长
     * @param unit    时间单位
     */
    public void set(RedisDomain domain, RedisFeature feature, String value, long timeout, TimeUnit unit) {
        redisTemplate.opsForValue().set(key(domain, feature), value, timeout, unit);
    }

    /**
     * 获取 String 值
     *
     * @param domain  业务域
     * @param feature 功能标识
     * @return 值，key 不存在时返回 null
     */
    public String get(RedisDomain domain, RedisFeature feature) {
        return redisTemplate.opsForValue().get(key(domain, feature));
    }

    /**
     * 删除 key
     *
     * @param domain  业务域
     * @param feature 功能标识
     * @return 是否删除成功
     */
    public Boolean delete(RedisDomain domain, RedisFeature feature) {
        return redisTemplate.delete(key(domain, feature));
    }

    /**
     * 判断 key 是否存在
     *
     * @param domain  业务域
     * @param feature 功能标识
     * @return true 表示存在
     */
    public Boolean hasKey(RedisDomain domain, RedisFeature feature) {
        return redisTemplate.hasKey(key(domain, feature));
    }

    // ==================== 带后缀的 key ====================

    /**
     * 设置 String 值，key 带自定义后缀
     *
     * @param domain  业务域
     * @param feature 功能标识
     * @param suffix  自定义后缀
     * @param value   值
     * @param timeout 过期时长
     * @param unit    时间单位
     */
    public void setWithSuffix(RedisDomain domain, RedisFeature feature, String suffix, String value, long timeout, TimeUnit unit) {
        redisTemplate.opsForValue().set(key(domain, feature) + ":" + suffix, value, timeout, unit);
    }

    /**
     * 原子自增并保证 key 带过期时间，供固定窗口限流计数使用
     *
     * @param domain  业务域
     * @param feature 功能标识
     * @param suffix  自定义后缀
     * @param timeout 窗口时长（仅在首次自增时设置过期）
     * @param unit    时间单位
     * @return 自增后的计数值
     */
    public Long incrementWithSuffix(RedisDomain domain, RedisFeature feature, String suffix, long timeout, TimeUnit unit) {
        String fullKey = key(domain, feature) + ":" + suffix;
        Long count = redisTemplate.opsForValue().increment(fullKey);
        // 只有窗口内的第一次请求负责设置过期，避免每次自增都重置窗口
        if (count != null && count == 1L) {
            redisTemplate.expire(fullKey, timeout, unit);
        }
        return count;
    }

    /**
     * 获取 String 值，key 带自定义后缀
     *
     * @param domain  业务域
     * @param feature 功能标识
     * @param suffix  自定义后缀
     * @return 值，key 不存在时返回 null
     */
    public String getWithSuffix(RedisDomain domain, RedisFeature feature, String suffix) {
        return redisTemplate.opsForValue().get(key(domain, feature) + ":" + suffix);
    }

    /**
     * 判断 key 是否存在，key 带自定义后缀
     *
     * @param domain  业务域
     * @param feature 功能标识
     * @param suffix  自定义后缀
     * @return true 表示存在
     */
    public Boolean hasKeyWithSuffix(RedisDomain domain, RedisFeature feature, String suffix) {
        return redisTemplate.hasKey(key(domain, feature) + ":" + suffix);
    }

    /**
     * 删除 key，key 带自定义后缀
     *
     * @param domain  业务域
     * @param feature 功能标识
     * @param suffix  自定义后缀
     * @return 是否删除成功
     */
    public Boolean deleteWithSuffix(RedisDomain domain, RedisFeature feature, String suffix) {
        return redisTemplate.delete(key(domain, feature) + ":" + suffix);
    }

    /**
     * 设置过期时间
     *
     * @param domain  业务域
     * @param feature 功能标识
     * @param timeout 过期时长
     * @param unit    时间单位
     * @return 是否设置成功
     */
    public Boolean expire(RedisDomain domain, RedisFeature feature, long timeout, TimeUnit unit) {
        return redisTemplate.expire(key(domain, feature), timeout, unit);
    }

    /**
     * 获取过期时间
     *
     * @param domain  业务域
     * @param feature 功能标识
     * @param unit    时间单位
     * @return 剩余过期时长，key 不存在返回 -2，永久有效返回 -1
     */
    public Long getExpire(RedisDomain domain, RedisFeature feature, TimeUnit unit) {
        return redisTemplate.getExpire(key(domain, feature), unit);
    }

    // ==================== Hash ====================

    /**
     * Hash 设置字段值
     *
     * @param domain  业务域
     * @param feature 功能标识
     * @param field   字段名
     * @param value   字段值
     */
    public void hSet(RedisDomain domain, RedisFeature feature, String field, String value) {
        redisTemplate.opsForHash().put(key(domain, feature), field, value);
    }

    /**
     * Hash 获取字段值
     *
     * @param domain  业务域
     * @param feature 功能标识
     * @param field   字段名
     * @return 字段值，不存在返回 null
     */
    public String hGet(RedisDomain domain, RedisFeature feature, String field) {
        Object value = redisTemplate.opsForHash().get(key(domain, feature), field);
        return value == null ? null : value.toString();
    }

    /**
     * Hash 获取全部字段
     *
     * @param domain  业务域
     * @param feature 功能标识
     * @return 字段名和值的映射
     */
    public Map<Object, Object> hGetAll(RedisDomain domain, RedisFeature feature) {
        return redisTemplate.opsForHash().entries(key(domain, feature));
    }

    /**
     * Hash 删除字段
     *
     * @param domain  业务域
     * @param feature 功能标识
     * @param fields  字段名数组
     */
    public void hDelete(RedisDomain domain, RedisFeature feature, Object... fields) {
        redisTemplate.opsForHash().delete(key(domain, feature), fields);
    }

    // ==================== List ====================

    /**
     * List 左推入
     *
     * @param domain  业务域
     * @param feature 功能标识
     * @param value   值
     * @return 列表长度
     */
    public Long lPush(RedisDomain domain, RedisFeature feature, String value) {
        return redisTemplate.opsForList().leftPush(key(domain, feature), value);
    }

    /**
     * List 左弹出
     *
     * @param domain  业务域
     * @param feature 功能标识
     * @return 弹出的值，列表为空返回 null
     */
    public String lPop(RedisDomain domain, RedisFeature feature) {
        return redisTemplate.opsForList().leftPop(key(domain, feature));
    }

    /**
     * List 范围查询
     *
     * @param domain  业务域
     * @param feature 功能标识
     * @param start   起始索引，从 0 开始
     * @param end     结束索引，-1 表示到最后
     * @return 值列表
     */
    public List<String> lRange(RedisDomain domain, RedisFeature feature, long start, long end) {
        return redisTemplate.opsForList().range(key(domain, feature), start, end);
    }

    // ==================== Set ====================

    /**
     * Set 添加成员
     *
     * @param domain  业务域
     * @param feature 功能标识
     * @param members 成员值数组
     * @return 新增成员数量
     */
    public Long sAdd(RedisDomain domain, RedisFeature feature, String... members) {
        return redisTemplate.opsForSet().add(key(domain, feature), members);
    }

    /**
     * Set 判断成员是否存在
     *
     * @param domain  业务域
     * @param feature 功能标识
     * @param member  成员值
     * @return true 表示存在
     */
    public Boolean sIsMember(RedisDomain domain, RedisFeature feature, String member) {
        return redisTemplate.opsForSet().isMember(key(domain, feature), member);
    }

    /**
     * Set 获取所有成员
     *
     * @param domain  业务域
     * @param feature 功能标识
     * @return 成员集合
     */
    public Set<String> sMembers(RedisDomain domain, RedisFeature feature) {
        return redisTemplate.opsForSet().members(key(domain, feature));
    }
}
