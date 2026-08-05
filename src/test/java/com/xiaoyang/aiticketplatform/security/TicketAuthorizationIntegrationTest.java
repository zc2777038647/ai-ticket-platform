package com.xiaoyang.aiticketplatform.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jayway.jsonpath.JsonPath;
import com.xiaoyang.aiticketplatform.entity.Ticket;
import com.xiaoyang.aiticketplatform.entity.UserAccount;
import com.xiaoyang.aiticketplatform.enums.TicketPriority;
import com.xiaoyang.aiticketplatform.enums.TicketStatus;
import com.xiaoyang.aiticketplatform.enums.UserRole;
import com.xiaoyang.aiticketplatform.mapper.TicketMapper;
import com.xiaoyang.aiticketplatform.mapper.UserAccountMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TicketAuthorizationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserAccountMapper userAccountMapper;

    @Autowired
    private TicketMapper ticketMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String testPrefix;
    private Long authenticatedUserId;

    @AfterTransaction
    void verifyTestDataWasRolledBack() {
        if (testPrefix != null) {
            assertEquals(0L, userAccountMapper.selectCount(
                    new LambdaQueryWrapper<UserAccount>()
                            .likeRight(UserAccount::getUsername, testPrefix)
            ));
            assertEquals(0L, ticketMapper.selectCount(
                    new LambdaQueryWrapper<Ticket>()
                            .likeRight(Ticket::getCreatorName, testPrefix)
            ));
        }
    }

    @Test
    void shouldRejectAnonymousTicketCreationWithoutInsertingData() throws Exception {
        String prefix = prefix();

        assertUnauthorized(mockMvc.perform(post("/api/tickets")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createTicketJson(prefix + "creator"))));

        assertEquals(0L, countTestTickets());
    }

    @Test
    void shouldAllowUserToCreateTicketInRealDatabase() throws Exception {
        String token = loginToken(UserRole.USER);
        String creatorName = prefix() + "客户端填写的展示名称";

        MvcResult result = mockMvc.perform(post("/api/tickets")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createTicketJson(creatorName)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andReturn();

        Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
        Ticket persisted = ticketMapper.selectById(id.longValue());
        assertNotNull(persisted);
        assertEquals(authenticatedUserId, persisted.getCreatorUserId());
        assertEquals(creatorName, persisted.getCreatorName());
        assertEquals(TicketStatus.OPEN, persisted.getStatus());
    }

    @Test
    void shouldBindAgentIdWhenAgentCreatesTicket() throws Exception {
        assertAuthenticatedCreatorBinding(UserRole.AGENT);
    }

    @Test
    void shouldBindAdminIdWhenAdminCreatesTicket() throws Exception {
        assertAuthenticatedCreatorBinding(UserRole.ADMIN);
    }

    @Test
    void shouldIgnoreClientSuppliedCreatorUserIdAndUseTokenSubject() throws Exception {
        String token = loginToken(UserRole.USER);
        Long tokenUserId = authenticatedUserId;
        UserAccount anotherUser = insertUser(UserRole.AGENT, "other");
        String creatorName = prefix() + "override-attempt";

        MvcResult result = mockMvc.perform(post("/api/tickets")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createTicketJson(creatorName, anotherUser.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        Number ticketId = JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
        Ticket persisted = ticketMapper.selectById(ticketId.longValue());
        assertNotNull(persisted);
        assertEquals(tokenUserId, persisted.getCreatorUserId());
        assertEquals(creatorName, persisted.getCreatorName());
    }

    @Test
    void shouldForbidUserFromPageQuery() throws Exception {
        String token = loginToken(UserRole.USER);

        assertForbidden(mockMvc.perform(get("/api/tickets")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .param("page", "1")
                .param("size", "10")));
    }

    @Test
    void shouldForbidUserFromTicketDetailsWithoutLeakingTicket() throws Exception {
        Ticket ticket = insertTicket(TicketStatus.OPEN);
        String token = loginToken(UserRole.USER);

        assertForbidden(mockMvc.perform(get("/api/tickets/{id}", ticket.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))))
                .andExpect(content().string(not(containsString(ticket.getTitle()))))
                .andExpect(content().string(not(containsString(ticket.getCreatorName()))));
    }

    @Test
    void shouldForbidUserStatusUpdateAndKeepDatabaseStatusOpen() throws Exception {
        Ticket ticket = insertTicket(TicketStatus.OPEN);
        String token = loginToken(UserRole.USER);

        assertForbidden(mockMvc.perform(patch("/api/tickets/{id}/status", ticket.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(statusJson("IN_PROGRESS"))));

        assertEquals(TicketStatus.OPEN, ticketMapper.selectById(ticket.getId()).getStatus());
    }

    @Test
    void shouldAllowAgentToReadTicketPageAndDetails() throws Exception {
        Ticket ticket = insertTicket(TicketStatus.OPEN);
        String token = loginToken(UserRole.AGENT);

        mockMvc.perform(get("/api/tickets")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .param("page", "1")
                        .param("size", "10")
                        .param("creatorName", ticket.getCreatorName()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.records[0].id").value(ticket.getId()));

        assertTicketDetailsAllowed(token, ticket);
    }

    @Test
    void shouldAllowAgentToUpdateStatusInDatabase() throws Exception {
        Ticket ticket = insertTicket(TicketStatus.OPEN);
        String token = loginToken(UserRole.AGENT);

        assertStatusUpdateAllowed(token, ticket, "IN_PROGRESS", TicketStatus.IN_PROGRESS);
    }

    @Test
    void shouldAllowAdminToReadAndUpdateTicket() throws Exception {
        Ticket ticket = insertTicket(TicketStatus.OPEN);
        String token = loginToken(UserRole.ADMIN);

        assertTicketDetailsAllowed(token, ticket);
        assertStatusUpdateAllowed(token, ticket, "IN_PROGRESS", TicketStatus.IN_PROGRESS);
    }

    @Test
    void shouldReturnUnauthorizedForAllAnonymousTicketReadAndUpdateRequests() throws Exception {
        Ticket ticket = insertTicket(TicketStatus.OPEN);

        assertUnauthorized(mockMvc.perform(get("/api/tickets")
                .param("page", "1")
                .param("size", "10")));
        assertUnauthorized(mockMvc.perform(get("/api/tickets/{id}", ticket.getId())));
        assertUnauthorized(mockMvc.perform(patch("/api/tickets/{id}/status", ticket.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(statusJson("IN_PROGRESS"))));
        assertEquals(TicketStatus.OPEN, ticketMapper.selectById(ticket.getId()).getStatus());
    }

    @Test
    void shouldDistinguishInsufficientRoleFromInvalidToken() throws Exception {
        String userToken = loginToken(UserRole.USER);

        assertForbidden(mockMvc.perform(get("/api/tickets")
                .header(HttpHeaders.AUTHORIZATION, bearer(userToken))
                .param("page", "1")
                .param("size", "10")));

        assertUnauthorized(mockMvc.perform(get("/api/tickets")
                .header(HttpHeaders.AUTHORIZATION, "Bearer broken-test-token")
                .param("page", "1")
                .param("size", "10")));
    }

    private void assertTicketDetailsAllowed(String token, Ticket ticket) throws Exception {
        mockMvc.perform(get("/api/tickets/{id}", ticket.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(ticket.getId()))
                .andExpect(jsonPath("$.data.title").value(ticket.getTitle()))
                .andExpect(jsonPath("$.data.creatorName").value(ticket.getCreatorName()));
    }

    private void assertAuthenticatedCreatorBinding(UserRole role) throws Exception {
        String token = loginToken(role);
        Long expectedUserId = authenticatedUserId;
        String creatorName = prefix() + role.name().toLowerCase(Locale.ROOT) + "-display";

        MvcResult result = mockMvc.perform(post("/api/tickets")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createTicketJson(creatorName)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        Number ticketId = JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
        Ticket persisted = ticketMapper.selectById(ticketId.longValue());
        assertNotNull(persisted);
        assertEquals(expectedUserId, persisted.getCreatorUserId());
        assertEquals(creatorName, persisted.getCreatorName());
    }

    private void assertStatusUpdateAllowed(
            String token,
            Ticket ticket,
            String requestedStatus,
            TicketStatus expectedStatus
    ) throws Exception {
        mockMvc.perform(patch("/api/tickets/{id}/status", ticket.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusJson(requestedStatus)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value(expectedStatus.name()));
        assertEquals(expectedStatus, ticketMapper.selectById(ticket.getId()).getStatus());
    }

    private ResultActions assertForbidden(ResultActions resultActions) throws Exception {
        return resultActions
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE))
                .andExpect(jsonPath("$.code").value(40300))
                .andExpect(jsonPath("$.message").value("权限不足，无法执行此操作"))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    private ResultActions assertUnauthorized(ResultActions resultActions) throws Exception {
        return resultActions
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, containsString("Bearer")))
                .andExpect(jsonPath("$.code").value(40101))
                .andExpect(jsonPath("$.message").value("请先登录或提供有效访问令牌"))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    private String loginToken(UserRole role) throws Exception {
        UserAccount user = insertUser(role, role.name().toLowerCase(Locale.ROOT));
        String password = "P5_5_test_password";
        authenticatedUserId = user.getId();

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(user.getUsername(), password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.user.role").value(role.name()))
                .andReturn();
        return JsonPath.read(loginResult.getResponse().getContentAsString(), "$.data.accessToken");
    }

    private UserAccount insertUser(UserRole role, String suffix) {
        String username = prefix() + suffix;
        String password = "P5_5_test_password";
        UserAccount user = new UserAccount();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setDisplayName("授权测试" + role.name());
        user.setRole(role);
        assertEquals(1, userAccountMapper.insert(user));
        assertNotNull(user.getId());
        assertTrue(passwordEncoder.matches(password, user.getPasswordHash()));
        return user;
    }

    private Ticket insertTicket(TicketStatus status) {
        Ticket ticket = new Ticket();
        ticket.setTitle(prefix() + "ticket");
        ticket.setDescription("角色授权集成测试描述");
        ticket.setCreatorName(prefix() + "creator");
        ticket.setPriority(TicketPriority.HIGH);
        ticket.setStatus(status);
        assertEquals(1, ticketMapper.insert(ticket));
        assertNotNull(ticket.getId());
        return ticketMapper.selectById(ticket.getId());
    }

    private long countTestTickets() {
        return ticketMapper.selectCount(new LambdaQueryWrapper<Ticket>()
                .likeRight(Ticket::getCreatorName, prefix()));
    }

    private String prefix() {
        if (testPrefix == null) {
            testPrefix = "p55_" + UUID.randomUUID().toString().replace("-", "") + "_";
        }
        return testPrefix;
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static String createTicketJson(String creatorName) {
        return """
                {
                  "title": "授权测试工单",
                  "description": "授权测试描述",
                  "creatorName": "%s",
                  "priority": "HIGH"
                }
                """.formatted(creatorName);
    }

    private static String createTicketJson(String creatorName, Long attemptedCreatorUserId) {
        return """
                {
                  "title": "授权测试工单",
                  "description": "授权测试描述",
                  "creatorName": "%s",
                  "priority": "HIGH",
                  "creatorUserId": %d
                }
                """.formatted(creatorName, attemptedCreatorUserId);
    }

    private static String statusJson(String status) {
        return """
                {"status":"%s"}
                """.formatted(status);
    }

    private static String loginJson(String username, String password) {
        return """
                {
                  "username": "%s",
                  "password": "%s"
                }
                """.formatted(username, password);
    }
}
