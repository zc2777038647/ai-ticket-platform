package com.xiaoyang.aiticketplatform.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xiaoyang.aiticketplatform.entity.Ticket;
import com.xiaoyang.aiticketplatform.entity.TicketOperationLog;
import com.xiaoyang.aiticketplatform.entity.UserAccount;
import com.xiaoyang.aiticketplatform.enums.TicketPriority;
import com.xiaoyang.aiticketplatform.enums.TicketStatus;
import com.xiaoyang.aiticketplatform.enums.UserRole;
import com.xiaoyang.aiticketplatform.mapper.TicketMapper;
import com.xiaoyang.aiticketplatform.mapper.TicketOperationLogMapper;
import com.xiaoyang.aiticketplatform.mapper.UserAccountMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;
import java.util.List;
import java.util.Map;
import java.time.Instant;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
class TicketStatusUpdateHttpIntegrationTest {

    private static final long MISSING_TICKET_ID = Long.MAX_VALUE;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private TicketMapper ticketMapper;

    @Autowired
    private TicketOperationLogMapper ticketOperationLogMapper;

    @Autowired
    private UserAccountMapper userAccountMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private MockMvc mockMvc;
    private String cleanupCreatorName;
    private UserAccount operator;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @AfterTransaction
    void verifyPreparedTicketWasRolledBack() {
        if (cleanupCreatorName != null) {
            assertEquals(0L, countPreparedTickets());
            assertEquals(0L, userAccountMapper.selectCount(
                    new LambdaQueryWrapper<UserAccount>()
                            .eq(UserAccount::getUsername, cleanupCreatorName)
            ));
        }
    }

    @Test
    void shouldCompleteStatusChainThroughRealHttpAndMySql() throws Exception {
        Ticket originalTicket = insertUniqueTicket("HTTP完整状态链");

        performSuccessfulStatusUpdate(originalTicket, TicketStatus.IN_PROGRESS);
        performSuccessfulStatusUpdate(originalTicket, TicketStatus.RESOLVED);
        performSuccessfulStatusUpdate(originalTicket, TicketStatus.CLOSED);

        mockMvc.perform(patch("/api/tickets/{id}/status", originalTicket.getId())
                        .principal(authentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusRequestJson("OPEN")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40900))
                .andExpect(jsonPath("$.message").value("工单状态流转不合法"))
                .andExpect(jsonPath("$.data").value(nullValue()));

        assertPersistedTicket(originalTicket, TicketStatus.CLOSED);
        assertEquals(3L, countLogs(originalTicket.getId()));
    }

    @Test
    void shouldRejectSkippedStatusWithoutChangingDatabase() throws Exception {
        Ticket originalTicket = insertUniqueTicket("HTTP跳级状态更新");

        mockMvc.perform(patch("/api/tickets/{id}/status", originalTicket.getId())
                        .principal(authentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusRequestJson("RESOLVED")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40900))
                .andExpect(jsonPath("$.message").value("工单状态流转不合法"))
                .andExpect(jsonPath("$.data").value(nullValue()));

        assertPersistedTicket(originalTicket, TicketStatus.OPEN);
        assertEquals(0L, countLogs(originalTicket.getId()));
    }

    @Test
    void shouldReturnNotFoundWithoutChangingDatabase() throws Exception {
        assertNull(ticketMapper.selectById(MISSING_TICKET_ID));
        long countBeforeRequest = countTickets();

        mockMvc.perform(patch("/api/tickets/{id}/status", MISSING_TICKET_ID)
                        .principal(authentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusRequestJson("IN_PROGRESS")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40400))
                .andExpect(jsonPath("$.message").value("工单不存在"))
                .andExpect(jsonPath("$.data").value(nullValue()));

        assertNull(ticketMapper.selectById(MISSING_TICKET_ID));
        assertEquals(countBeforeRequest, countTickets());
    }

