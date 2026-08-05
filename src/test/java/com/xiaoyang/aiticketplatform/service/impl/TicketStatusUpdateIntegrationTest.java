package com.xiaoyang.aiticketplatform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.xiaoyang.aiticketplatform.common.ErrorCode;
import com.xiaoyang.aiticketplatform.dto.request.UpdateTicketStatusRequest;
import com.xiaoyang.aiticketplatform.dto.response.TicketResponse;
import com.xiaoyang.aiticketplatform.entity.Ticket;
import com.xiaoyang.aiticketplatform.entity.TicketOperationLog;
import com.xiaoyang.aiticketplatform.entity.UserAccount;
import com.xiaoyang.aiticketplatform.enums.TicketOperationType;
import com.xiaoyang.aiticketplatform.enums.TicketPriority;
import com.xiaoyang.aiticketplatform.enums.TicketStatus;
import com.xiaoyang.aiticketplatform.enums.UserRole;
import com.xiaoyang.aiticketplatform.exception.BusinessException;
import com.xiaoyang.aiticketplatform.mapper.TicketMapper;
import com.xiaoyang.aiticketplatform.mapper.TicketOperationLogMapper;
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
class TicketStatusUpdateIntegrationTest {

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

    private String cleanupCreatorName;
    private UserAccount operator;

    @AfterTransaction
    void verifyPreparedTicketWasRolledBack() {
        if (cleanupCreatorName != null) {
            assertEquals(0L, ticketMapper.selectCount(new LambdaQueryWrapper<Ticket>()
                    .eq(Ticket::getCreatorName, cleanupCreatorName)));
            assertEquals(0L, userAccountMapper.selectCount(new LambdaQueryWrapper<UserAccount>()
                    .eq(UserAccount::getUsername, cleanupCreatorName)));
        }
    }

    @Test
    void shouldCompleteValidStatusChainThroughRealMySql() {
        Ticket ticket = insertUniqueTicket("完整状态链");

        assertStatusUpdate(ticket, TicketStatus.IN_PROGRESS);
        assertStatusUpdate(ticket, TicketStatus.RESOLVED);
        assertStatusUpdate(ticket, TicketStatus.CLOSED);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> ticketService.updateTicketStatus(
                        ticket.getId(),
                        new UpdateTicketStatusRequest(TicketStatus.OPEN),
                        operatorId()
                )
        );

