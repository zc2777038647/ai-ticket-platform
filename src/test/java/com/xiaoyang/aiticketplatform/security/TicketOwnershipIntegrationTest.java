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
import org.junit.jupiter.api.BeforeEach;
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

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
class TicketOwnershipIntegrationTest {

    private static final String PASSWORD = "P6_3_test_password";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserAccountMapper userAccountMapper;

    @Autowired
    private TicketMapper ticketMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String testPrefix;
    private AccountSession userA;
    private AccountSession userB;
    private AccountSession agent;
    private AccountSession admin;
    private Ticket userATicketOne;
    private Ticket userATicketTwo;
    private Ticket userBTicket;
    private Ticket agentTicket;
    private Ticket adminTicket;
    private Ticket historicalTicket;
    private String sharedCreatorName;
    private String uniqueKeyword;

    @BeforeEach
    void prepareRealAccountsAndTickets() throws Exception {
        testPrefix = "p63_" + UUID.randomUUID().toString().replace("-", "") + "_";
        userA = createSession("user_a", UserRole.USER);
        userB = createSession("user_b", UserRole.USER);
        agent = createSession("agent", UserRole.AGENT);
        admin = createSession("admin", UserRole.ADMIN);

        sharedCreatorName = testPrefix + "相同展示名称";
        uniqueKeyword = testPrefix + "独有关键词";
        userATicketOne = createTicket(
                userA.token(),
                uniqueKeyword + "-USER_A工单1",
                "USER_A筛选描述",
                sharedCreatorName,
                TicketPriority.HIGH
        );
        userATicketTwo = createTicket(
                userA.token(),
                testPrefix + "USER_A工单2",
                "USER_A普通描述",
                testPrefix + "USER_A展示名",
                TicketPriority.LOW
        );
        userBTicket = createTicket(
                userB.token(),
                testPrefix + "USER_B工单1",
                uniqueKeyword + "-其他用户也命中关键词",
                sharedCreatorName,
                TicketPriority.HIGH
        );
        agentTicket = createTicket(
                agent.token(),
                testPrefix + "AGENT工单1",
                "AGENT描述",
                testPrefix + "AGENT展示名",
                TicketPriority.HIGH
        );
        adminTicket = createTicket(
                admin.token(),
                testPrefix + "ADMIN工单1",
                "ADMIN描述",
                testPrefix + "ADMIN展示名",
                TicketPriority.HIGH
        );
        historicalTicket = insertHistoricalTicket();

        assertEquals(userA.user().getId(), userATicketOne.getCreatorUserId());
        assertEquals(userA.user().getId(), userATicketTwo.getCreatorUserId());
        assertEquals(userB.user().getId(), userBTicket.getCreatorUserId());
        assertEquals(agent.user().getId(), agentTicket.getCreatorUserId());
        assertEquals(admin.user().getId(), adminTicket.getCreatorUserId());
    }

    @AfterTransaction
    void verifyAllPreparedDataWasRolledBack() {
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
    void shouldIsolateMyTicketsByAuthenticatedCreatorAndKeepFiltersGrouped() throws Exception {
        long countBeforeRequests = countPreparedTickets();

        assertMineContainsOnly(userA.token(), List.of(userATicketOne, userATicketTwo));
        assertMineContainsOnly(userB.token(), List.of(userBTicket));
        assertMineContainsOnly(agent.token(), List.of(agentTicket));

        mockMvc.perform(get("/api/tickets/mine")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userA.token()))
                        .param("page", "1")
                        .param("size", "20")
                        .param("status", "OPEN")
                        .param("priority", "HIGH")
                        .param("creatorName", "  " + sharedCreatorName + "  ")
                        .param("keyword", "  " + uniqueKeyword + "  "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records.length()").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(userATicketOne.getId()))
                .andExpect(content().string(not(containsString("\"creatorUserId\""))));

        assertEquals(countBeforeRequests, countPreparedTickets());
    }

    @Test
    void shouldEnforceDetailOwnershipWithoutRevealingHiddenResources() throws Exception {
        long countBeforeRequests = countPreparedTickets();

        assertDetailsAllowed(userA.token(), userATicketOne);
        assertDetailsHidden(userA.token(), userBTicket);
        assertDetailsHidden(userA.token(), historicalTicket);

        assertDetailsAllowed(agent.token(), userATicketOne);
        assertDetailsAllowed(agent.token(), userBTicket);
        assertDetailsAllowed(agent.token(), historicalTicket);
        assertDetailsAllowed(admin.token(), userBTicket);
        assertDetailsAllowed(admin.token(), historicalTicket);

        assertEquals(countBeforeRequests, countPreparedTickets());
    }

    @Test
    void shouldKeepAuthenticationAndRoleBoundariesAndDatabaseUnchanged() throws Exception {
        long countBeforeRequests = countPreparedTickets();

        assertUnauthorized(get("/api/tickets/mine").param("page", "1").param("size", "20"));
        assertUnauthorized(get("/api/tickets/{id}", userATicketOne.getId()));

        mockMvc.perform(get("/api/tickets")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userA.token()))
                        .param("page", "1")
                        .param("size", "20"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));