    @Test
    void shouldRejectInvalidBodiesWithoutChangingDatabase() throws Exception {
        Ticket originalTicket = insertUniqueTicket("HTTP请求体校验");

        mockMvc.perform(patch("/api/tickets/{id}/status", originalTicket.getId())
                        .principal(authentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": null
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000))
                .andExpect(jsonPath("$.message").value("请求参数校验失败"))
                .andExpect(jsonPath("$.data.status").value("目标状态不能为空"));

        mockMvc.perform(patch("/api/tickets/{id}/status", originalTicket.getId())
                        .principal(authentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusRequestJson("UNKNOWN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value("请求体格式错误"))
                .andExpect(jsonPath("$.data").value(nullValue()));

        assertEquals(1L, countPreparedTickets());
        assertPersistedTicket(originalTicket, TicketStatus.OPEN);
    }

    @Test
    void shouldRejectInvalidPathIdsWithoutChangingDatabase() throws Exception {
        Ticket originalTicket = insertUniqueTicket("HTTP路径参数校验");

        mockMvc.perform(patch("/api/tickets/0/status")
                        .principal(authentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusRequestJson("IN_PROGRESS")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000))
                .andExpect(jsonPath("$.message").value("请求参数校验失败"));

        mockMvc.perform(patch("/api/tickets/abc/status")
                        .principal(authentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusRequestJson("IN_PROGRESS")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40002))
                .andExpect(jsonPath("$.message").value("请求参数格式错误"))
                .andExpect(jsonPath("$.data").value(nullValue()));

        assertEquals(1L, countPreparedTickets());
        assertPersistedTicket(originalTicket, TicketStatus.OPEN);
    }

    private Ticket insertUniqueTicket(String scenario) {
        cleanupCreatorName = "http-status-update-" + UUID.randomUUID();
        Ticket ticket = new Ticket();
        ticket.setTitle(scenario + "-" + cleanupCreatorName);
        ticket.setDescription("验证 PATCH Controller 到 MySQL 的完整状态更新链路");
        ticket.setCreatorName(cleanupCreatorName);
        ticket.setPriority(TicketPriority.HIGH);
        ticket.setStatus(TicketStatus.OPEN);

        assertEquals(1, ticketMapper.insert(ticket));
        assertNotNull(ticket.getId());
        assertTrue(ticket.getId() > 0);
        assertEquals(1L, countPreparedTickets());
        return ticket;
    }

    private void performSuccessfulStatusUpdate(Ticket originalTicket, TicketStatus targetStatus)
            throws Exception {
        mockMvc.perform(patch("/api/tickets/{id}/status", originalTicket.getId())
                        .principal(authentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusRequestJson(targetStatus.name())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.id").value(originalTicket.getId()))
                .andExpect(jsonPath("$.data.title").value(originalTicket.getTitle()))
                .andExpect(jsonPath("$.data.description").value(originalTicket.getDescription()))
                .andExpect(jsonPath("$.data.creatorName").value(originalTicket.getCreatorName()))
                .andExpect(jsonPath("$.data.priority").value(originalTicket.getPriority().name()))
                .andExpect(jsonPath("$.data.status").value(targetStatus.name()));

        assertPersistedTicket(originalTicket, targetStatus);
    }

    private void assertPersistedTicket(Ticket originalTicket, TicketStatus expectedStatus) {
        Ticket persistedTicket = ticketMapper.selectById(originalTicket.getId());
        assertNotNull(persistedTicket);
        assertAll(
                () -> assertEquals(originalTicket.getId(), persistedTicket.getId()),
                () -> assertEquals(originalTicket.getTitle(), persistedTicket.getTitle()),
                () -> assertEquals(originalTicket.getDescription(), persistedTicket.getDescription()),
                () -> assertEquals(originalTicket.getCreatorName(), persistedTicket.getCreatorName()),
                () -> assertEquals(originalTicket.getPriority(), persistedTicket.getPriority()),
                () -> assertEquals(expectedStatus, persistedTicket.getStatus())
        );
    }

    private long countPreparedTickets() {
        return ticketMapper.selectCount(new LambdaQueryWrapper<Ticket>()
                .eq(Ticket::getCreatorName, cleanupCreatorName));
    }

    private long countTickets() {
        return ticketMapper.selectCount(new LambdaQueryWrapper<>());
    }

    private long countLogs(Long ticketId) {
        return ticketOperationLogMapper.selectCount(
                new LambdaQueryWrapper<TicketOperationLog>()
                        .eq(TicketOperationLog::getTicketId, ticketId)
        );
    }

    private JwtAuthenticationToken authentication() {
        if (cleanupCreatorName == null) {
            cleanupCreatorName = "http-status-update-" + UUID.randomUUID();
        }
        if (operator == null) {
            operator = new UserAccount();
            operator.setUsername(cleanupCreatorName);
            operator.setPasswordHash(passwordEncoder.encode("P7-3-2-http-status-password"));
            operator.setDisplayName("P7-3-2 HTTP 状态操作者");
            operator.setRole(UserRole.AGENT);
            assertEquals(1, userAccountMapper.insert(operator));
            assertNotNull(operator.getId());
        }

        Instant now = Instant.now();
        Jwt jwt = new Jwt(
                "test-token",
                now,
                now.plusSeconds(300),
                Map.of("alg", "HS256"),
                Map.of(
                        "sub", operator.getId().toString(),
                        "username", operator.getUsername(),
                        "role", operator.getRole().name(),
                        "jti", UUID.randomUUID().toString()
                )
        );
        return new JwtAuthenticationToken(jwt, List.of(), jwt.getSubject());
    }

    private static String statusRequestJson(String status) {
        return """
                {
                  "status": "%s"
                }
                """.formatted(status);
    }
}
