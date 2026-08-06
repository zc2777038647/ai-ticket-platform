package com.xiaoyang.aiticketplatform.idempotency;

import com.xiaoyang.aiticketplatform.config.CreateTicketIdempotencyProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisCreateTicketIdempotencyStoreTest {

    private static final String REDIS_KEY = "test:key";
    private static final String FINGERPRINT = "a".repeat(64);
    private static final String OWNER_TOKEN = "owner-token";

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private CreateTicketIdempotencyKeyGenerator keyGenerator;

    private CreateTicketIdempotencyProperties properties;
    private RedisCreateTicketIdempotencyStore store;

    @BeforeEach
    void setUp() {
        properties = new CreateTicketIdempotencyProperties();
        store = new RedisCreateTicketIdempotencyStore(
                stringRedisTemplate,
                objectMapper,
                properties,
                keyGenerator
        );
    }

    @Test
    void shouldReturnDisabledWithoutGeneratingKeyOrAccessingRedis() {
        properties.setEnabled(false);

        IdempotencyAcquireResult result = store.acquire(null, null, null, null);

        assertEquals(IdempotencyAcquireStatus.DISABLED, result.status());
        verifyNoInteractions(keyGenerator, stringRedisTemplate, objectMapper);
    }

    @Test
    void shouldSkipCompleteAndReleaseWhenDisabled() {
        properties.setEnabled(false);

        store.markSucceeded(null, null, null, null, null);

        assertFalse(store.releaseProcessing(null, null, null, null));
        verifyNoInteractions(keyGenerator, stringRedisTemplate, objectMapper);
    }

    @Test
    void shouldRejectNullAcquireScriptResult() {
        prepareRedisKey();
        when(executeRedis()).thenReturn(null);

        assertThrows(IllegalStateException.class,
                () -> store.acquire(100L, "request-123", FINGERPRINT, OWNER_TOKEN));
    }

    @Test
    void shouldRejectUnparseableAcquireJson() throws JacksonException {
        prepareRedisKey();
        when(executeRedis()).thenReturn("not-json");
        when(objectMapper.readValue(
                "not-json",
                RedisCreateTicketIdempotencyStore.AcquireScriptResult.class
        )).thenThrow(jsonException());

        assertThrows(IllegalStateException.class,
                () -> store.acquire(100L, "request-123", FINGERPRINT, OWNER_TOKEN));
    }

    @Test
    void shouldRejectIllegalAcquireStateCombination() throws JacksonException {
        prepareRedisKey();
        when(executeRedis()).thenReturn("json");
        when(objectMapper.readValue(
                "json",
                RedisCreateTicketIdempotencyStore.AcquireScriptResult.class
        )).thenReturn(new RedisCreateTicketIdempotencyStore.AcquireScriptResult(
                "IN_PROGRESS", 0, null));

        assertThrows(IllegalStateException.class,
                () -> store.acquire(100L, "request-123", FINGERPRINT, OWNER_TOKEN));
    }

    @Test
    void shouldReturnValidAcquireResult() throws JacksonException {
        prepareRedisKey();
        when(executeRedis()).thenReturn("json");
        when(objectMapper.readValue(
                "json",
                RedisCreateTicketIdempotencyStore.AcquireScriptResult.class
        )).thenReturn(new RedisCreateTicketIdempotencyStore.AcquireScriptResult(
                "ACQUIRED", 0, null));

        assertEquals(
                IdempotencyAcquireStatus.ACQUIRED,
                store.acquire(100L, "request-123", FINGERPRINT, OWNER_TOKEN).status()
        );
    }

    @Test
    void shouldRejectCompleteResultOtherThanOne() {
        prepareRedisKey();
        when(executeRedis()).thenReturn(0L);

        assertThrows(IllegalStateException.class, () -> store.markSucceeded(
                100L, "request-123", FINGERPRINT, OWNER_TOKEN, "{\"id\":100}"));
    }

    @Test
    void shouldReturnTrueWhenReleaseDeletesProcessingRecord() {
        prepareRedisKey();
        when(executeRedis()).thenReturn(1L);

        assertTrue(store.releaseProcessing(100L, "request-123", FINGERPRINT, OWNER_TOKEN));
    }

    @Test
    void shouldReturnFalseWhenReleaseCannotDeleteRecord() {
        prepareRedisKey();
        when(executeRedis()).thenReturn(-3L);

        assertFalse(store.releaseProcessing(100L, "request-123", FINGERPRINT, OWNER_TOKEN));
    }

    @Test
    void shouldPropagateRedisFailure() {
        prepareRedisKey();
        DataAccessResourceFailureException failure =
                new DataAccessResourceFailureException("redis unavailable");
        when(executeRedis()).thenThrow(failure);

        assertSame(failure, assertThrows(
                DataAccessResourceFailureException.class,
                () -> store.acquire(100L, "request-123", FINGERPRINT, OWNER_TOKEN)
        ));
    }

    @Test
    void shouldRejectInvalidInputBeforeGeneratingKeyOrAccessingRedis() {
        assertThrows(IllegalArgumentException.class,
                () -> store.acquire(100L, "request-123", "invalid", OWNER_TOKEN));

        verifyNoInteractions(keyGenerator, stringRedisTemplate, objectMapper);
    }

    private void prepareRedisKey() {
        when(keyGenerator.generateRedisKey(100L, "request-123")).thenReturn(REDIS_KEY);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Object executeRedis() {
        return stringRedisTemplate.execute(
                any(RedisScript.class),
                anyList(),
                any(Object[].class)
        );
    }

    private static JacksonException jsonException() {
        try {
            new ObjectMapper().readTree("{");
            throw new AssertionError("Expected invalid JSON");
        } catch (JacksonException exception) {
            return exception;
        }
    }
}
