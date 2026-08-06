package com.xiaoyang.aiticketplatform.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jayway.jsonpath.JsonPath;
import com.xiaoyang.aiticketplatform.dto.request.CreateTicketRequest;
import com.xiaoyang.aiticketplatform.entity.Ticket;
import com.xiaoyang.aiticketplatform.entity.UserAccount;
import com.xiaoyang.aiticketplatform.enums.TicketPriority;
import com.xiaoyang.aiticketplatform.enums.UserRole;
import com.xiaoyang.aiticketplatform.idempotency.CreateTicketIdempotencyKeyGenerator;
import com.xiaoyang.aiticketplatform.idempotency.CreateTicketRequestFingerprint;
import com.xiaoyang.aiticketplatform.idempotency.IdempotencyAcquireStatus;
import com.xiaoyang.aiticketplatform.idempotency.RedisCreateTicketIdempotencyStore;
import com.xiaoyang.aiticketplatform.mapper.TicketMapper;
import com.xiaoyang.aiticketplatform.mapper.UserAccountMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.idempotency.create-ticket.enabled=true",
        "app.idempotency.create-ticket.key-prefix=ai-ticket:test:idempotency:create-ticket:http",
        "app.idempotency.create-ticket.processing-ttl-seconds=30",
        "app.idempotency.create-ticket.success-ttl-seconds=300",
        "app.idempotency.create-ticket.minimum-key-length=8",
        "app.idempotency.create-ticket.maximum-key-length=128"
})
@AutoConfigureMockMvc
@Transactional
class CreateTicketIdempotencyHttpIntegrationTest {

    private static final String PASSWORD = "P8_3_2_test_password";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserAccountMapper userAccountMapper;
    @Autowired
    private TicketMapper ticketMapper;
    @Autowired
    private RedisCreateTicketIdempotencyStore store;
    @Autowired
    private CreateTicketRequestFingerprint requestFingerprint;
    @Autowired
    private CreateTicketIdempotencyKeyGenerator keyGenerator;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private final Set<String> redisKeys = new LinkedHashSet<>();
    private String testPrefix;

    @AfterEach
    void deleteExactRedisKeys() {
        for (String redisKey : redisKeys) {
            stringRedisTemplate.delete(redisKey);
            assertFalse(Boolean.TRUE.equals(stringRedisTemplate.hasKey(redisKey)),
                    () -> "HTTP 幂等测试 Key 清理失败: " + redisKey);
        }
        redisKeys.clear();
    }

    @AfterTransaction
    void verifyDatabaseRowsWereRolledBack() {
        if (testPrefix != null) {
            assertEquals(0L, ticketMapper.selectCount(new LambdaQueryWrapper<Ticket>()
                    .likeRight(Ticket::getCreatorName, testPrefix)));
            assertEquals(0L, userAccountMapper.selectCount(new LambdaQueryWrapper<UserAccount>()
                    .likeRight(UserAccount::getUsername, testPrefix)));
        }
    }

    @Test
    void shouldCreateTicketAndPersistSucceededState() throws Exception {
        AuthenticatedUser user = authenticatedUser("first");
        RequestData request = request("first");
        String key = idempotencyKey();
        String redisKey = track(user.id(), key);

        MvcResult result = create(user.token(), key, request)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").isNumber())
                .andReturn();

        Number ticketId = JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
        Ticket persisted = ticketMapper.selectById(ticketId.longValue());
        assertNotNull(persisted);
        assertEquals(user.id(), persisted.getCreatorUserId());
        assertEquals(1L, countTickets(request.creatorName()));
        assertEquals("SUCCEEDED", stringRedisTemplate.opsForHash().get(redisKey, "state"));
        assertNotNull(stringRedisTemplate.opsForHash().get(redisKey, "response"));
    }

    @Test
    void shouldReplaySameResponseWithoutCreatingSecondTicket() throws Exception {
        AuthenticatedUser user = authenticatedUser("replay");
        RequestData request = request("replay");
        String key = idempotencyKey();
        track(user.id(), key);

        MvcResult first = create(user.token(), key, request)
                .andExpect(status().isCreated()).andReturn();
        MvcResult second = create(user.token(), key, request)
                .andExpect(status().isCreated()).andReturn();

        Map<String, Object> firstData = JsonPath.read(
                first.getResponse().getContentAsString(), "$.data");
        Map<String, Object> secondData = JsonPath.read(
                second.getResponse().getContentAsString(), "$.data");
        assertEquals(firstData, secondData);
        assertEquals(1L, countTickets(request.creatorName()));
    }

