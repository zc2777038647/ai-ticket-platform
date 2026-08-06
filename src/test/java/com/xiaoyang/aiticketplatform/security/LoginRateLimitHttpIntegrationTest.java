package com.xiaoyang.aiticketplatform.security;

import com.xiaoyang.aiticketplatform.ratelimit.LoginRateLimitKeyGenerator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.rate-limit.login.enabled=true",
        "app.rate-limit.login.key-prefix=ai-ticket:test:rate-limit:login:http",
        "app.rate-limit.login.ip.max-requests=3",
        "app.rate-limit.login.ip.window-seconds=30",
        "app.rate-limit.login.username.max-requests=2",
        "app.rate-limit.login.username.window-seconds=30"
})
@AutoConfigureMockMvc
@Transactional
class LoginRateLimitHttpIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private LoginRateLimitKeyGenerator keyGenerator;

    private final Set<String> testKeys = new LinkedHashSet<>();

    @AfterEach
    void deleteTestKeys() {
        for (String key : testKeys) {
            stringRedisTemplate.delete(key);
            assertFalse(Boolean.TRUE.equals(stringRedisTemplate.hasKey(key)),
                    () -> "HTTP限流测试Key清理失败: " + key);
        }
        testKeys.clear();
    }

    @Test
    void shouldLimitUsernameWithoutRevealingWhetherAccountExists() throws Exception {
        String username = "missing_user_" + suffix();
        String firstIp = "192.0.2." + uniqueOctet();
        String secondIp = "198.51.100." + uniqueOctet();
        String thirdIp = "203.0.113." + uniqueOctet();

        performLogin(firstIp, username, "wrong-password")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
        performLogin(secondIp, username, "wrong-password")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));

        assertRateLimited(performLogin(thirdIp, username, "wrong-password"), username, thirdIp);
    }

    @Test
    void shouldLimitSameRemoteAddressAcrossDifferentUsernames() throws Exception {
        String remoteAddress = "198.51.100." + uniqueOctet();
        String suffix = suffix();

        for (int attempt = 1; attempt <= 3; attempt++) {
            performLogin(remoteAddress, "ip_missing_" + attempt + "_" + suffix, "wrong-password")
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value(40100));
        }

        String rejectedUsername = "ip_missing_4_" + suffix;
        assertRateLimited(
                performLogin(remoteAddress, rejectedUsername, "wrong-password"),
                rejectedUsername,
                remoteAddress
        );
    }

    @Test
    void shouldCountSuccessfulLoginsAndEventuallyReject() throws Exception {
        String suffix = suffix();
        String username = "rate_success_" + suffix;
        String password = "P_" + suffix;
        String remoteAddress = "203.0.113." + uniqueOctet();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(username, password)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(0));

        performLogin(remoteAddress, username, password)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        performLogin(remoteAddress, username, password)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        assertRateLimited(performLogin(remoteAddress, username, password), username, remoteAddress);
    }

    @Test
    void shouldIgnoreSpoofedXForwardedForHeaders() throws Exception {
        String remoteAddress = "192.0.2." + uniqueOctet();
        String suffix = suffix();

        for (int attempt = 1; attempt <= 3; attempt++) {
            performLogin(
                    remoteAddress,
                    "forwarded_user_" + attempt + "_" + suffix,
                    "wrong-password",
                    "203.0.113." + attempt
            )
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value(40100));
        }

        String rejectedUsername = "forwarded_user_4_" + suffix;
        assertRateLimited(
                performLogin(
                        remoteAddress,
                        rejectedUsername,
                        "wrong-password",
                        "203.0.113.200"
                ),
                rejectedUsername,
                remoteAddress
        );
    }

    @Test
    void shouldNotCountValidationFailure() throws Exception {
        String remoteAddress = "203.0.113." + uniqueOctet();
        String suffix = suffix();
        String invalidUsername = "invalid_request_" + suffix;

        performLogin(remoteAddress, invalidUsername, "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
        assertFalse(Boolean.TRUE.equals(stringRedisTemplate.hasKey(keyGenerator.ipKey(remoteAddress))));
        assertFalse(Boolean.TRUE.equals(stringRedisTemplate.hasKey(
                keyGenerator.usernameKey(invalidUsername)
        )));

        for (int attempt = 1; attempt <= 3; attempt++) {
            performLogin(remoteAddress, "valid_request_" + attempt + "_" + suffix, "wrong-password")
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value(40100));
        }

        String rejectedUsername = "valid_request_4_" + suffix;
        assertRateLimited(
                performLogin(remoteAddress, rejectedUsername, "wrong-password"),
                rejectedUsername,
                remoteAddress
        );
    }

    private ResultActions performLogin(
            String remoteAddress,
            String username,
            String password
    ) throws Exception {
        return performLogin(remoteAddress, username, password, null);
    }

    private ResultActions performLogin(
            String remoteAddress,
            String username,
            String password,
            String forwardedFor
    ) throws Exception {
        track(remoteAddress, username);
        var requestBuilder = post("/api/auth/login")
                .with(request -> {
                    request.setRemoteAddr(remoteAddress);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginJson(username, password));
        if (forwardedFor != null) {
            requestBuilder.header("X-Forwarded-For", forwardedFor);
        }
        return mockMvc.perform(requestBuilder);
    }

    private void assertRateLimited(
            ResultActions resultActions,
            String username,
            String remoteAddress
    ) throws Exception {
        resultActions
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(
                        HttpHeaders.RETRY_AFTER,
                        matchesPattern("[1-9][0-9]*")
                ))
                .andExpect(jsonPath("$.code").value(42900))
                .andExpect(jsonPath("$.message").value("请求过于频繁，请稍后重试"))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(content().string(not(containsString(username))))
                .andExpect(content().string(not(containsString(remoteAddress))))
                .andExpect(content().string(not(containsString("currentCount"))))
                .andExpect(content().string(not(containsString("Redis"))))
                .andExpect(content().string(not(containsString("Lua"))))
                .andExpect(content().string(not(containsString("stackTrace"))));
    }

    private void track(String remoteAddress, String username) {
        testKeys.add(keyGenerator.ipKey(remoteAddress));
        testKeys.add(keyGenerator.usernameKey(username));
    }

    private static String registerJson(String username, String password) {
        return """
                {
                  "username": "%s",
                  "password": "%s",
                  "displayName": "登录限流测试用户"
                }
                """.formatted(username, password);
    }

    private static String loginJson(String username, String password) {
        return """
                {
                  "username": "%s",
                  "password": "%s"
                }
                """.formatted(username, password);
    }

    private static String suffix() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static int uniqueOctet() {
        return Math.floorMod(UUID.randomUUID().hashCode(), 254) + 1;
    }
}
