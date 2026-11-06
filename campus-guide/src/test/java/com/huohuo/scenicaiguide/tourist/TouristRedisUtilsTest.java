package com.huohuo.scenicaiguide.tourist;

import com.huohuo.scenicaiguide.common.AbstractIntegrationTest;

import com.huohuo.scenicaiguide.common.redis.RedisDomain;
import com.huohuo.scenicaiguide.common.redis.RedisUtils;
import com.huohuo.scenicaiguide.tourist.enums.TouristRedisFeature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RedisUtils 完整测试 — 以 tourist 域为例
 * <p>
 * 覆盖 String、Hash、List、Set 四种数据类型常用操作
 * 每个测试独立可运行，适合用作教学示例
 */
class TouristRedisUtilsTest extends AbstractIntegrationTest {

    @Autowired
    private RedisUtils redisUtils;

    /**
     * 每个测试开始前清理 tourist 域所有 key，避免数据残留干扰
     */
    @BeforeEach
    void setUp() {
        redisUtils.delete(RedisDomain.TOURIST, TouristRedisFeature.SESSION);
        redisUtils.delete(RedisDomain.TOURIST, TouristRedisFeature.LOCATION);
        redisUtils.delete(RedisDomain.TOURIST, TouristRedisFeature.GEOFENCE);
    }

    @Test
    @DisplayName("String: set + get")
    void testStringSetAndGet() {
        // key = scenic-ai-guide:tourist:session
        redisUtils.set(RedisDomain.TOURIST, TouristRedisFeature.SESSION, "游客会话数据");

        String value = redisUtils.get(RedisDomain.TOURIST, TouristRedisFeature.SESSION);

        assertEquals("游客会话数据", value);
    }

    @Test
    @DisplayName("String: setEx 带过期时间 + hasKey + getExpire")
    void testStringSetWithExpire() throws InterruptedException {
        // 设置 2 秒过期
        redisUtils.set(RedisDomain.TOURIST, TouristRedisFeature.SESSION, "临时数据", 2, TimeUnit.SECONDS);

        // 验证 key 存在
        assertTrue(redisUtils.hasKey(RedisDomain.TOURIST, TouristRedisFeature.SESSION));

        // 验证 TTL 在 1-2 秒之间
        Long ttl = redisUtils.getExpire(RedisDomain.TOURIST, TouristRedisFeature.SESSION, TimeUnit.SECONDS);
        assertNotNull(ttl);
        assertTrue(ttl > 0 && ttl <= 2);

        // 等待 3 秒后验证已过期
        Thread.sleep(3000);
        assertFalse(redisUtils.hasKey(RedisDomain.TOURIST, TouristRedisFeature.SESSION));
    }

    @Test
    @DisplayName("Hash: hSet + hGet")
    void testHashSetAndGet() {
        // key = scenic-ai-guide:tourist:location
        redisUtils.hSet(RedisDomain.TOURIST, TouristRedisFeature.LOCATION, "longitude", "120.155");
        redisUtils.hSet(RedisDomain.TOURIST, TouristRedisFeature.LOCATION, "latitude", "30.285");

        String longitude = redisUtils.hGet(RedisDomain.TOURIST, TouristRedisFeature.LOCATION, "longitude");

        assertEquals("120.155", longitude);
    }

    @Test
    @DisplayName("Hash: hGetAll 获取全部字段")
    void testHashGetAll() {
        redisUtils.hSet(RedisDomain.TOURIST, TouristRedisFeature.LOCATION, "longitude", "120.155");
        redisUtils.hSet(RedisDomain.TOURIST, TouristRedisFeature.LOCATION, "latitude", "30.285");

        Map<Object, Object> map = redisUtils.hGetAll(RedisDomain.TOURIST, TouristRedisFeature.LOCATION);

        assertEquals(2, map.size());
        assertEquals("120.155", map.get("longitude"));
        assertEquals("30.285", map.get("latitude"));
    }

    @Test
    @DisplayName("List: lPush + lRange")
    void testListPushAndRange() {
        // key = scenic-ai-guide:tourist:geofence
        redisUtils.lPush(RedisDomain.TOURIST, TouristRedisFeature.GEOFENCE, "望月桥");
        redisUtils.lPush(RedisDomain.TOURIST, TouristRedisFeature.GEOFENCE, "听雨轩");
        redisUtils.lPush(RedisDomain.TOURIST, TouristRedisFeature.GEOFENCE, "断桥");

        // lPush 左推入，顺序为 断桥 → 听雨轩 → 望月桥
        List<String> list = redisUtils.lRange(RedisDomain.TOURIST, TouristRedisFeature.GEOFENCE, 0, -1);

        assertEquals(3, list.size());
        assertEquals("断桥", list.get(0));
        assertEquals("听雨轩", list.get(1));
        assertEquals("望月桥", list.get(2));
    }

    @Test
    @DisplayName("Set: sAdd + sIsMember + sMembers")
    void testSetAddAndIsMember() {
        redisUtils.sAdd(RedisDomain.TOURIST, TouristRedisFeature.SESSION, "user1", "user2", "user3");

        assertTrue(redisUtils.sIsMember(RedisDomain.TOURIST, TouristRedisFeature.SESSION, "user2"));
        assertFalse(redisUtils.sIsMember(RedisDomain.TOURIST, TouristRedisFeature.SESSION, "user999"));

        Set<String> members = redisUtils.sMembers(RedisDomain.TOURIST, TouristRedisFeature.SESSION);
        assertEquals(3, members.size());
    }
}
