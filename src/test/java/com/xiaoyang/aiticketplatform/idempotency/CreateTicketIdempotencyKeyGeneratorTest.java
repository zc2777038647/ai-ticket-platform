package com.xiaoyang.aiticketplatform.idempotency;

import com.xiaoyang.aiticketplatform.config.CreateTicketIdempotencyProperties;
import com.xiaoyang.aiticketplatform.exception.InvalidIdempotencyKeyException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreateTicketIdempotencyKeyGeneratorTest {

    private static final String PREFIX = "ai-ticket:test:idempotency:create-ticket";

    private CreateTicketIdempotencyKeyGenerator generator;

    @BeforeEach
    void setUp() {
        CreateTicketIdempotencyProperties properties = new CreateTicketIdempotencyProperties();
        properties.setKeyPrefix(PREFIX);
        generator = new CreateTicketIdempotencyKeyGenerator(properties);
    }

    @Test
    void shouldGenerateSameRedisKeyForSameScope() {
        assertEquals(
                generator.generateRedisKey(100L, "request-123"),
                generator.generateRedisKey(100L, "request-123")
        );
    }

    @Test
    void shouldSeparateDifferentUsers() {
        assertNotEquals(
                generator.generateRedisKey(100L, "request-123"),
                generator.generateRedisKey(101L, "request-123")
        );
    }

    @Test
    void shouldSeparateDifferentClientKeys() {
        assertNotEquals(
                generator.generateRedisKey(100L, "request-123"),
                generator.generateRedisKey(100L, "request-456")
        );
    }

    @Test
    void shouldNormalizeLeadingAndTrailingSpaces() {
        assertEquals(
                generator.generateRedisKey(100L, "request-123"),
                generator.generateRedisKey(100L, "  request-123  ")
        );
    }

    @Test
    void shouldHideRawInputsAndUsePrefixAndSha256Hex() {
        String redisKey = generator.generateRedisKey(987654321L, "private-client-key");
        String digest = redisKey.substring(PREFIX.length() + 1);

        assertTrue(redisKey.startsWith(PREFIX + ":"));
        assertFalse(redisKey.contains("private-client-key"));
        assertFalse(redisKey.contains("987654321"));
        assertTrue(digest.matches("[0-9a-f]{64}"));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0L, -1L})
    void shouldRejectInvalidCreatorUserId(Long creatorUserId) {
        assertThrows(
                IllegalArgumentException.class,
                () -> generator.generateRedisKey(creatorUserId, "request-123")
        );
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "short", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"})
    void shouldRejectInvalidClientKey(String idempotencyKey) {
        assertThrows(
                InvalidIdempotencyKeyException.class,
                () -> generator.generateRedisKey(100L, idempotencyKey)
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"request\n123", "request\t123", "request\u0000123"})
    void shouldRejectControlCharacters(String idempotencyKey) {
        assertThrows(
                InvalidIdempotencyKeyException.class,
                () -> generator.generateRedisKey(100L, idempotencyKey)
        );
    }
}
