package com.xiaoyang.aiticketplatform.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jayway.jsonpath.JsonPath;
import com.xiaoyang.aiticketplatform.entity.Ticket;
import com.xiaoyang.aiticketplatform.entity.TicketOperationLog;
import com.xiaoyang.aiticketplatform.entity.UserAccount;
import com.xiaoyang.aiticketplatform.enums.TicketOperationType;
import com.xiaoyang.aiticketplatform.enums.TicketPriority;
import com.xiaoyang.aiticketplatform.enums.TicketStatus;
import com.xiaoyang.aiticketplatform.enums.UserRole;
import com.xiaoyang.aiticketplatform.mapper.TicketMapper;
import com.xiaoyang.aiticketplatform.mapper.TicketOperationLogMapper;
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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TicketOperationLoggingHttpIntegrationTest {

    private static final String PASSWORD = "P7-3-2-http-log-password";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserAccountMapper userAccountMapper;

    @Autowired
    private TicketMapper ticketMapper;

    @Autowired
    private TicketOperationLogMapper ticketOperationLogMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final List<Long> preparedTicketIds = new ArrayList<>();
    private String testPrefix;

    @AfterTransaction
    void verifyAllPreparedDataWasRolledBack() {
        if (testPrefix == null) {
            return;
        }
        assertEquals(0L, userAccountMapper.selectCount(
                new LambdaQueryWrapper<UserAccount>()
                        .likeRight(UserAccount::getUsername, testPrefix)
        ));
        assertEquals(0L, ticketMapper.selectCount(
                new LambdaQueryWrapper<Ticket>()
                        .likeRight(Ticket::getCreatorName, testPrefix)
        ));
        if (!preparedTicketIds.isEmpty()) {
            assertEquals(0L, ticketOperationLogMapper.selectCount(
                    new LambdaQueryWrapper<TicketOperationLog>()
                            .in(TicketOperationLog::getTicketId, preparedTicketIds)
            ));
        }
    }

    @Test
    void shouldRecordAuthenticatedAgentForStatusUpdate() throws Exception {
        AccountSession agent = createSession("status-agent", UserRole.AGENT);
        UserAccount creator = insertUser("status-creator", UserRole.USER);
        Ticket ticket = insertTicket("agent-status", creator.getId(), null);

        performStatusUpdate(agent.token(), ticket.getId(), "IN_PROGRESS", 200, 0);

        TicketOperationLog log = singleLog(ticket.getId());
        assertAll(
                () -> assertEquals(TicketStatus.IN_PROGRESS, ticketMapper.selectById(ticket.getId()).getStatus()),
                () -> assertEquals(agent.user().getId(), log.getOperatorUserId()),
                () -> assertEquals(TicketOperationType.STATUS_CHANGED, log.getOperationType()),
                () -> assertEquals("OPEN", log.getBeforeValue()),
                () -> assertEquals("IN_PROGRESS", log.getAfterValue())
        );
    }

    @Test
    void shouldRecordAuthenticatedAdminForStatusUpdate() throws Exception {
        AccountSession admin = createSession("status-admin", UserRole.ADMIN);
        UserAccount creator = insertUser("admin-status-creator", UserRole.USER);
        Ticket ticket = insertTicket("admin-status", creator.getId(), null);

        performStatusUpdate(admin.token(), ticket.getId(), "IN_PROGRESS", 200, 0);

        TicketOperationLog log = singleLog(ticket.getId());
        assertEquals(admin.user().getId(), log.getOperatorUserId());
        assertEquals(TicketOperationType.STATUS_CHANGED, log.getOperationType());
    }

    @Test
    void shouldRecordAdminAsAssignmentOperatorInsteadOfTargetAgent() throws Exception {
        AccountSession admin = createSession("assign-admin", UserRole.ADMIN);
        UserAccount creator = insertUser("assign-creator", UserRole.USER);
        UserAccount targetAgent = insertUser("assign-target", UserRole.AGENT);
        Ticket ticket = insertTicket("admin-assignment", creator.getId(), null);

        performAssignment(admin.token(), ticket.getId(), targetAgent.getId(), 200, 0);

        TicketOperationLog log = singleLog(ticket.getId());
        assertAll(
                () -> assertEquals(targetAgent.getId(), ticketMapper.selectById(ticket.getId()).getAssigneeUserId()),
                () -> assertEquals(admin.user().getId(), log.getOperatorUserId()),
                () -> assertTrue(!targetAgent.getId().equals(log.getOperatorUserId())),
                () -> assertEquals(TicketOperationType.ASSIGNEE_CHANGED, log.getOperationType()),
                () -> assertNull(log.getBeforeValue()),
                () -> assertEquals(targetAgent.getId().toString(), log.getAfterValue())
        );
    }

    @Test
    void shouldNotWriteLogWhenUserStatusUpdateIsForbidden() throws Exception {
        AccountSession user = createSession("forbidden-user", UserRole.USER);
        Ticket ticket = insertTicket("user-status-forbidden", user.user().getId(), null);

        performStatusUpdate(user.token(), ticket.getId(), "IN_PROGRESS", 403, 40300);

        assertEquals(TicketStatus.OPEN, ticketMapper.selectById(ticket.getId()).getStatus());
        assertEquals(0L, countLogs(ticket.getId()));
    }

    @Test
    void shouldNotWriteLogWhenAgentAssignmentIsForbidden() throws Exception {
        AccountSession agent = createSession("forbidden-agent", UserRole.AGENT);
        UserAccount targetAgent = insertUser("forbidden-target", UserRole.AGENT);
        Ticket ticket = insertTicket("agent-assignment-forbidden", agent.user().getId(), null);

        performAssignment(agent.token(), ticket.getId(), targetAgent.getId(), 403, 40300);

        assertNull(ticketMapper.selectById(ticket.getId()).getAssigneeUserId());
        assertEquals(0L, countLogs(ticket.getId()));
    }

    @Test
    void shouldNotWriteLogForInvalidStatusTransition() throws Exception {
        AccountSession agent = createSession("invalid-status-agent", UserRole.AGENT);
        UserAccount creator = insertUser("invalid-status-creator", UserRole.USER);
        Ticket ticket = insertTicket("invalid-status", creator.getId(), null);

        performStatusUpdate(agent.token(), ticket.getId(), "RESOLVED", 409, 40900);

        assertEquals(TicketStatus.OPEN, ticketMapper.selectById(ticket.getId()).getStatus());
        assertEquals(0L, countLogs(ticket.getId()));
    }

    @Test
    void shouldKeepOnlyFirstLogWhenAssignmentIsRepeated() throws Exception {
        AccountSession admin = createSession("duplicate-admin", UserRole.ADMIN);
        UserAccount creator = insertUser("duplicate-creator", UserRole.USER);
        UserAccount targetAgent = insertUser("duplicate-target", UserRole.AGENT);
        Ticket ticket = insertTicket("duplicate-assignment", creator.getId(), null);

        performAssignment(admin.token(), ticket.getId(), targetAgent.getId(), 200, 0);
        performAssignment(admin.token(), ticket.getId(), targetAgent.getId(), 409, 40904);

        assertEquals(targetAgent.getId(), ticketMapper.selectById(ticket.getId()).getAssigneeUserId());
        assertEquals(1L, countLogs(ticket.getId()));
        assertEquals(admin.user().getId(), singleLog(ticket.getId()).getOperatorUserId());
    }

    private void performStatusUpdate(
            String token,
            Long ticketId,
            String targetStatus,
            int expectedHttpStatus,
            int expectedCode
    ) throws Exception {
        mockMvc.perform(patch("/api/tickets/{id}/status", ticketId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusJson(targetStatus)))
                .andExpect(status().is(expectedHttpStatus))
                .andExpect(jsonPath("$.code").value(expectedCode));
    }

    private void performAssignment(
            String token,
            Long ticketId,
            Long assigneeUserId,
            int expectedHttpStatus,
            int expectedCode
    ) throws Exception {
        mockMvc.perform(patch("/api/tickets/{id}/assignee", ticketId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignmentJson(assigneeUserId)))
                .andExpect(status().is(expectedHttpStatus))
                .andExpect(jsonPath("$.code").value(expectedCode));
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
        user.setDisplayName("P7-3-2 HTTP " + role.name());
        user.setRole(role);
        assertEquals(1, userAccountMapper.insert(user));
        assertNotNull(user.getId());
        assertTrue(user.getId() > 0);
        return user;
    }

    private Ticket insertTicket(String suffix, Long creatorUserId, Long assigneeUserId) {
        Ticket ticket = new Ticket();
        ticket.setTitle(prefix() + suffix);
        ticket.setDescription("P7-3-2 真实安全链路操作日志测试");
        ticket.setCreatorName(prefix() + "creator-" + suffix);
        ticket.setCreatorUserId(creatorUserId);
        ticket.setAssigneeUserId(assigneeUserId);
        ticket.setPriority(TicketPriority.HIGH);
        ticket.setStatus(TicketStatus.OPEN);
        assertEquals(1, ticketMapper.insert(ticket));
        assertNotNull(ticket.getId());
        preparedTicketIds.add(ticket.getId());
        return ticketMapper.selectById(ticket.getId());
    }

    private TicketOperationLog singleLog(Long ticketId) {
        List<TicketOperationLog> logs = ticketOperationLogMapper.selectList(
                new LambdaQueryWrapper<TicketOperationLog>()
                        .eq(TicketOperationLog::getTicketId, ticketId)
                        .orderByAsc(TicketOperationLog::getCreatedAt)
                        .orderByAsc(TicketOperationLog::getId)
        );
        assertEquals(1, logs.size());
        return logs.getFirst();
    }

    private long countLogs(Long ticketId) {
        return ticketOperationLogMapper.selectCount(
                new LambdaQueryWrapper<TicketOperationLog>()
                        .eq(TicketOperationLog::getTicketId, ticketId)
        );
    }

    private String prefix() {
        if (testPrefix == null) {
            testPrefix = "p732h_"
                    + UUID.randomUUID().toString().replace("-", "").substring(0, 18)
                    + "_";
        }
        return testPrefix;
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static String statusJson(String status) {
        return """
                {"status":"%s"}
                """.formatted(status);
    }

    private static String assignmentJson(Long assigneeUserId) {
        return """
                {"assigneeUserId":%d}
                """.formatted(assigneeUserId);
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
