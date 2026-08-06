package com.xiaoyang.aiticketplatform.ratelimit;

import com.xiaoyang.aiticketplatform.config.LoginRateLimitProperties;
import com.xiaoyang.aiticketplatform.exception.RateLimitExceededException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "app.rate-limit.login.enabled=true",
        "app.rate-limit.login.key-prefix=ai-ticket:test:rate-limit:login:service",
        "app.rate-limit.login.ip.max-requests=3",
        "app.rate-limit.login.ip.window-seconds=30",
        "app.rate-limit.login.username.max-requests=2",
        "app.rate-limit.login.username.window-seconds=30"
})
class RedisLoginRateLimiterIntegrationTest {

    private static final long WINDOW_SECONDS = 30;

    @Autowired
    private RedisLoginRateLimiter loginRateLimiter;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private LoginRateLimitKeyGenerator keyGenerator;

    @Autowired
    private LoginRateLimitProperties properties;

    private final Set<String> testKeys = new LinkedHashSet<>();

    @AfterEach
    void deleteTestKeys() {
        for (String key : testKeys) {
            stringRedisTemplate.delete(key);
            assertFalse(Boolean.TRUE.equals(stringRedisTemplate.hasKey(key)),
                    () -> "限流测试Key清理失败: " + key);
        }
        testKeys.clear();
    }

    @Test
    void shouldRejectFourthRequestFromSameIp() {
        String suffix = suffix();
        String remoteAddress = "198.51.100." + uniqueOctet();

        for (int attempt = 1; attempt <= 3; attempt++) {
            String username = "ip_user_" + attempt + "_" + suffix;
            track(remoteAddress, username);
            assertDoesNotThrow(() -> loginRateLimiter.checkLoginAllowed(remoteAddress, username));
        }

        String rejectedUsername = "ip_user_4_" + suffix;
        track(remoteAddress, rejectedUsername);
        RateLimitExceededException exception = assertThrows(
                RateLimitExceededException.class,
                () -> loginRateLimiter.checkLoginAllowed(remoteAddress, rejectedUsername)
        );

        assertRetryAfterWithinWindow(exception);
        assertValidTtl(keyGenerator.ipKey(remoteAddress));
    }

    @Test
    void shouldLimitNormalizedUsernameAcrossDifferentIps() {
        String username = "shared_user_" + suffix();
        String firstIp = "192.0.2." + uniqueOctet();
        String secondIp = "198.51.100." + uniqueOctet();
        String thirdIp = "203.0.113." + uniqueOctet();

        track(firstIp, username);
        track(secondIp, username);
        track(thirdIp, username);
        assertDoesNotThrow(() -> loginRateLimiter.checkLoginAllowed(firstIp, username));
        assertDoesNotThrow(() -> loginRateLimiter.checkLoginAllowed(secondIp, username));

        RateLimitExceededException exception = assertThrows(
                RateLimitExceededException.class,
                () -> loginRateLimiter.checkLoginAllowed(thirdIp, username)
        );

        assertRetryAfterWithinWindow(exception);
        assertValidTtl(keyGenerator.usernameKey(username));
    }

    @Test
    void shouldUseOneBucketForNormalizedUsernameVariants() {
        String suffix = suffix();
        String canonicalUsername = "test_user_" + suffix;
        String firstIp = "192.0.2." + uniqueOctet();
        String secondIp = "198.51.100." + uniqueOctet();
        String thirdIp = "203.0.113." + uniqueOctet();
        String firstVariant = "Test_User_" + suffix;
        String secondVariant = "  test_user_" + suffix + "  ";
        String thirdVariant = canonicalUsername.toUpperCase(Locale.ROOT);

        track(firstIp, firstVariant);
        track(secondIp, secondVariant);
        track(thirdIp, thirdVariant);
        assertDoesNotThrow(() -> loginRateLimiter.checkLoginAllowed(firstIp, firstVariant));
        assertDoesNotThrow(() -> loginRateLimiter.checkLoginAllowed(secondIp, secondVariant));

        RateLimitExceededException exception = assertThrows(
                RateLimitExceededException.class,
                () -> loginRateLimiter.checkLoginAllowed(thirdIp, thirdVariant)
        );

        assertRetryAfterWithinWindow(exception);
        assertTrue(Boolean.TRUE.equals(stringRedisTemplate.hasKey(
                keyGenerator.usernameKey(canonicalUsername)
        )));
    }

    @Test
    void shouldNotResetFixedWindowTtlForAllowedRequests() throws InterruptedException {
        String suffix = suffix();
        String remoteAddress = "198.51.100." + uniqueOctet();
        String username = "ttl_user_" + suffix;
        track(remoteAddress, username);

        loginRateLimiter.checkLoginAllowed(remoteAddress, username);
        String ipKey = keyGenerator.ipKey(remoteAddress);
        Long firstTtlMillis = stringRedisTemplate.getExpire(ipKey, TimeUnit.MILLISECONDS);
        assertNotNull(firstTtlMillis);
        assertTrue(firstTtlMillis > 0);
        assertTrue(firstTtlMillis <= TimeUnit.SECONDS.toMillis(WINDOW_SECONDS));

        Thread.sleep(100);
        loginRateLimiter.checkLoginAllowed(remoteAddress, username);
        Long secondTtlMillis = stringRedisTemplate.getExpire(ipKey, TimeUnit.MILLISECONDS);
        assertNotNull(secondTtlMillis);
        assertTrue(secondTtlMillis > 0);
        assertTrue(secondTtlMillis < firstTtlMillis,
                "固定窗口内的后续请求不得重置正常TTL");
    }

    @Test
    void shouldReturnWithoutCreatingKeysWhenDisabled() {
        String remoteAddress = "203.0.113." + uniqueOctet();
        String username = "disabled_user_" + suffix();
        track(remoteAddress, username);

        properties.setEnabled(false);
        try {
            assertDoesNotThrow(() -> loginRateLimiter.checkLoginAllowed(remoteAddress, username));
        } finally {
            properties.setEnabled(true);
        }

        assertFalse(Boolean.TRUE.equals(stringRedisTemplate.hasKey(keyGenerator.ipKey(remoteAddress))));
        assertFalse(Boolean.TRUE.equals(stringRedisTemplate.hasKey(keyGenerator.usernameKey(username))));
    }

    private void track(String remoteAddress, String username) {
        testKeys.add(keyGenerator.ipKey(remoteAddress));
        testKeys.add(keyGenerator.usernameKey(username));
    }

    private void assertValidTtl(String key) {
        Long ttl = stringRedisTemplate.getExpire(key, TimeUnit.SECONDS);
        assertNotNull(ttl);
        assertTrue(ttl > 0);
        assertTrue(ttl <= WINDOW_SECONDS);
    }

    private void assertRetryAfterWithinWindow(RateLimitExceededException exception) {
        assertTrue(exception.getRetryAfterSeconds() > 0);
        assertTrue(exception.getRetryAfterSeconds() <= WINDOW_SECONDS);
    }

    private static String suffix() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static int uniqueOctet() {
        return Math.floorMod(UUID.randomUUID().hashCode(), 254) + 1;
    }
}