        mockMvc.perform(patch("/api/tickets/{id}/status", userATicketOne.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(userA.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"IN_PROGRESS"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));

        assertEquals(TicketStatus.OPEN, ticketMapper.selectById(userATicketOne.getId()).getStatus());
        assertEquals(countBeforeRequests, countPreparedTickets());
    }

    private void assertMineContainsOnly(String token, List<Ticket> expectedTickets) throws Exception {
        List<Ticket> expectedOrder = new ArrayList<>(expectedTickets);
        expectedOrder.sort((left, right) -> {
            int createdAtComparison = right.getCreatedAt().compareTo(left.getCreatedAt());
            return createdAtComparison != 0
                    ? createdAtComparison
                    : right.getId().compareTo(left.getId());
        });

        MvcResult result = mockMvc.perform(get("/api/tickets/mine")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .param("page", "1")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.total").value(expectedTickets.size()))
                .andExpect(jsonPath("$.data.records.length()").value(expectedTickets.size()))
                .andExpect(content().string(not(containsString("\"creatorUserId\""))))
                .andReturn();

        List<Number> responseIds = JsonPath.read(
                result.getResponse().getContentAsString(),
                "$.data.records[*].id"
        );
        assertEquals(
                expectedOrder.stream().map(Ticket::getId).toList(),
                responseIds.stream().map(Number::longValue).toList()
        );
    }

    private void assertDetailsAllowed(String token, Ticket ticket) throws Exception {
        mockMvc.perform(get("/api/tickets/{id}", ticket.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(ticket.getId()))
                .andExpect(jsonPath("$.data.title").value(ticket.getTitle()))
                .andExpect(content().string(not(containsString("\"creatorUserId\""))));
    }

    private void assertDetailsHidden(String token, Ticket ticket) throws Exception {
        mockMvc.perform(get("/api/tickets/{id}", ticket.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40400))
                .andExpect(jsonPath("$.message").value("工单不存在"))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(content().string(not(containsString(ticket.getTitle()))))
                .andExpect(content().string(not(containsString(ticket.getCreatorName()))));
    }

    private void assertUnauthorized(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        mockMvc.perform(request)
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, containsString("Bearer")))
                .andExpect(jsonPath("$.code").value(40101))
                .andExpect(jsonPath("$.message").value("请先登录或提供有效访问令牌"))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    private AccountSession createSession(String suffix, UserRole role) throws Exception {
        UserAccount user = new UserAccount();
        user.setUsername(testPrefix + suffix);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setDisplayName("P6-3" + role.name());
        user.setRole(role);
        assertEquals(1, userAccountMapper.insert(user));
        assertNotNull(user.getId());
        assertTrue(passwordEncoder.matches(PASSWORD, user.getPasswordHash()));

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

    private Ticket createTicket(
            String token,
            String title,
            String description,
            String creatorName,
            TicketPriority priority
    ) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/tickets")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ticketJson(title, description, creatorName, priority)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
        Ticket ticket = ticketMapper.selectById(id.longValue());
        assertNotNull(ticket);
        assertNotNull(ticket.getCreatorUserId());
        return ticket;
    }

    private Ticket insertHistoricalTicket() {
        Ticket ticket = new Ticket();
        ticket.setTitle(testPrefix + "历史NULL工单");
        ticket.setDescription("历史兼容数据");
        ticket.setCreatorName(testPrefix + "历史展示名");
        ticket.setPriority(TicketPriority.HIGH);
        ticket.setStatus(TicketStatus.OPEN);
        assertEquals(1, ticketMapper.insert(ticket));
        assertNotNull(ticket.getId());
        Ticket persisted = ticketMapper.selectById(ticket.getId());
        assertNotNull(persisted);
        assertNull(persisted.getCreatorUserId());
        return persisted;
    }

    private long countPreparedTickets() {
        return ticketMapper.selectCount(new LambdaQueryWrapper<Ticket>()
                .likeRight(Ticket::getCreatorName, testPrefix));
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static String loginJson(String username) {
        return """
                {
                  "username": "%s",
                  "password": "%s"
                }
                """.formatted(username, PASSWORD);
    }

    private static String ticketJson(
            String title,
            String description,
            String creatorName,
            TicketPriority priority
    ) {
        return """
                {
                  "title": "%s",
                  "description": "%s",
                  "creatorName": "%s",
                  "priority": "%s"
                }
                """.formatted(title, description, creatorName, priority.name());
    }

    private record AccountSession(UserAccount user, String token) {
    }
}
