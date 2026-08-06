package com.xiaoyang.aiticketplatform.ratelimit;

import com.xiaoyang.aiticketplatform.config.LoginRateLimitProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginRateLimitKeyGeneratorTest {

    private static final String KEY_PREFIX = "ai-ticket:test:rate-limit:login:key-generator";

    private LoginRateLimitKeyGenerator keyGenerator;

    @BeforeEach
    void setUp() {
        LoginRateLimitProperties properties = new LoginRateLimitProperties();
        properties.setKeyPrefix(KEY_PREFIX);
        keyGenerator = new LoginRateLimitKeyGenerator(properties);
    }

    @Test
    void shouldNormalizeUsernameCaseAndWhitespace() {
        assertEquals(
                keyGenerator.usernameKey("Test_User"),
                keyGenerator.usernameKey("  test_user  ")
        );
        assertEquals(
                keyGenerator.usernameKey("Test_User"),
                keyGenerator.usernameKey("TEST_USER")
        );
    }

    @Test
    void shouldGenerateDifferentKeysForDifferentUsernames() {
        assertNotEquals(
                keyGenerator.usernameKey("first_user"),
                keyGenerator.usernameKey("second_user")
        );
    }

    @Test
    void shouldUseDifferentNamespacesForIpAndUsername() {
        assertNotEquals(
                keyGenerator.ipKey("203.0.113.10"),
                keyGenerator.usernameKey("203.0.113.10")
        );
        assertTrue(keyGenerator.ipKey("203.0.113.10").startsWith(KEY_PREFIX + ":ip:"));
        assertTrue(keyGenerator.usernameKey("test_user").startsWith(KEY_PREFIX + ":username:"));
    }

    @Test
    void shouldNotIncludeRawUsername() {
        assertFalse(keyGenerator.usernameKey("Sensitive_User").contains("sensitive_user"));
    }

    @Test
    void shouldNotIncludeRawIpAddress() {
        assertFalse(keyGenerator.ipKey("203.0.113.10").contains("203.0.113.10"));
    }

    @Test
    void shouldUseSameUnknownBucketForNullAndBlankIp() {
        assertEquals(keyGenerator.ipKey(null), keyGenerator.ipKey("   "));
    }

    @Test
    void shouldUseConfiguredPrefix() {
        assertTrue(keyGenerator.ipKey("203.0.113.10").startsWith(KEY_PREFIX + ":ip:"));
    }

    @Test
    void shouldAppendSixtyFourCharacterSha256HexDigest() {
        String key = keyGenerator.usernameKey("test_user");
        String digest = key.substring((KEY_PREFIX + ":username:").length());

        assertEquals(64, digest.length());
        assertTrue(digest.matches("[0-9a-f]{64}"));
    }
}
