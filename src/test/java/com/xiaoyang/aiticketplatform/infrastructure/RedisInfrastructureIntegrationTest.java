package com.xiaoyang.aiticketplatform.infrastructure;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class RedisInfrastructureIntegrationTest {

    private static final String KEY_PREFIX = "ai-ticket:test:redis:infrastructure:";
    private static final Duration TEST_TTL = Duration.ofSeconds(30);

    @Autowired
    private RedisConnectionFactory redisConnectionFactory;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private final Set<String> createdKeys = new LinkedHashSet<>();

    @AfterEach
    void deleteCreatedKeys() {
        for (String key : createdKeys) {
            stringRedisTemplate.delete(key);
            assertFalse(Boolean.TRUE.equals(stringRedisTemplate.hasKey(key)),
                    () -> "Redis 测试 Key 清理失败: " + key);
        }
        createdKeys.clear();
    }

    @Test
    void shouldPingRealRedis() {
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            assertEquals("PONG", connection.ping());
        }
    }

    @Test
    void shouldWriteAndReadString() {
        String key = testKey("string");
        String value = "redis-integration-value";

        stringRedisTemplate.opsForValue().set(key, value);

        assertEquals(value, stringRedisTemplate.opsForValue().get(key));
    }

    @Test
    void shouldWriteStringWithTtl() {
        String key = testKey("ttl");

        stringRedisTemplate.opsForValue().set(key, "ttl-value", TEST_TTL);

        assertEquals("ttl-value", stringRedisTemplate.opsForValue().get(key));
        Long ttlSeconds = stringRedisTemplate.getExpire(key, TimeUnit.SECONDS);
        assertNotNull(ttlSeconds);
        assertTrue(ttlSeconds > 0);
        assertTrue(ttlSeconds <= TEST_TTL.toSeconds());
    }

    @Test
    void shouldIncrementAtomically() {
        String key = testKey("counter");

        Long firstIncrement = stringRedisTemplate.opsForValue().increment(key);
        Long secondIncrement = stringRedisTemplate.opsForValue().increment(key);

        assertEquals(1L, firstIncrement);
        assertEquals(2L, secondIncrement);
    }

    @Test
    void shouldSetValueOnlyWhenAbsentWithTtl() {
        String key = testKey("setnx");

        Boolean firstWrite = stringRedisTemplate.opsForValue()
                .setIfAbsent(key, "first-value", TEST_TTL);
        Boolean secondWrite = stringRedisTemplate.opsForValue()
                .setIfAbsent(key, "second-value", TEST_TTL);

        assertEquals(Boolean.TRUE, firstWrite);
        assertEquals(Boolean.FALSE, secondWrite);
        assertEquals("first-value", stringRedisTemplate.opsForValue().get(key));
        Long ttlSeconds = stringRedisTemplate.getExpire(key, TimeUnit.SECONDS);
        assertNotNull(ttlSeconds);
        assertTrue(ttlSeconds > 0);
        assertTrue(ttlSeconds <= TEST_TTL.toSeconds());
    }

    private String testKey(String purpose) {
        String key = KEY_PREFIX + UUID.randomUUID() + ":" + purpose;
        createdKeys.add(key);
        return key;
    }
}