        assertEquals(ErrorCode.INVALID_TICKET_STATUS_TRANSITION, exception.getErrorCode());
        assertEquals(TicketStatus.CLOSED, ticketMapper.selectById(ticket.getId()).getStatus());
        assertEquals(3L, countLogs(ticket.getId()));
    }

    @Test
    void shouldRejectSkippedStatusAndKeepDatabaseStatusOpen() {
        Ticket ticket = insertUniqueTicket("跳级状态更新");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> ticketService.updateTicketStatus(
                        ticket.getId(),
                        new UpdateTicketStatusRequest(TicketStatus.RESOLVED),
                        operatorId()
                )
        );

        assertEquals(ErrorCode.INVALID_TICKET_STATUS_TRANSITION, exception.getErrorCode());
        assertEquals(TicketStatus.OPEN, ticketMapper.selectById(ticket.getId()).getStatus());
        assertEquals(0L, countLogs(ticket.getId()));
    }

    @Test
    void shouldReportNotFoundWithoutCreatingOrUpdatingTicket() {
        long missingId = Long.MAX_VALUE;
        assertNull(ticketMapper.selectById(missingId));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> ticketService.updateTicketStatus(
                        missingId,
                        new UpdateTicketStatusRequest(TicketStatus.IN_PROGRESS),
                        operatorId()
                )
        );

        assertEquals(ErrorCode.TICKET_NOT_FOUND, exception.getErrorCode());
        assertNull(ticketMapper.selectById(missingId));
    }

    @Test
    void shouldRejectStaleCurrentStatusThroughRealConditionalUpdates() {
        Ticket ticket = insertUniqueTicket("真实条件更新");

        int firstAffectedRows = ticketMapper.update(
                null,
                statusUpdate(ticket.getId(), TicketStatus.OPEN, TicketStatus.IN_PROGRESS)
        );
        Ticket afterFirstUpdate = ticketMapper.selectById(ticket.getId());

        int staleAffectedRows = ticketMapper.update(
                null,
                statusUpdate(ticket.getId(), TicketStatus.OPEN, TicketStatus.RESOLVED)
        );
        Ticket afterStaleUpdate = ticketMapper.selectById(ticket.getId());

        assertAll(
                () -> assertEquals(1, firstAffectedRows),
                () -> assertEquals(TicketStatus.IN_PROGRESS, afterFirstUpdate.getStatus()),
                () -> assertEquals(0, staleAffectedRows),
                () -> assertEquals(TicketStatus.IN_PROGRESS, afterStaleUpdate.getStatus())
        );
    }

    private Ticket insertUniqueTicket(String scenario) {
        cleanupCreatorName = "status-update-" + UUID.randomUUID();
        Ticket ticket = new Ticket();
        ticket.setTitle(scenario + "-" + cleanupCreatorName);
        ticket.setDescription("验证状态流转、条件更新与冲突处理");
        ticket.setCreatorName(cleanupCreatorName);
        ticket.setPriority(TicketPriority.HIGH);
        ticket.setStatus(TicketStatus.OPEN);

        assertEquals(1, ticketMapper.insert(ticket));
        assertNotNull(ticket.getId());
        assertTrue(ticket.getId() > 0);
        return ticket;
    }

    private void assertStatusUpdate(Ticket originalTicket, TicketStatus targetStatus) {
        TicketResponse response = ticketService.updateTicketStatus(
                originalTicket.getId(),
                new UpdateTicketStatusRequest(targetStatus),
                operatorId()
        );
        Ticket persistedTicket = ticketMapper.selectById(originalTicket.getId());
        TicketOperationLog latestLog = ticketOperationLogMapper.selectOne(
                new LambdaQueryWrapper<TicketOperationLog>()
                        .eq(TicketOperationLog::getTicketId, originalTicket.getId())
                        .eq(TicketOperationLog::getAfterValue, targetStatus.name())
        );

        assertAll(
                () -> assertEquals(originalTicket.getId(), response.id()),
                () -> assertEquals(originalTicket.getTitle(), response.title()),
                () -> assertEquals(originalTicket.getDescription(), response.description()),
                () -> assertEquals(originalTicket.getCreatorName(), response.creatorName()),
                () -> assertEquals(originalTicket.getPriority(), response.priority()),
                () -> assertEquals(targetStatus, response.status()),
                () -> assertEquals(originalTicket.getId(), persistedTicket.getId()),
                () -> assertEquals(originalTicket.getTitle(), persistedTicket.getTitle()),
                () -> assertEquals(originalTicket.getDescription(), persistedTicket.getDescription()),
                () -> assertEquals(originalTicket.getCreatorName(), persistedTicket.getCreatorName()),
                () -> assertEquals(originalTicket.getPriority(), persistedTicket.getPriority()),
                () -> assertEquals(targetStatus, persistedTicket.getStatus())
                , () -> assertNotNull(latestLog)
                , () -> assertEquals(operatorId(), latestLog.getOperatorUserId())
                , () -> assertEquals(TicketOperationType.STATUS_CHANGED, latestLog.getOperationType())
                , () -> assertEquals(originalTicket.getStatus().name(), latestLog.getBeforeValue())
                , () -> assertEquals(targetStatus.name(), latestLog.getAfterValue())
        );

        originalTicket.setStatus(targetStatus);
    }

    private Long operatorId() {
        if (operator == null) {
            if (cleanupCreatorName == null) {
                cleanupCreatorName = "status-update-" + UUID.randomUUID();
            }
            operator = new UserAccount();
            operator.setUsername(cleanupCreatorName);
            operator.setPasswordHash(passwordEncoder.encode("P7-3-2-status-test-password"));
            operator.setDisplayName("P7-3-2 状态操作者");
            operator.setRole(UserRole.AGENT);
            assertEquals(1, userAccountMapper.insert(operator));
            assertNotNull(operator.getId());
        }
        return operator.getId();
    }

    private long countLogs(Long ticketId) {
        return ticketOperationLogMapper.selectCount(
                new LambdaQueryWrapper<TicketOperationLog>()
                        .eq(TicketOperationLog::getTicketId, ticketId)
        );
    }

    private static LambdaUpdateWrapper<Ticket> statusUpdate(
            Long id,
            TicketStatus currentStatus,
            TicketStatus targetStatus
    ) {
        return Wrappers.lambdaUpdate(Ticket.class)
                .eq(Ticket::getId, id)
                .eq(Ticket::getStatus, currentStatus)
                .set(Ticket::getStatus, targetStatus);
    }
}
