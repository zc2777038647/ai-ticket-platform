package com.xiaoyang.aiticketplatform.ratelimit;

import com.xiaoyang.aiticketplatform.config.LoginRateLimitProperties;
import com.xiaoyang.aiticketplatform.exception.RateLimitExceededException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class RedisLoginRateLimiter {

    private static final String SCRIPT_PATH = "redis/fixed_window_rate_limit.lua";

    private final StringRedisTemplate stringRedisTemplate;
    private final LoginRateLimitProperties properties;
    private final LoginRateLimitKeyGenerator keyGenerator;
    private final DefaultRedisScript<Long> script;

    public RedisLoginRateLimiter(
            StringRedisTemplate stringRedisTemplate,
            LoginRateLimitProperties properties,
            LoginRateLimitKeyGenerator keyGenerator
    ) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.properties = properties;
        this.keyGenerator = keyGenerator;
        this.script = loadScript();
    }

    public void checkLoginAllowed(String remoteAddress, String username) {
        if (!properties.isEnabled()) {
            return;
        }

        checkDimension(keyGenerator.ipKey(remoteAddress), properties.getIp());
        checkDimension(keyGenerator.usernameKey(username), properties.getUsername());
    }

    private void checkDimension(String key, LoginRateLimitProperties.Limit limit) {
        Long result = stringRedisTemplate.execute(
                script,
                List.of(key),
                Long.toString(limit.getMaxRequests()),
                Long.toString(limit.getWindowSeconds())
        );
        if (result == null) {
            throw new IllegalStateException("Redis限流脚本未返回结果");
        }
        if (result < 0) {
            throw new IllegalStateException("Redis限流脚本返回了无效结果");
        }
        if (result > 0) {
            throw new RateLimitExceededException(result);
        }
    }

    private DefaultRedisScript<Long> loadScript() {
        ClassPathResource resource = new ClassPathResource(SCRIPT_PATH);
        try {
            String scriptText = resource.getContentAsString(StandardCharsets.UTF_8);
            return new DefaultRedisScript<>(scriptText, Long.class);
        } catch (IOException exception) {
            throw new IllegalStateException("无法加载Redis限流脚本", exception);
        }
    }
}
