package com.xiaoyang.aiticketplatform.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jayway.jsonpath.JsonPath;
import com.xiaoyang.aiticketplatform.entity.Ticket;
import com.xiaoyang.aiticketplatform.enums.TicketPriority;
import com.xiaoyang.aiticketplatform.enums.TicketStatus;
import com.xiaoyang.aiticketplatform.mapper.TicketMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
class TicketPaginationIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private TicketMapper ticketMapper;

    private MockMvc mockMvc;
    private String cleanupToken;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @AfterTransaction
    void verifyPreparedTicketsWereRolledBack() {
        if (cleanupToken != null) {
            assertEquals(0L, countPreparedTickets());
        }
    }

    @Test
    void shouldFilterPaginateAndOrderTicketsThroughCompleteHttpChain() throws Exception {
        cleanupToken = UUID.randomUUID().toString();
        String creatorName = "http-page-" + cleanupToken;
        String otherCreatorName = "http-other-" + cleanupToken;
        String keywordToken = "http-key-" + cleanupToken;
        LocalDateTime laterTime = LocalDateTime.of(2031, 6, 1, 12, 0);
        LocalDateTime earlierTime = LocalDateTime.of(2031, 5, 1, 12, 0);

        assertEquals(0L, countPreparedTickets());
        Ticket matchByTitleLater = insertTicket(
                keywordToken + " 标题命中",
                "普通描述",
                creatorName,
                TicketPriority.HIGH,
                TicketStatus.OPEN,
                laterTime
        );
        Ticket matchByDescriptionEarlier = insertTicket(
                "普通标题一",
                "描述命中 " + keywordToken,
                creatorName,
                TicketPriority.HIGH,
                TicketStatus.OPEN,
                earlierTime
        );
        Ticket matchByTitleSameTime = insertTicket(
                keywordToken + " 同时间标题命中",
                "普通描述",
                creatorName,
                TicketPriority.HIGH,
                TicketStatus.OPEN,
                earlierTime
        );
        Ticket excludedByStatus = insertTicket(
                "普通标题二",
                "已解决但描述命中 " + keywordToken,
                creatorName,
                TicketPriority.HIGH,
                TicketStatus.RESOLVED,
                laterTime
        );
        Ticket excludedByPriority = insertTicket(
                keywordToken + " 低优先级",
                "普通描述",
                creatorName,
                TicketPriority.LOW,
                TicketStatus.OPEN,
                laterTime
        );
        Ticket excludedByCreator = insertTicket(
                keywordToken + " 其他创建人",
                "普通描述",
                otherCreatorName,
                TicketPriority.HIGH,
                TicketStatus.OPEN,
                laterTime
        );
        Ticket excludedByKeyword = insertTicket(
                "无关标题",
                "无关描述",
                creatorName,
                TicketPriority.HIGH,
                TicketStatus.OPEN,
                laterTime
        );
        assertEquals(7L, countPreparedTickets());

        MvcResult firstResult = performPageRequest(1, creatorName, keywordToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.pages").value(2))
                .andExpect(jsonPath("$.data.current").value(1))
                .andExpect(jsonPath("$.data.size").value(2))
                .andExpect(jsonPath("$.data.records.length()").value(2))
                .andExpect(jsonPath("$.data.records[0].id").value(matchByTitleLater.getId()))
                .andExpect(jsonPath("$.data.records[1].id").value(matchByTitleSameTime.getId()))
                .andReturn();

        MvcResult secondResult = performPageRequest(2, creatorName, keywordToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.pages").value(2))
                .andExpect(jsonPath("$.data.current").value(2))
                .andExpect(jsonPath("$.data.size").value(2))
                .andExpect(jsonPath("$.data.records.length()").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(matchByDescriptionEarlier.getId()))
                .andReturn();

        List<Long> firstPageIds = responseIds(firstResult);
        List<Long> secondPageIds = responseIds(secondResult);
        Set<Long> returnedIds = new HashSet<>(firstPageIds);
        assertTrue(returnedIds.addAll(secondPageIds));
        assertEquals(3, returnedIds.size());
        assertFalse(returnedIds.contains(excludedByStatus.getId()));
        assertFalse(returnedIds.contains(excludedByPriority.getId()));
        assertFalse(returnedIds.contains(excludedByCreator.getId()));
        assertFalse(returnedIds.contains(excludedByKeyword.getId()));
        assertEquals(7L, countPreparedTickets());
    }

    @Test
    void shouldRejectInvalidPaginationParametersWithoutChangingDatabase() throws Exception {
        long countBeforeRequests = countTickets();

        mockMvc.perform(get("/api/tickets").param("page", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000))
                .andExpect(jsonPath("$.message").value("请求参数校验失败"))
                .andExpect(jsonPath("$.data.page").value("页码必须大于等于1"));

        mockMvc.perform(get("/api/tickets").param("status", "UNKNOWN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40002))
                .andExpect(jsonPath("$.message").value("请求参数格式错误"))
                .andExpect(jsonPath("$.data").value(nullValue()));

        assertEquals(countBeforeRequests, countTickets());
    }

    private org.springframework.test.web.servlet.ResultActions performPageRequest(
            int page,
            String creatorName,
            String keywordToken
    ) throws Exception {
        return mockMvc.perform(get("/api/tickets")
                .param("page", Integer.toString(page))
                .param("size", "2")
                .param("status", "OPEN")
                .param("priority", "HIGH")
                .param("creatorName", "  " + creatorName + "  ")
                .param("keyword", "  " + keywordToken + "  "));
    }

    private Ticket insertTicket(
            String title,
            String description,
            String creatorName,
            TicketPriority priority,
            TicketStatus status,
            LocalDateTime timestamp
    ) {
        Ticket ticket = new Ticket();
        ticket.setTitle(title);
        ticket.setDescription(description);
        ticket.setCreatorName(creatorName);
        ticket.setPriority(priority);
        ticket.setStatus(status);
        ticket.setCreatedAt(timestamp);
        ticket.setUpdatedAt(timestamp);

        assertEquals(1, ticketMapper.insert(ticket));
        assertNotNull(ticket.getId());
        assertTrue(ticket.getId() > 0);
        return ticket;
    }

    private long countPreparedTickets() {
        return ticketMapper.selectCount(new LambdaQueryWrapper<Ticket>()
                .like(Ticket::getCreatorName, cleanupToken)
                .or()
                .like(Ticket::getTitle, cleanupToken)
                .or()
                .like(Ticket::getDescription, cleanupToken));
    }

    private long countTickets() {
        return ticketMapper.selectCount(new LambdaQueryWrapper<>());
    }

    private static List<Long> responseIds(MvcResult result) throws Exception {
        List<Number> ids = JsonPath.read(
                result.getResponse().getContentAsString(),
                "$.data.records[*].id"
        );
        return ids.stream().map(Number::longValue).toList();
    }
}
