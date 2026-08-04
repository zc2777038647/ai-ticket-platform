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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
class TicketCreationIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private TicketMapper ticketMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    void shouldCreateTicketThroughCompleteApplicationChain() throws Exception {
        String creatorName = uniqueCreatorName("p16-success-");
        String title = "P1 全链路测试工单";
        String description = "验证 Controller 到 MySQL 的完整创建链路";

        MvcResult mvcResult = mockMvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson(title, description, creatorName, "HIGH")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.title").value(title))
                .andExpect(jsonPath("$.data.description").value(description))
                .andExpect(jsonPath("$.data.creatorName").value(creatorName))
                .andExpect(jsonPath("$.data.priority").value("HIGH"))
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andReturn();

        List<Ticket> matchingTickets = ticketMapper.selectList(queryByCreatorName(creatorName));
        assertEquals(1, matchingTickets.size());
        Ticket persistedTicket = matchingTickets.getFirst();
        Number responseId = JsonPath.read(mvcResult.getResponse().getContentAsString(), "$.data.id");

        assertAll(
                () -> assertNotNull(persistedTicket.getId()),
                () -> assertTrue(persistedTicket.getId() > 0),
                () -> assertEquals(persistedTicket.getId().longValue(), responseId.longValue()),
                () -> assertEquals(title, persistedTicket.getTitle()),
                () -> assertEquals(description, persistedTicket.getDescription()),
                () -> assertEquals(creatorName, persistedTicket.getCreatorName()),
                () -> assertEquals(TicketPriority.HIGH, persistedTicket.getPriority()),
                () -> assertEquals(TicketStatus.OPEN, persistedTicket.getStatus()),
                () -> assertNotNull(persistedTicket.getCreatedAt()),
                () -> assertNotNull(persistedTicket.getUpdatedAt())
        );
    }

    @Test
    void shouldNotInsertTicketWhenValidationFails() throws Exception {
        String creatorName = uniqueCreatorName("p16-invalid-");
        assertEquals(0L, ticketMapper.selectCount(queryByCreatorName(creatorName)));

        mockMvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson("   ", "测试描述", creatorName, "HIGH")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000))
                .andExpect(jsonPath("$.data.title").value("标题不能为空"));

        assertEquals(0L, ticketMapper.selectCount(queryByCreatorName(creatorName)));
    }

    @Test
    void shouldNotInsertTicketWhenPriorityIsUnknown() throws Exception {
        String creatorName = uniqueCreatorName("p16-enum-");
        assertEquals(0L, ticketMapper.selectCount(queryByCreatorName(creatorName)));

        mockMvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson("测试工单", "测试描述", creatorName, "UNKNOWN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value("请求体格式错误"));

        assertEquals(0L, ticketMapper.selectCount(queryByCreatorName(creatorName)));
    }

    private static LambdaQueryWrapper<Ticket> queryByCreatorName(String creatorName) {
        return new LambdaQueryWrapper<Ticket>().eq(Ticket::getCreatorName, creatorName);
    }

    private static String uniqueCreatorName(String prefix) {
        return prefix + UUID.randomUUID();
    }

    private static String requestJson(
            String title,
            String description,
            String creatorName,
            String priority
    ) {
        return """
                {
                  "title": "%s",
                  "description": "%s",
                  "creatorName": "%s",
                  "priority": "%s"
                }
                """.formatted(title, description, creatorName, priority);
    }
}