    @Test
    void shouldRejectSameKeyForDifferentPayloadWithoutOverwritingSuccess() throws Exception {
        AuthenticatedUser user = authenticatedUser("mismatch");
        RequestData firstRequest = request("mismatch");
        RequestData differentRequest = new RequestData(
                firstRequest.title() + "-changed",
                firstRequest.description(),
                firstRequest.creatorName(),
                firstRequest.priority()
        );
        String key = idempotencyKey();
        String redisKey = track(user.id(), key);

        create(user.token(), key, firstRequest).andExpect(status().isCreated());
        Object storedResponse = stringRedisTemplate.opsForHash().get(redisKey, "response");

        create(user.token(), key, differentRequest)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40907));

        assertEquals(1L, countTickets(firstRequest.creatorName()));
        assertEquals(storedResponse, stringRedisTemplate.opsForHash().get(redisKey, "response"));
    }

    @Test
    void shouldReturnInProgressWithoutChangingOwnerOrCreatingTicket() throws Exception {
        AuthenticatedUser user = authenticatedUser("processing");
        RequestData request = request("processing");
        String key = idempotencyKey();
        String ownerToken = UUID.randomUUID().toString();
        String fingerprint = requestFingerprint.fingerprint(request.toDto());
        String redisKey = track(user.id(), key);
        assertEquals(IdempotencyAcquireStatus.ACQUIRED, store.acquire(
                user.id(), key, fingerprint, ownerToken).status());

        create(user.token(), key, request)
                .andExpect(status().isConflict())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER,
                        org.hamcrest.Matchers.matchesPattern("[1-9][0-9]*")))
                .andExpect(jsonPath("$.code").value(40906));

        assertEquals(0L, countTickets(request.creatorName()));
        assertEquals(ownerToken, stringRedisTemplate.opsForHash().get(redisKey, "ownerToken"));
    }

    @Test
    void shouldIsolateSameClientKeyBetweenUsers() throws Exception {
        AuthenticatedUser firstUser = authenticatedUser("usera");
        AuthenticatedUser secondUser = authenticatedUser("userb");
        RequestData firstRequest = request("usera");
        RequestData secondRequest = request("userb");
        String key = idempotencyKey();
        String firstRedisKey = track(firstUser.id(), key);
        String secondRedisKey = track(secondUser.id(), key);

        create(firstUser.token(), key, firstRequest).andExpect(status().isCreated());
        create(secondUser.token(), key, secondRequest).andExpect(status().isCreated());

        assertNotEquals(firstRedisKey, secondRedisKey);
        assertEquals(1L, countTickets(firstRequest.creatorName()));
        assertEquals(1L, countTickets(secondRequest.creatorName()));
        assertEquals(firstUser.id(), singleTicket(firstRequest.creatorName()).getCreatorUserId());
        assertEquals(secondUser.id(), singleTicket(secondRequest.creatorName()).getCreatorUserId());
    }

    @Test
    void shouldRejectMissingHeaderWithoutCreatingTicket() throws Exception {
        AuthenticatedUser user = authenticatedUser("missing");
        RequestData request = request("missing");

        create(user.token(), null, request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40003));

        assertEquals(0L, countTickets(request.creatorName()));
    }

    @Test
    void shouldRejectShortAndBlankHeadersWithoutCreatingTicket() throws Exception {
        AuthenticatedUser user = authenticatedUser("invalid");
        RequestData request = request("invalid");

        create(user.token(), "short", request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40003));
        create(user.token(), "   ", request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40003));

        assertEquals(0L, countTickets(request.creatorName()));
    }

    @Test
    void shouldNotAcquireRedisWhenRequestValidationFails() throws Exception {
        AuthenticatedUser user = authenticatedUser("validation");
        RequestData request = request("validation");
        String key = idempotencyKey();
        String redisKey = track(user.id(), key);
        RequestData invalid = new RequestData(
                "   ", request.description(), request.creatorName(), request.priority());

        create(user.token(), key, invalid)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));

        assertFalse(Boolean.TRUE.equals(stringRedisTemplate.hasKey(redisKey)));
        assertEquals(0L, countTickets(request.creatorName()));
    }

    @Test
    void shouldNotAcquireRedisForUnauthenticatedRequest() throws Exception {
        RequestData request = request("anonymous");

        mockMvc.perform(post("/api/tickets")
                        .header("Idempotency-Key", idempotencyKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request.json()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));

        assertEquals(0L, countTickets(request.creatorName()));
    }

    @Test
    void shouldNotRefreshSuccessTtlWhenResponseIsReplayed() throws Exception {
        AuthenticatedUser user = authenticatedUser("ttl");
        RequestData request = request("ttl");
        String key = idempotencyKey();
        String redisKey = track(user.id(), key);
        create(user.token(), key, request).andExpect(status().isCreated());
        long firstTtl = ttlMillis(redisKey);

        Thread.sleep(120);
        create(user.token(), key, request).andExpect(status().isCreated());
        long secondTtl = ttlMillis(redisKey);

        assertTrue(secondTtl < firstTtl, "成功响应重放不得刷新 success TTL");
        assertEquals(1L, countTickets(request.creatorName()));
    }

    private org.springframework.test.web.servlet.ResultActions create(
            String token,
            String idempotencyKey,
            RequestData request
    ) throws Exception {
        var builder = post("/api/tickets")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(request.json());
        if (idempotencyKey != null) {
            builder.header("Idempotency-Key", idempotencyKey);
        }
        return mockMvc.perform(builder);
    }

    private AuthenticatedUser authenticatedUser(String scenario) throws Exception {
        String username = prefix() + scenario;
        UserAccount user = new UserAccount();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setDisplayName("幂等HTTP测试用户");
        user.setRole(UserRole.USER);
        assertEquals(1, userAccountMapper.insert(user));
        assertNotNull(user.getId());

        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s"}
                                """.formatted(username, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        String token = JsonPath.read(login.getResponse().getContentAsString(), "$.data.accessToken");
        return new AuthenticatedUser(user.getId(), token);
    }

    private String track(Long creatorUserId, String idempotencyKey) {
        String redisKey = keyGenerator.generateRedisKey(creatorUserId, idempotencyKey);
        redisKeys.add(redisKey);
        return redisKey;
    }

    private long ttlMillis(String redisKey) {
        Long ttl = stringRedisTemplate.getExpire(redisKey, TimeUnit.MILLISECONDS);
        assertNotNull(ttl);
        assertTrue(ttl > 0);
        return ttl;
    }

    private long countTickets(String creatorName) {
        return ticketMapper.selectCount(new LambdaQueryWrapper<Ticket>()
                .eq(Ticket::getCreatorName, creatorName));
    }

    private Ticket singleTicket(String creatorName) {
        return ticketMapper.selectOne(new LambdaQueryWrapper<Ticket>()
                .eq(Ticket::getCreatorName, creatorName));
    }

    private RequestData request(String scenario) {
        return new RequestData(
                "P8-3-2 " + scenario,
                "真实HTTP幂等测试描述",
                prefix() + "creator_" + scenario,
                TicketPriority.HIGH
        );
    }

    private String prefix() {
        if (testPrefix == null) {
            testPrefix = "p832_" + UUID.randomUUID().toString().substring(0, 8) + "_";
        }
        return testPrefix;
    }

    private static String idempotencyKey() {
        return "create-ticket-" + UUID.randomUUID();
    }

    private record AuthenticatedUser(Long id, String token) {
    }

    private record RequestData(
            String title,
            String description,
            String creatorName,
            TicketPriority priority
    ) {
        CreateTicketRequest toDto() {
            return new CreateTicketRequest(title, description, creatorName, priority);
        }

        String json() {
            return """
                    {
                      "title": "%s",
                      "description": "%s",
                      "creatorName": "%s",
                      "priority": "%s"
                    }
                    """.formatted(title, description, creatorName, priority.name());
        }
    }
}
