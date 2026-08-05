package com.xiaoyang.aiticketplatform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.xiaoyang.aiticketplatform.common.ErrorCode;
import com.xiaoyang.aiticketplatform.dto.request.AssignTicketRequest;
import com.xiaoyang.aiticketplatform.dto.response.TicketAssignmentResponse;
import com.xiaoyang.aiticketplatform.entity.Ticket;
import com.xiaoyang.aiticketplatform.entity.UserAccount;
import com.xiaoyang.aiticketplatform.enums.TicketPriority;
import com.xiaoyang.aiticketplatform.enums.TicketStatus;
import com.xiaoyang.aiticketplatform.enums.UserRole;
import com.xiaoyang.aiticketplatform.exception.BusinessException;
import com.xiaoyang.aiticketplatform.mapper.TicketMapper;
import com.xiaoyang.aiticketplatform.mapper.UserAccountMapper;
import com.xiaoyang.aiticketplatform.service.TicketService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class TicketAssignmentIntegrationTest {

    private static final String PASSWORD = "P7_2_service_test_password";

    @Autowired
    private TicketService ticketService;

    @Autowired
    private TicketMapper ticketMapper;

    @Autowired
    private UserAccountMapper userAccountMapper;

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
    void shouldAssignUnassignedTicketThroughRealServiceAndMySql() {
        UserAccount creator = insertUser("creator", UserRole.USER);
        UserAccount agent = insertUser("agent", UserRole.AGENT);
        Ticket ticket = insertTicket("first", creator.getId(), null);

        TicketAssignmentResponse response = ticketService.assignTicket(
                ticket.getId(),
                new AssignTicketRequest(agent.getId())
        );

        Ticket persisted = ticketMapper.selectById(ticket.getId());
        assertAll(
                () -> assertEquals(ticket.getId(), response.ticketId()),
                () -> assertEquals(agent.getId(), response.assigneeUserId()),
                () -> assertEquals(agent.getUsername(), response.assigneeUsername()),
                () -> assertEquals(agent.getDisplayName(), response.assigneeDisplayName()),
                () -> assertEquals(agent.getId(), persisted.getAssigneeUserId()),
                () -> assertEquals(ticket.getTitle(), persisted.getTitle()),
                () -> assertEquals(ticket.getDescription(), persisted.getDescription()),
                () -> assertEquals(ticket.getCreatorName(), persisted.getCreatorName()),
                () -> assertEquals(ticket.getCreatorUserId(), persisted.getCreatorUserId()),
                () -> assertEquals(TicketPriority.HIGH, persisted.getPriority()),
                () -> assertEquals(TicketStatus.OPEN, persisted.getStatus())
        );
    }

    @Test
    void shouldReassignTicketBetweenAgentsThroughRealServiceAndMySql() {
        UserAccount creator = insertUser("creator", UserRole.USER);
        UserAccount agentA = insertUser("agent_a", UserRole.AGENT);
        UserAccount agentB = insertUser("agent_b", UserRole.AGENT);
        Ticket ticket = insertTicket("reassign", creator.getId(), null);

        ticketService.assignTicket(ticket.getId(), new AssignTicketRequest(agentA.getId()));
        TicketAssignmentResponse response = ticketService.assignTicket(
                ticket.getId(),
                new AssignTicketRequest(agentB.getId())
        );

        Ticket persisted = ticketMapper.selectById(ticket.getId());
        assertAll(
                () -> assertEquals(agentB.getId(), response.assigneeUserId()),
                () -> assertEquals(agentB.getUsername(), response.assigneeUsername()),
                () -> assertEquals(agentB.getDisplayName(), response.assigneeDisplayName()),
                () -> assertEquals(agentB.getId(), persisted.getAssigneeUserId()),
                () -> assertEquals(TicketStatus.OPEN, persisted.getStatus())
        );
    }

    @Test
    void shouldRejectNonAgentAndKeepRealDatabaseUnassigned() {
        UserAccount creator = insertUser("creator", UserRole.USER);
        UserAccount targetUser = insertUser("target_user", UserRole.USER);
        Ticket ticket = insertTicket("invalid-role", creator.getId(), null);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> ticketService.assignTicket(
                        ticket.getId(),
                        new AssignTicketRequest(targetUser.getId())
                )
        );

        assertEquals(ErrorCode.INVALID_ASSIGNEE_ROLE, exception.getErrorCode());
        assertNull(ticketMapper.selectById(ticket.getId()).getAssigneeUserId());
    }

    @Test
    void shouldRejectMissingTargetAndKeepRealDatabaseUnassigned() {
        UserAccount creator = insertUser("creator", UserRole.USER);
        Ticket ticket = insertTicket("missing-target", creator.getId(), null);
        assertNull(userAccountMapper.selectById(Long.MAX_VALUE));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> ticketService.assignTicket(
                        ticket.getId(),
                        new AssignTicketRequest(Long.MAX_VALUE)
                )
        );

        assertEquals(ErrorCode.ASSIGNEE_NOT_FOUND, exception.getErrorCode());
        assertNull(ticketMapper.selectById(ticket.getId()).getAssigneeUserId());
    }

    @Test
    void shouldRejectStaleNullAndNonNullAssigneeConditionsInRealMySql() {
        UserAccount creator = insertUser("creator", UserRole.USER);
        UserAccount agentA = insertUser("agent_a", UserRole.AGENT);
        UserAccount agentB = insertUser("agent_b", UserRole.AGENT);
        UserAccount agentC = insertUser("agent_c", UserRole.AGENT);
        Ticket ticket = insertTicket("stale-condition", creator.getId(), null);

        int firstAssignment = ticketMapper.update(
                null,
                assignmentUpdate(ticket.getId(), null, agentA.getId())
        );
        int staleNullAssignment = ticketMapper.update(
                null,
                assignmentUpdate(ticket.getId(), null, agentB.getId())
        );
        int validReassignment = ticketMapper.update(
                null,
                assignmentUpdate(ticket.getId(), agentA.getId(), agentB.getId())
        );
        int staleNonNullAssignment = ticketMapper.update(
                null,
                assignmentUpdate(ticket.getId(), agentA.getId(), agentC.getId())
        );

        Ticket persisted = ticketMapper.selectById(ticket.getId());
        assertAll(
                () -> assertEquals(1, firstAssignment),
                () -> assertEquals(0, staleNullAssignment),
                () -> assertEquals(1, validReassignment),
                () -> assertEquals(0, staleNonNullAssignment),
                () -> assertEquals(agentB.getId(), persisted.getAssigneeUserId()),
                () -> assertEquals(TicketStatus.OPEN, persisted.getStatus())
        );
    }

    private UserAccount insertUser(String suffix, UserRole role) {
        UserAccount user = new UserAccount();
        user.setUsername(prefix() + suffix);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setDisplayName("P7-2 Service " + role.name());
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
        ticket.setDescription("P7-2真实指派Service测试");
        ticket.setCreatorName(prefix() + "creator-" + scenario);
        ticket.setCreatorUserId(creatorUserId);
        ticket.setAssigneeUserId(assigneeUserId);
        ticket.setPriority(TicketPriority.HIGH);
        ticket.setStatus(TicketStatus.OPEN);
        assertEquals(1, ticketMapper.insert(ticket));
        assertNotNull(ticket.getId());
        assertTrue(ticket.getId() > 0);
        return ticketMapper.selectById(ticket.getId());
    }

    private static LambdaUpdateWrapper<Ticket> assignmentUpdate(
            Long ticketId,
            Long currentAssigneeUserId,
            Long targetAssigneeUserId
    ) {
        LambdaUpdateWrapper<Ticket> wrapper = Wrappers.lambdaUpdate(Ticket.class)
                .eq(Ticket::getId, ticketId)
                .set(Ticket::getAssigneeUserId, targetAssigneeUserId);
        return currentAssigneeUserId == null
                ? wrapper.isNull(Ticket::getAssigneeUserId)
                : wrapper.eq(Ticket::getAssigneeUserId, currentAssigneeUserId);
    }

    private String prefix() {
        if (testPrefix == null) {
            testPrefix = "p72s_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20) + "_";
        }
        return testPrefix;
    }
}
