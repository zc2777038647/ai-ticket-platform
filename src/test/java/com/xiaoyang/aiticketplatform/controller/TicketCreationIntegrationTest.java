package com.xiaoyang.aiticketplatform.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jayway.jsonpath.JsonPath;
import com.xiaoyang.aiticketplatform.entity.Ticket;
import com.xiaoyang.aiticketplatform.entity.UserAccount;
import com.xiaoyang.aiticketplatform.enums.TicketPriority;
import com.xiaoyang.aiticketplatform.enums.TicketStatus;
import com.xiaoyang.aiticketplatform.enums.UserRole;
import com.xiaoyang.aiticketplatform.mapper.TicketMapper;
import com.xiaoyang.aiticketplatform.mapper.UserAccountMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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

    @Autowired
    private UserAccountMapper userAccountMapper;

    private MockMvc mockMvc;
    private String cleanupPrefix;
    private Long authenticatedUserId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @AfterTransaction
    void verifyPreparedDataWasRolledBack() {
        if (cleanupPrefix != null) {
            assertEquals(0L, ticketMapper.selectCount(
                    new LambdaQueryWrapper<Ticket>()
                            .likeRight(Ticket::getCreatorName, cleanupPrefix)
            ));
            assertEquals(0L, userAccountMapper.selectCount(
                    new LambdaQueryWrapper<UserAccount>()
                            .likeRight(UserAccount::getUsername, cleanupPrefix)
            ));
        }
    }

    @Test
    void shouldCreateTicketThroughCompleteApplicationChain() throws Exception {
        String creatorName = uniqueCreatorName("success");
        String title = "P1 全链路测试工单";
        String description = "验证 Controller 到 MySQL 的完整创建链路";

        MvcResult mvcResult = mockMvc.perform(post("/api/tickets")
                        .principal(authentication())
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
                () -> assertEquals(authenticatedUserId, persistedTicket.getCreatorUserId()),
                () -> assertNull(persistedTicket.getAssigneeUserId()),
                () -> assertEquals(TicketPriority.HIGH, persistedTicket.getPriority()),
                () -> assertEquals(TicketStatus.OPEN, persistedTicket.getStatus()),
                () -> assertNotNull(persistedTicket.getCreatedAt()),
                () -> assertNotNull(persistedTicket.getUpdatedAt())
        );
    }

    @Test
    void shouldNotInsertTicketWhenValidationFails() throws Exception {
        String creatorName = uniqueCreatorName("invalid");
        assertEquals(0L, ticketMapper.selectCount(queryByCreatorName(creatorName)));

        mockMvc.perform(post("/api/tickets")
                        .principal(authentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson("   ", "测试描述", creatorName, "HIGH")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000))
                .andExpect(jsonPath("$.data.title").value("标题不能为空"));

        assertEquals(0L, ticketMapper.selectCount(queryByCreatorName(creatorName)));
    }

    @Test
    void shouldNotInsertTicketWhenPriorityIsUnknown() throws Exception {
        String creatorName = uniqueCreatorName("enum");
        assertEquals(0L, ticketMapper.selectCount(queryByCreatorName(creatorName)));

        mockMvc.perform(post("/api/tickets")
                        .principal(authentication())
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

    private String uniqueCreatorName(String scenario) {
        return prefix() + scenario;
    }

    private JwtAuthenticationToken authentication() {
        UserAccount user = new UserAccount();
        user.setUsername(prefix() + "user");
        user.setPasswordHash("{test-only-hash}:ticket-creation");
        user.setDisplayName("创建工单集成测试用户");
        user.setRole(UserRole.USER);
        assertEquals(1, userAccountMapper.insert(user));
        assertNotNull(user.getId());
        authenticatedUserId = user.getId();

        Instant now = Instant.now();
        Jwt jwt = new Jwt(
                "test-token",
                now,
                now.plusSeconds(300),
                Map.of("alg", "HS256"),
                Map.of(
                        "sub", user.getId().toString(),
                        "username", user.getUsername(),
                        "role", "USER",
                        "jti", UUID.randomUUID().toString()
                )
        );
        return new JwtAuthenticationToken(jwt, List.of(), jwt.getSubject());
    }

    private String prefix() {
        if (cleanupPrefix == null) {
            cleanupPrefix = "p16_" + UUID.randomUUID().toString().replace("-", "") + "_";
        }
        return cleanupPrefix;
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
