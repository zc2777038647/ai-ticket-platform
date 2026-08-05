package com.xiaoyang.aiticketplatform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xiaoyang.aiticketplatform.dto.request.AssignTicketRequest;
import com.xiaoyang.aiticketplatform.dto.request.UpdateTicketStatusRequest;
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
import com.xiaoyang.aiticketplatform.service.TicketService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class TicketOperationAtomicityIntegrationTest {

    private static final String PASSWORD = "P7-3-2-atomicity-password";

    @Autowired
    private TicketService ticketService;

    @Autowired
    private TicketMapper ticketMapper;

    @Autowired
    private TicketOperationLogMapper ticketOperationLogMapper;

    @Autowired
    private UserAccountMapper userAccountMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private PlatformTransactionManager transactionManager;

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
    void shouldCommitStatusUpdateAndStatusLogTogether() {
        UserAccount agent = insertUser("status-agent", UserRole.AGENT);
        Ticket ticket = insertTicket("status-success", null);

        ticketService.updateTicketStatus(
                ticket.getId(),
                new UpdateTicketStatusRequest(TicketStatus.IN_PROGRESS),
                agent.getId()
        );

        Ticket persisted = ticketMapper.selectById(ticket.getId());
        TicketOperationLog log = singleLog(ticket.getId());
        assertAll(
                () -> assertEquals(TicketStatus.IN_PROGRESS, persisted.getStatus()),
                () -> assertEquals(TicketOperationType.STATUS_CHANGED, log.getOperationType()),
                () -> assertEquals(agent.getId(), log.getOperatorUserId()),
                () -> assertEquals("OPEN", log.getBeforeValue()),
                () -> assertEquals("IN_PROGRESS", log.getAfterValue()),
                () -> assertNotNull(log.getCreatedAt())
        );
    }

    @Test
    void shouldCommitAssignmentAndAssignmentLogTogether() {
        UserAccount admin = insertUser("assign-admin", UserRole.ADMIN);
        UserAccount agent = insertUser("assign-agent", UserRole.AGENT);
        Ticket ticket = insertTicket("assign-success", null);

        ticketService.assignTicket(
                ticket.getId(),
                new AssignTicketRequest(agent.getId()),
                admin.getId()
        );

        Ticket persisted = ticketMapper.selectById(ticket.getId());
        TicketOperationLog log = singleLog(ticket.getId());
        assertAll(
                () -> assertEquals(agent.getId(), persisted.getAssigneeUserId()),
                () -> assertEquals(TicketOperationType.ASSIGNEE_CHANGED, log.getOperationType()),
                () -> assertEquals(admin.getId(), log.getOperatorUserId()),
                () -> assertNull(log.getBeforeValue()),
                () -> assertEquals(agent.getId().toString(), log.getAfterValue())
        );
    }

    @Test
    void shouldRecordOldAndNewAssigneeWhenReassigning() {
        UserAccount admin = insertUser("reassign-admin", UserRole.ADMIN);
        UserAccount agentA = insertUser("reassign-agent-a", UserRole.AGENT);
        UserAccount agentB = insertUser("reassign-agent-b", UserRole.AGENT);
        Ticket ticket = insertTicket("reassign-success", null);

        ticketService.assignTicket(
                ticket.getId(),
                new AssignTicketRequest(agentA.getId()),
                admin.getId()
        );
        ticketService.assignTicket(
                ticket.getId(),
                new AssignTicketRequest(agentB.getId()),
                admin.getId()
        );

        List<TicketOperationLog> logs = logs(ticket.getId());
        assertAll(
                () -> assertEquals(2, logs.size()),
                () -> assertNull(logs.get(0).getBeforeValue()),
                () -> assertEquals(agentA.getId().toString(), logs.get(0).getAfterValue()),
                () -> assertEquals(agentA.getId().toString(), logs.get(1).getBeforeValue()),
                () -> assertEquals(agentB.getId().toString(), logs.get(1).getAfterValue()),
                () -> assertEquals(admin.getId(), logs.get(1).getOperatorUserId()),
                () -> assertEquals(agentB.getId(), ticketMapper.selectById(ticket.getId()).getAssigneeUserId())
        );
    }

    @Test
    void shouldRollBackStatusUpdateWhenOperationLogForeignKeyFails() {
        Ticket ticket = insertTicket("status-fk-failure", null);
        assertNull(userAccountMapper.selectById(Long.MAX_VALUE));

        assertThrows(
                DataIntegrityViolationException.class,
                () -> executeInNestedTransaction(() -> ticketService.updateTicketStatus(
                        ticket.getId(),
                        new UpdateTicketStatusRequest(TicketStatus.IN_PROGRESS),
                        Long.MAX_VALUE
                ))
        );

        assertEquals(TicketStatus.OPEN, ticketMapper.selectById(ticket.getId()).getStatus());
        assertEquals(0L, countLogs(ticket.getId()));
    }

    @Test
    void shouldRollBackAssignmentWhenOperationLogForeignKeyFails() {
        UserAccount targetAgent = insertUser("assignment-fk-target", UserRole.AGENT);
        Ticket ticket = insertTicket("assignment-fk-failure", null);
        assertNull(userAccountMapper.selectById(Long.MAX_VALUE));

        assertThrows(
                DataIntegrityViolationException.class,
                () -> executeInNestedTransaction(() -> ticketService.assignTicket(
                        ticket.getId(),
                        new AssignTicketRequest(targetAgent.getId()),
                        Long.MAX_VALUE
                ))
        );

        assertNull(ticketMapper.selectById(ticket.getId()).getAssigneeUserId());
        assertEquals(0L, countLogs(ticket.getId()));
    }

    private void executeInNestedTransaction(Runnable operation) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_NESTED);
        transactionTemplate.executeWithoutResult(status -> operation.run());
    }

    private UserAccount insertUser(String suffix, UserRole role) {
        UserAccount user = new UserAccount();
        user.setUsername(prefix() + suffix);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setDisplayName("P7-3-2 Atomic " + role.name());
        user.setRole(role);
        assertEquals(1, userAccountMapper.insert(user));
        assertNotNull(user.getId());
        assertTrue(user.getId() > 0);
        return user;
    }

    private Ticket insertTicket(String suffix, Long assigneeUserId) {
        Ticket ticket = new Ticket();
        ticket.setTitle(prefix() + suffix);
        ticket.setDescription("P7-3-2 操作日志事务原子性测试");
        ticket.setCreatorName(prefix() + "creator-" + suffix);
        ticket.setAssigneeUserId(assigneeUserId);
        ticket.setPriority(TicketPriority.HIGH);
        ticket.setStatus(TicketStatus.OPEN);
        assertEquals(1, ticketMapper.insert(ticket));
        assertNotNull(ticket.getId());
        preparedTicketIds.add(ticket.getId());
        return ticketMapper.selectById(ticket.getId());
    }

    private TicketOperationLog singleLog(Long ticketId) {
        List<TicketOperationLog> operationLogs = logs(ticketId);
        assertEquals(1, operationLogs.size());
        return operationLogs.getFirst();
    }

    private List<TicketOperationLog> logs(Long ticketId) {
        return ticketOperationLogMapper.selectList(
                new LambdaQueryWrapper<TicketOperationLog>()
                        .eq(TicketOperationLog::getTicketId, ticketId)
                        .orderByAsc(TicketOperationLog::getCreatedAt)
                        .orderByAsc(TicketOperationLog::getId)
        );
    }

    private long countLogs(Long ticketId) {
        return ticketOperationLogMapper.selectCount(
                new LambdaQueryWrapper<TicketOperationLog>()
                        .eq(TicketOperationLog::getTicketId, ticketId)
        );
    }

    private String prefix() {
        if (testPrefix == null) {
            testPrefix = "p732a_"
                    + UUID.randomUUID().toString().replace("-", "").substring(0, 18)
                    + "_";
        }
        return testPrefix;
    }
}
