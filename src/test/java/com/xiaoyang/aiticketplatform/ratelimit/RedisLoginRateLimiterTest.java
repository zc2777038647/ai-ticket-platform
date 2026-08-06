package com.xiaoyang.aiticketplatform.ratelimit;

import com.xiaoyang.aiticketplatform.config.LoginRateLimitProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class RedisLoginRateLimiterTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private LoginRateLimitKeyGenerator keyGenerator;

    @Test
    void shouldNotAccessRedisOrGenerateKeysWhenDisabled() {
        LoginRateLimitProperties properties = new LoginRateLimitProperties();
        properties.setEnabled(false);
        RedisLoginRateLimiter rateLimiter = new RedisLoginRateLimiter(
                stringRedisTemplate,
                properties,
                keyGenerator
        );

        assertDoesNotThrow(() -> rateLimiter.checkLoginAllowed("203.0.113.10", "test_user"));

        verifyNoInteractions(stringRedisTemplate, keyGenerator);
    }
}
