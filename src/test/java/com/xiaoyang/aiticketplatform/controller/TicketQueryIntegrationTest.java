package com.xiaoyang.aiticketplatform.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xiaoyang.aiticketplatform.entity.Ticket;
import com.xiaoyang.aiticketplatform.enums.TicketPriority;
import com.xiaoyang.aiticketplatform.enums.TicketStatus;
import com.xiaoyang.aiticketplatform.mapper.TicketMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
class TicketQueryIntegrationTest {

    private static final long MISSING_TICKET_ID = Long.MAX_VALUE;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private TicketMapper ticketMapper;

    private MockMvc mockMvc;
    private String insertedCreatorName;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @AfterTransaction
    void verifyPreparedTicketWasRolledBack() {
        if (insertedCreatorName != null) {
            assertEquals(0L, ticketMapper.selectCount(queryByCreatorName(insertedCreatorName)));
        }
    }

    @Test
    void shouldQueryExistingTicketThroughCompleteApplicationChain() throws Exception {
        long countBeforePreparation = countTickets();
        insertedCreatorName = "p23-query-" + UUID.randomUUID();
        Ticket ticket = new Ticket();
        ticket.setTitle("查询集成测试工单");
        ticket.setDescription("查询集成测试描述");
        ticket.setCreatorName(insertedCreatorName);
        ticket.setPriority(TicketPriority.HIGH);
        ticket.setStatus(TicketStatus.OPEN);

        assertEquals(1, ticketMapper.insert(ticket));
        assertNotNull(ticket.getId());
        assertTrue(ticket.getId() > 0);
        assertEquals(countBeforePreparation + 1, countTickets());

        mockMvc.perform(get("/api/tickets/{id}", ticket.getId()).principal(agentAuthentication()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.id").value(ticket.getId()))
                .andExpect(jsonPath("$.data.title").value("查询集成测试工单"))
                .andExpect(jsonPath("$.data.description").value("查询集成测试描述"))
                .andExpect(jsonPath("$.data.creatorName").value(insertedCreatorName))
                .andExpect(jsonPath("$.data.priority").value("HIGH"))
                .andExpect(jsonPath("$.data.status").value("OPEN"));

        assertEquals(countBeforePreparation + 1, countTickets());
        Ticket persistedTicket = ticketMapper.selectById(ticket.getId());
        assertNotNull(persistedTicket);
        assertEquals(insertedCreatorName, persistedTicket.getCreatorName());
    }

    @Test
    void shouldReturnNotFoundWithoutChangingDatabase() throws Exception {
        assertNull(ticketMapper.selectById(MISSING_TICKET_ID));
        long countBeforeRequest = countTickets();

        mockMvc.perform(get("/api/tickets/{id}", MISSING_TICKET_ID)
                        .principal(agentAuthentication()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40400))
                .andExpect(jsonPath("$.message").value("工单不存在"))
                .andExpect(jsonPath("$.data").value(nullValue()));

        assertEquals(countBeforeRequest, countTickets());
    }

    @Test
    void shouldRejectZeroIdWithoutChangingDatabase() throws Exception {
        long countBeforeRequest = countTickets();

        mockMvc.perform(get("/api/tickets/0").principal(agentAuthentication()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000))
                .andExpect(jsonPath("$.message").value("请求参数校验失败"));

        assertEquals(countBeforeRequest, countTickets());
    }

    @Test
    void shouldRejectNonNumericIdWithoutChangingDatabase() throws Exception {
        long countBeforeRequest = countTickets();

        mockMvc.perform(get("/api/tickets/abc").principal(agentAuthentication()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40002))
                .andExpect(jsonPath("$.message").value("请求参数格式错误"));

        assertEquals(countBeforeRequest, countTickets());
    }

    private long countTickets() {
        return ticketMapper.selectCount(new LambdaQueryWrapper<>());
    }

    private static LambdaQueryWrapper<Ticket> queryByCreatorName(String creatorName) {
        return new LambdaQueryWrapper<Ticket>().eq(Ticket::getCreatorName, creatorName);
    }

    private static JwtAuthenticationToken agentAuthentication() {
        Instant now = Instant.now();
        Jwt jwt = new Jwt(
                "query-integration-token",
                now,
                now.plusSeconds(300),
                Map.of("alg", "HS256"),
                Map.of(
                        "sub", "100",
                        "username", "query_agent",
                        "role", "AGENT",
                        "jti", UUID.randomUUID().toString()
                )
        );
        return new JwtAuthenticationToken(jwt, List.of(), jwt.getSubject());
    }
}
