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
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TicketAssignmentAuthorizationIntegrationTest {

    private static final String PASSWORD = "P7_2_http_test_password";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserAccountMapper userAccountMapper;

    @Autowired
    private TicketMapper ticketMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String testPrefix;

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
    void shouldRejectAnonymousAssignmentWithoutChangingDatabase() throws Exception {
        UserAccount creator = insertUser("creator", UserRole.USER);
        UserAccount agent = insertUser("agent", UserRole.AGENT);
        Ticket ticket = insertTicket("anonymous", creator.getId(), null);

        mockMvc.perform(patch("/api/tickets/{id}/assignee", ticket.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignmentJson(agent.getId())))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, containsString("Bearer")))
                .andExpect(jsonPath("$.code").value(40101))
                .andExpect(jsonPath("$.message").value("请先登录或提供有效访问令牌"))
                .andExpect(jsonPath("$.data").value(nullValue()));

        assertNull(ticketMapper.selectById(ticket.getId()).getAssigneeUserId());
    }

    @Test
    void shouldForbidUserAssignmentWithoutChangingDatabase() throws Exception {
        AccountSession user = createSession("user", UserRole.USER);
        UserAccount agent = insertUser("agent", UserRole.AGENT);
        Ticket ticket = insertTicket("user-forbidden", user.user().getId(), null);

        assertForbiddenAssignment(user.token(), ticket, agent.getId());
        assertNull(ticketMapper.selectById(ticket.getId()).getAssigneeUserId());
    }

    @Test
    void shouldForbidAgentAssignmentWithoutChangingDatabase() throws Exception {
        AccountSession agentActor = createSession("agent_actor", UserRole.AGENT);
        UserAccount targetAgent = insertUser("agent_target", UserRole.AGENT);
        Ticket ticket = insertTicket("agent-forbidden", agentActor.user().getId(), null);

        assertForbiddenAssignment(agentActor.token(), ticket, targetAgent.getId());
        assertNull(ticketMapper.selectById(ticket.getId()).getAssigneeUserId());
    }

    @Test
    void shouldAllowAdminFirstAssignmentThroughFullSecurityChain() throws Exception {
        AccountSession admin = createSession("admin", UserRole.ADMIN);
        UserAccount creator = insertUser("creator", UserRole.USER);
        UserAccount agent = insertUser("agent_a", UserRole.AGENT);
        Ticket ticket = insertTicket("admin-first", creator.getId(), null);

        mockMvc.perform(patch("/api/tickets/{id}/assignee", ticket.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignmentJson(agent.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.ticketId").value(ticket.getId()))
                .andExpect(jsonPath("$.data.assigneeUserId").value(agent.getId()))
                .andExpect(jsonPath("$.data.assigneeUsername").value(agent.getUsername()))
                .andExpect(jsonPath("$.data.assigneeDisplayName").value(agent.getDisplayName()));

        Ticket persisted = ticketMapper.selectById(ticket.getId());
        assertEquals(agent.getId(), persisted.getAssigneeUserId());
        assertEquals(TicketStatus.OPEN, persisted.getStatus());
    }

    @Test
    void shouldAllowAdminReassignmentThroughFullSecurityChain() throws Exception {
        AccountSession admin = createSession("admin", UserRole.ADMIN);
        UserAccount creator = insertUser("creator", UserRole.USER);
        UserAccount agentA = insertUser("agent_a", UserRole.AGENT);
        UserAccount agentB = insertUser("agent_b", UserRole.AGENT);
        Ticket ticket = insertTicket("admin-reassign", creator.getId(), agentA.getId());

        mockMvc.perform(patch("/api/tickets/{id}/assignee", ticket.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignmentJson(agentB.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.assigneeUserId").value(agentB.getId()))
                .andExpect(jsonPath("$.data.assigneeUsername").value(agentB.getUsername()));

        Ticket persisted = ticketMapper.selectById(ticket.getId());
        assertEquals(agentB.getId(), persisted.getAssigneeUserId());
        assertEquals(TicketStatus.OPEN, persisted.getStatus());
    }

    @Test
    void shouldRejectAdminAssignmentToUserWithoutChangingDatabase() throws Exception {
        AccountSession admin = createSession("admin", UserRole.ADMIN);
        UserAccount creator = insertUser("creator", UserRole.USER);
        UserAccount targetUser = insertUser("target_user", UserRole.USER);
        Ticket ticket = insertTicket("target-user", creator.getId(), null);

        assertAssignmentBusinessError(admin.token(), ticket, targetUser.getId(), 409, 40903);
        assertNull(ticketMapper.selectById(ticket.getId()).getAssigneeUserId());
    }

    @Test
    void shouldRejectAdminAssignmentToAdminWithoutChangingDatabase() throws Exception {
        AccountSession admin = createSession("admin", UserRole.ADMIN);
        UserAccount creator = insertUser("creator", UserRole.USER);
        Ticket ticket = insertTicket("target-admin", creator.getId(), null);

        assertAssignmentBusinessError(admin.token(), ticket, admin.user().getId(), 409, 40903);
        assertNull(ticketMapper.selectById(ticket.getId()).getAssigneeUserId());
    }

    @Test
    void shouldReturnNotFoundForMissingAssigneeWithoutChangingDatabase() throws Exception {
        AccountSession admin = createSession("admin", UserRole.ADMIN);
        UserAccount creator = insertUser("creator", UserRole.USER);
        Ticket ticket = insertTicket("missing-assignee", creator.getId(), null);
        assertNull(userAccountMapper.selectById(Long.MAX_VALUE));

        assertAssignmentBusinessError(admin.token(), ticket, Long.MAX_VALUE, 404, 40401);
        assertNull(ticketMapper.selectById(ticket.getId()).getAssigneeUserId());
    }

    @Test
    void shouldReturnNotFoundForMissingTicket() throws Exception {
        AccountSession admin = createSession("admin", UserRole.ADMIN);
        UserAccount agent = insertUser("agent", UserRole.AGENT);
        assertNull(ticketMapper.selectById(Long.MAX_VALUE));

        mockMvc.perform(patch("/api/tickets/{id}/assignee", Long.MAX_VALUE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignmentJson(agent.getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40400))
                .andExpect(jsonPath("$.message").value("工单不存在"))
                .andExpect(jsonPath("$.data").value(nullValue()));

        assertNull(ticketMapper.selectById(Long.MAX_VALUE));
    }

    @Test
    void shouldRejectDuplicateAssignmentAndKeepExistingAssignee() throws Exception {
        AccountSession admin = createSession("admin", UserRole.ADMIN);
        UserAccount creator = insertUser("creator", UserRole.USER);
        UserAccount agent = insertUser("agent", UserRole.AGENT);
        Ticket ticket = insertTicket("duplicate", creator.getId(), agent.getId());

        assertAssignmentBusinessError(admin.token(), ticket, agent.getId(), 409, 40904);
        assertEquals(agent.getId(), ticketMapper.selectById(ticket.getId()).getAssigneeUserId());
    }

    @Test
    void shouldKeepExistingStatusAuthorizationRules() throws Exception {
        AccountSession user = createSession("user", UserRole.USER);
        AccountSession agent = createSession("agent", UserRole.AGENT);
        AccountSession admin = createSession("admin", UserRole.ADMIN);
        Ticket ticket = insertTicket("status-regression", user.user().getId(), null);

        performStatusUpdate(agent.token(), ticket, "IN_PROGRESS", TicketStatus.IN_PROGRESS);
        performStatusUpdate(admin.token(), ticket, "RESOLVED", TicketStatus.RESOLVED);

        mockMvc.perform(patch("/api/tickets/{id}/status", ticket.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusJson("CLOSED")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300))
                .andExpect(jsonPath("$.message").value("权限不足，无法执行此操作"));

        assertEquals(TicketStatus.RESOLVED, ticketMapper.selectById(ticket.getId()).getStatus());
    }

    private void assertForbiddenAssignment(String token, Ticket ticket, Long targetId) throws Exception {
        mockMvc.perform(patch("/api/tickets/{id}/assignee", ticket.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignmentJson(targetId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300))
                .andExpect(jsonPath("$.message").value("权限不足，无法执行此操作"))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    private void assertAssignmentBusinessError(
            String token,
            Ticket ticket,
            Long targetId,
            int expectedHttpStatus,
            int expectedCode
    ) throws Exception {
        mockMvc.perform(patch("/api/tickets/{id}/assignee", ticket.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignmentJson(targetId)))
                .andExpect(status().is(expectedHttpStatus))
                .andExpect(jsonPath("$.code").value(expectedCode))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    private void performStatusUpdate(
            String token,
            Ticket ticket,
            String targetStatus,
            TicketStatus expectedStatus
    ) throws Exception {
        mockMvc.perform(patch("/api/tickets/{id}/status", ticket.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusJson(targetStatus)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value(expectedStatus.name()));
        assertEquals(expectedStatus, ticketMapper.selectById(ticket.getId()).getStatus());
    }

    private AccountSession createSession(String suffix, UserRole role) throws Exception {
        UserAccount user = insertUser(suffix, role);
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(user.getUsername())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.user.role").value(role.name()))
                .andReturn();
        String token = JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
        return new AccountSession(user, token);
    }

    private UserAccount insertUser(String suffix, UserRole role) {
        UserAccount user = new UserAccount();
        user.setUsername(prefix() + suffix);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setDisplayName("P7-2 HTTP " + role.name());
        user.setRole(role);
        assertEquals(1, userAccountMapper.insert(user));
        assertNotNull(user.getId());
        assertTrue(user.getId() > 0);
        assertTrue(passwordEncoder.matches(PASSWORD, user.getPasswordHash()));
        return user;
    }

    private Ticket insertTicket(String scenario, Long creatorUserId, Long assigneeUserId) {
        Ticket ticket = new Ticket();
        ticket.setTitle(prefix() + scenario);
        ticket.setDescription("P7-2真实授权HTTP测试");
        ticket.setCreatorName(prefix() + "creator-" + scenario);
        ticket.setCreatorUserId(creatorUserId);
        ticket.setAssigneeUserId(assigneeUserId);
        ticket.setPriority(TicketPriority.HIGH);
        ticket.setStatus(TicketStatus.OPEN);
        assertEquals(1, ticketMapper.insert(ticket));
        assertNotNull(ticket.getId());
        return ticketMapper.selectById(ticket.getId());
    }

    private String prefix() {
        if (testPrefix == null) {
            testPrefix = "p72h_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20) + "_";
        }
        return testPrefix;
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static String assignmentJson(Long assigneeUserId) {
        return """
                {"assigneeUserId":%d}
                """.formatted(assigneeUserId);
    }

    private static String statusJson(String status) {
        return """
                {"status":"%s"}
                """.formatted(status);
    }

    private static String loginJson(String username) {
        return """
                {
                  "username":"%s",
                  "password":"%s"
                }
                """.formatted(username, PASSWORD);
    }

    private record AccountSession(UserAccount user, String token) {
    }
}
