package com.xiaoyang.aiticketplatform.idempotency;

import com.xiaoyang.aiticketplatform.dto.request.CreateTicketRequest;
import com.xiaoyang.aiticketplatform.enums.TicketPriority;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "app.idempotency.create-ticket.enabled=true",
        "app.idempotency.create-ticket.key-prefix=ai-ticket:test:idempotency:create-ticket:store",
        "app.idempotency.create-ticket.processing-ttl-seconds=30",
        "app.idempotency.create-ticket.success-ttl-seconds=300",
        "app.idempotency.create-ticket.minimum-key-length=8",
        "app.idempotency.create-ticket.maximum-key-length=128"
})
class RedisCreateTicketIdempotencyStoreIntegrationTest {

    private static final long PROCESSING_TTL_SECONDS = 30;
    private static final long SUCCESS_TTL_SECONDS = 300;
    private static final String RESPONSE_PAYLOAD =
            "{\"code\":0,\"message\":\"成功\",\"data\":{\"id\":100}}";

    @Autowired
    private RedisCreateTicketIdempotencyStore store;

    @Autowired
    private CreateTicketIdempotencyKeyGenerator keyGenerator;

    @Autowired
    private CreateTicketRequestFingerprint requestFingerprint;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private final Set<String> testKeys = new LinkedHashSet<>();

    @AfterEach
    void deleteExactTestKeys() {
        for (String key : testKeys) {
            stringRedisTemplate.delete(key);
            assertFalse(Boolean.TRUE.equals(stringRedisTemplate.hasKey(key)),
                    () -> "幂等状态机测试 Key 清理失败: " + key);
        }
        testKeys.clear();
    }

    @Test
    void shouldAcquireMissingKeyAsProcessing() {
        TestContext context = context();

        IdempotencyAcquireResult result = acquire(context);

        assertEquals(IdempotencyAcquireStatus.ACQUIRED, result.status());
        assertEquals("PROCESSING", hash(context, "state"));
        assertEquals(context.fingerprint(), hash(context, "fingerprint"));
        assertEquals(context.ownerToken(), hash(context, "ownerToken"));
        assertTtlWithin(context, PROCESSING_TTL_SECONDS);
    }

    @Test
    void shouldReportInProgressWithoutReplacingOwnerOrRefreshingTtl()
            throws InterruptedException {
        TestContext context = context();
        acquire(context);
        long firstTtl = ttlMillis(context);

        Thread.sleep(120);
        IdempotencyAcquireResult duplicate = store.acquire(
                context.creatorUserId(),
                context.idempotencyKey(),
                context.fingerprint(),
                UUID.randomUUID().toString()
        );
        long secondTtl = ttlMillis(context);

        assertEquals(IdempotencyAcquireStatus.IN_PROGRESS, duplicate.status());
        assertTrue(duplicate.retryAfterSeconds() > 0);
        assertEquals(context.ownerToken(), hash(context, "ownerToken"));
        assertTrue(secondTtl < firstTtl, "重复 acquire 不得刷新 PROCESSING TTL");
    }

    @Test
    void shouldRejectDifferentPayloadWithoutChangingProcessingRecord()
            throws InterruptedException {
        TestContext context = context();
        acquire(context);
        Map<Object, Object> before = entries(context);
        long firstTtl = ttlMillis(context);

        Thread.sleep(120);
        IdempotencyAcquireResult result = store.acquire(
                context.creatorUserId(),
                context.idempotencyKey(),
                differentFingerprint(),
                UUID.randomUUID().toString()
        );

        assertEquals(IdempotencyAcquireStatus.PAYLOAD_MISMATCH, result.status());
        assertEquals(before, entries(context));
        assertTrue(ttlMillis(context) < firstTtl, "payload 不一致不得刷新 TTL");
    }

    @Test
    void shouldCompleteProcessingRecordWithCorrectOwner() {
        TestContext context = context();
        acquire(context);

        complete(context);

        assertEquals("SUCCEEDED", hash(context, "state"));
        assertEquals(context.fingerprint(), hash(context, "fingerprint"));
        assertEquals(RESPONSE_PAYLOAD, hash(context, "response"));
        assertNull(hash(context, "ownerToken"));
        assertTtlWithin(context, SUCCESS_TTL_SECONDS);
    }

    @Test
    void shouldReplaySucceededPayloadWithoutRefreshingTtl() throws InterruptedException {
        TestContext context = context();
        acquire(context);
        complete(context);
        long firstTtl = ttlMillis(context);

        Thread.sleep(120);
        IdempotencyAcquireResult replay = store.acquire(
                context.creatorUserId(),
                context.idempotencyKey(),
                context.fingerprint(),
                UUID.randomUUID().toString()
        );

        assertEquals(IdempotencyAcquireStatus.SUCCEEDED, replay.status());
        assertEquals(RESPONSE_PAYLOAD, replay.responsePayload());
        assertTrue(ttlMillis(context) < firstTtl, "重复读取不得刷新 SUCCEEDED TTL");
    }

    @Test
    void shouldNotReplaySucceededPayloadForDifferentFingerprint() {
        TestContext context = context();
        acquire(context);
        complete(context);

        IdempotencyAcquireResult result = store.acquire(
                context.creatorUserId(),
                context.idempotencyKey(),
                differentFingerprint(),
                UUID.randomUUID().toString()
        );

        assertEquals(IdempotencyAcquireStatus.PAYLOAD_MISMATCH, result.status());
        assertNull(result.responsePayload());
        assertEquals(RESPONSE_PAYLOAD, hash(context, "response"));
    }

    @Test
    void shouldPreventStaleOwnerFromCompleting() {
        TestContext context = context();
        acquire(context);

        assertThrows(IllegalStateException.class, () -> store.markSucceeded(
                context.creatorUserId(),
                context.idempotencyKey(),
                context.fingerprint(),
                UUID.randomUUID().toString(),
                RESPONSE_PAYLOAD
        ));

        assertEquals("PROCESSING", hash(context, "state"));
        assertEquals(context.ownerToken(), hash(context, "ownerToken"));
        assertNull(hash(context, "response"));
    }

    @Test
    void shouldReleaseWithCorrectOwnerAndAllowNewAcquisition() {
        TestContext context = context();
        acquire(context);

        assertTrue(store.releaseProcessing(
                context.creatorUserId(), context.idempotencyKey(),
                context.fingerprint(), context.ownerToken()));
        assertFalse(Boolean.TRUE.equals(stringRedisTemplate.hasKey(context.redisKey())));

        String newOwner = UUID.randomUUID().toString();
        IdempotencyAcquireResult acquiredAgain = store.acquire(
                context.creatorUserId(), context.idempotencyKey(),
                context.fingerprint(), newOwner);
        assertEquals(IdempotencyAcquireStatus.ACQUIRED, acquiredAgain.status());
        assertEquals(newOwner, hash(context, "ownerToken"));
    }

    @Test
    void shouldNotReleaseWithWrongOwner() {
        TestContext context = context();
        acquire(context);

        assertFalse(store.releaseProcessing(
                context.creatorUserId(), context.idempotencyKey(),
                context.fingerprint(), UUID.randomUUID().toString()));

        assertTrue(Boolean.TRUE.equals(stringRedisTemplate.hasKey(context.redisKey())));
        assertEquals(context.ownerToken(), hash(context, "ownerToken"));
    }

    @Test
    void shouldNotReleaseSucceededRecord() {
        TestContext context = context();
        acquire(context);
        complete(context);

        assertFalse(store.releaseProcessing(
                context.creatorUserId(), context.idempotencyKey(),
                context.fingerprint(), context.ownerToken()));

        assertEquals("SUCCEEDED", hash(context, "state"));
        assertEquals(RESPONSE_PAYLOAD, hash(context, "response"));
    }

    private IdempotencyAcquireResult acquire(TestContext context) {
        return store.acquire(
                context.creatorUserId(),
                context.idempotencyKey(),
                context.fingerprint(),
                context.ownerToken()
        );
    }

    private void complete(TestContext context) {
        store.markSucceeded(
                context.creatorUserId(),
                context.idempotencyKey(),
                context.fingerprint(),
                context.ownerToken(),
                RESPONSE_PAYLOAD
        );
    }

    private TestContext context() {
        long creatorUserId = Math.floorMod(
                UUID.randomUUID().getMostSignificantBits(),
                1_000_000_000L
        ) + 1;
        String idempotencyKey = "request-" + UUID.randomUUID();
        String ownerToken = UUID.randomUUID().toString();
        String fingerprint = requestFingerprint.fingerprint(request());
        String redisKey = keyGenerator.generateRedisKey(creatorUserId, idempotencyKey);
        testKeys.add(redisKey);
        return new TestContext(
                creatorUserId, idempotencyKey, ownerToken, fingerprint, redisKey);
    }

    private String differentFingerprint() {
        return requestFingerprint.fingerprint(new CreateTicketRequest(
                "不同测试工单", "状态机测试描述", "幂等测试用户", TicketPriority.HIGH));
    }

    private static CreateTicketRequest request() {
        return new CreateTicketRequest(
                "状态机测试工单", "状态机测试描述", "幂等测试用户", TicketPriority.HIGH);
    }

    private Object hash(TestContext context, String field) {
        return stringRedisTemplate.opsForHash().get(context.redisKey(), field);
    }

    private Map<Object, Object> entries(TestContext context) {
        return stringRedisTemplate.opsForHash().entries(context.redisKey());
    }

    private long ttlMillis(TestContext context) {
        Long ttl = stringRedisTemplate.getExpire(context.redisKey(), TimeUnit.MILLISECONDS);
        assertNotNull(ttl);
        assertTrue(ttl > 0);
        return ttl;
    }

    private void assertTtlWithin(TestContext context, long maximumSeconds) {
        long ttl = ttlMillis(context);
        assertTrue(ttl <= TimeUnit.SECONDS.toMillis(maximumSeconds));
    }

    private record TestContext(
            Long creatorUserId,
            String idempotencyKey,
            String ownerToken,
            String fingerprint,
            String redisKey
    ) {
    }
}
