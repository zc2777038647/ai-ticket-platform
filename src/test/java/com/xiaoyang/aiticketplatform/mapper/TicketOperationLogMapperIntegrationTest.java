package com.xiaoyang.aiticketplatform.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xiaoyang.aiticketplatform.entity.Ticket;
import com.xiaoyang.aiticketplatform.entity.TicketOperationLog;
import com.xiaoyang.aiticketplatform.entity.UserAccount;
import com.xiaoyang.aiticketplatform.enums.TicketOperationType;
import com.xiaoyang.aiticketplatform.enums.TicketPriority;
import com.xiaoyang.aiticketplatform.enums.TicketStatus;
import com.xiaoyang.aiticketplatform.enums.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class TicketOperationLogMapperIntegrationTest {

    private static final String PASSWORD = "P7_3_1_log_test_password";

    @Autowired
    private UserAccountMapper userAccountMapper;

    @Autowired
    private TicketMapper ticketMapper;

    @Autowired
    private TicketOperationLogMapper ticketOperationLogMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<Long> preparedTicketIds = new ArrayList<>();
    private String testPrefix;

    @AfterTransaction
    void verifyTestDataWasRolledBack() {
        if (!preparedTicketIds.isEmpty()) {
            assertEquals(0L, ticketOperationLogMapper.selectCount(
                    new LambdaQueryWrapper<TicketOperationLog>()
                            .in(TicketOperationLog::getTicketId, preparedTicketIds)
            ));
        }
        if (testPrefix != null) {
            assertEquals(0L, ticketMapper.selectCount(
                    new LambdaQueryWrapper<Ticket>()
                            .likeRight(Ticket::getCreatorName, testPrefix)
            ));
            assertEquals(0L, userAccountMapper.selectCount(
                    new LambdaQueryWrapper<UserAccount>()
                            .likeRight(UserAccount::getUsername, testPrefix)
            ));
        }
    }

    @Test
    void shouldInsertAndReadStatusChangeLog() {
        UserAccount creator = insertUser("creator", UserRole.USER);
        UserAccount operator = insertUser("agent", UserRole.AGENT);
        Ticket ticket = insertTicket("status", creator.getId());
        TicketOperationLog log = newLog(
                ticket.getId(),
                operator.getId(),
                TicketOperationType.STATUS_CHANGED,
                TicketStatus.OPEN.name(),
                TicketStatus.IN_PROGRESS.name()
        );

        assertEquals(1, ticketOperationLogMapper.insert(log));
        assertNotNull(log.getId());
        assertTrue(log.getId() > 0);

        TicketOperationLog persisted = ticketOperationLogMapper.selectById(log.getId());
        assertNotNull(persisted);
        assertAll(
                () -> assertEquals(log.getId(), persisted.getId()),
                () -> assertEquals(ticket.getId(), persisted.getTicketId()),
                () -> assertEquals(operator.getId(), persisted.getOperatorUserId()),
                () -> assertEquals(TicketOperationType.STATUS_CHANGED, persisted.getOperationType()),
                () -> assertEquals("OPEN", persisted.getBeforeValue()),
                () -> assertEquals("IN_PROGRESS", persisted.getAfterValue()),
                () -> assertNotNull(persisted.getCreatedAt())
        );
    }

    @Test
    void shouldInsertAndReadFirstAssignmentLog() {
        UserAccount creator = insertUser("creator", UserRole.USER);
        UserAccount operator = insertUser("admin", UserRole.ADMIN);
        UserAccount targetAgent = insertUser("target_agent", UserRole.AGENT);
        Ticket ticket = insertTicket("first-assignment", creator.getId());
        TicketOperationLog log = newLog(
                ticket.getId(),
                operator.getId(),
                TicketOperationType.ASSIGNEE_CHANGED,
                null,
                targetAgent.getId().toString()
        );

        assertEquals(1, ticketOperationLogMapper.insert(log));
        assertNotNull(log.getId());

        TicketOperationLog persisted = ticketOperationLogMapper.selectById(log.getId());
        assertNotNull(persisted);
        assertAll(
                () -> assertEquals(TicketOperationType.ASSIGNEE_CHANGED, persisted.getOperationType()),
                () -> assertNull(persisted.getBeforeValue()),
                () -> assertEquals(targetAgent.getId().toString(), persisted.getAfterValue()),
                () -> assertEquals(operator.getId(), persisted.getOperatorUserId()),
                () -> assertTrue(!operator.getId().equals(targetAgent.getId())),
                () -> assertNotNull(persisted.getCreatedAt())
        );
    }

    @Test
    void shouldQueryOnlyOneTicketTimelineWithStableSameMillisecondOrder() {
        UserAccount creator = insertUser("creator", UserRole.USER);
        UserAccount operator = insertUser("operator", UserRole.ADMIN);
        UserAccount targetAgent = insertUser("target_agent", UserRole.AGENT);
        Ticket ticket = insertTicket("timeline", creator.getId());
        Ticket otherTicket = insertTicket("other-timeline", creator.getId());
        LocalDateTime sameMillisecond = LocalDateTime.of(2026, 1, 1, 0, 0, 0, 123_000_000);

        TicketOperationLog first = newLog(
                ticket.getId(), operator.getId(), TicketOperationType.STATUS_CHANGED,
                "OPEN", "IN_PROGRESS"
        );
        TicketOperationLog second = newLog(
                ticket.getId(), operator.getId(), TicketOperationType.ASSIGNEE_CHANGED,
                null, targetAgent.getId().toString()
        );
        TicketOperationLog third = newLog(
                ticket.getId(), operator.getId(), TicketOperationType.STATUS_CHANGED,
                "IN_PROGRESS", "RESOLVED"
        );
        first.setCreatedAt(sameMillisecond);
        second.setCreatedAt(sameMillisecond);
        third.setCreatedAt(sameMillisecond);

        assertEquals(1, ticketOperationLogMapper.insert(first));
        assertEquals(1, ticketOperationLogMapper.insert(second));
        assertEquals(1, ticketOperationLogMapper.insert(third));
        assertEquals(1, ticketOperationLogMapper.insert(newLog(
                otherTicket.getId(), operator.getId(), TicketOperationType.STATUS_CHANGED,
                "OPEN", "IN_PROGRESS"
        )));

        List<TicketOperationLog> timeline = ticketOperationLogMapper.selectList(
                new LambdaQueryWrapper<TicketOperationLog>()
                        .eq(TicketOperationLog::getTicketId, ticket.getId())
                        .orderByAsc(TicketOperationLog::getCreatedAt)
                        .orderByAsc(TicketOperationLog::getId)
        );

        assertAll(
                () -> assertEquals(3, timeline.size()),
                () -> assertTrue(timeline.stream()
                        .allMatch(item -> ticket.getId().equals(item.getTicketId()))),
                () -> assertTrue(timeline.stream()
                        .allMatch(item -> sameMillisecond.equals(item.getCreatedAt()))),
                () -> assertEquals(
                        List.of(first.getId(), second.getId(), third.getId()),
                        timeline.stream().map(TicketOperationLog::getId).toList()
                ),
                () -> assertEquals(
                        List.of(
                                TicketOperationType.STATUS_CHANGED,
                                TicketOperationType.ASSIGNEE_CHANGED,
                                TicketOperationType.STATUS_CHANGED
                        ),
                        timeline.stream().map(TicketOperationLog::getOperationType).toList()
                )
        );
    }

    @Test
    void shouldRejectMissingTicketForeignKey() {
        UserAccount operator = insertUser("operator", UserRole.AGENT);
        assertNull(ticketMapper.selectById(Long.MAX_VALUE));
        TicketOperationLog log = newLog(
                Long.MAX_VALUE,
                operator.getId(),
                TicketOperationType.STATUS_CHANGED,
                "OPEN",
                "IN_PROGRESS"
        );

        assertThrows(
                DataIntegrityViolationException.class,
                () -> ticketOperationLogMapper.insert(log)
        );
    }

    @Test
    void shouldRejectMissingOperatorForeignKey() {
        UserAccount creator = insertUser("creator", UserRole.USER);
        Ticket ticket = insertTicket("missing-operator", creator.getId());
        assertNull(userAccountMapper.selectById(Long.MAX_VALUE));
        TicketOperationLog log = newLog(
                ticket.getId(),
                Long.MAX_VALUE,
                TicketOperationType.STATUS_CHANGED,
                "OPEN",
                "IN_PROGRESS"
        );

        assertThrows(
                DataIntegrityViolationException.class,
                () -> ticketOperationLogMapper.insert(log)
        );
    }

    @Test
    void shouldRejectNullOperationType() {
        UserAccount creator = insertUser("creator", UserRole.USER);
        UserAccount operator = insertUser("operator", UserRole.AGENT);
        Ticket ticket = insertTicket("null-operation", creator.getId());
        TicketOperationLog log = newLog(
                ticket.getId(), operator.getId(), null, "OPEN", "IN_PROGRESS"
        );

        assertThrows(
                DataIntegrityViolationException.class,
                () -> ticketOperationLogMapper.insert(log)
        );
    }

    @Test
    void shouldExposeExpectedTableMetadata() {
        List<Map<String, Object>> columnRows = jdbcTemplate.queryForList("""
                SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT, EXTRA
                FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'ticket_operation_logs'
                ORDER BY ORDINAL_POSITION
                """);
        Map<String, Map<String, Object>> columns = columnRows.stream().collect(Collectors.toMap(
                row -> (String) row.get("COLUMN_NAME"),
                Function.identity()
        ));

        String ticketIndexColumns = jdbcTemplate.queryForObject("""
                SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX)
                FROM INFORMATION_SCHEMA.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'ticket_operation_logs'
                  AND INDEX_NAME = 'idx_ticket_operation_logs_ticket_time'
                """, String.class);
        String operatorIndexColumns = jdbcTemplate.queryForObject("""
                SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX)
                FROM INFORMATION_SCHEMA.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'ticket_operation_logs'
                  AND INDEX_NAME = 'idx_ticket_operation_logs_operator_time'
                """, String.class);

        Map<String, Object> ticketForeignKey = foreignKeyMetadata(
                "fk_ticket_operation_logs_ticket"
        );
        Map<String, Object> operatorForeignKey = foreignKeyMetadata(
                "fk_ticket_operation_logs_operator"
        );
        Map<String, Object> table = jdbcTemplate.queryForMap("""
                SELECT ENGINE, TABLE_COLLATION
                FROM INFORMATION_SCHEMA.TABLES
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'ticket_operation_logs'
                """);

        assertAll(
                () -> assertEquals(7, columns.size()),
                () -> assertColumn(columns, "id", "bigint unsigned", "NO"),
                () -> assertColumn(columns, "ticket_id", "bigint unsigned", "NO"),
                () -> assertColumn(columns, "operator_user_id", "bigint unsigned", "NO"),
                () -> assertColumn(columns, "operation_type", "varchar(32)", "NO"),
                () -> assertColumn(columns, "before_value", "varchar(64)", "YES"),
                () -> assertColumn(columns, "after_value", "varchar(64)", "YES"),
                () -> assertColumn(columns, "created_at", "datetime(3)", "NO"),
                () -> assertEquals("auto_increment", columns.get("id").get("EXTRA")),
                () -> assertEquals("ticket_id,created_at,id", ticketIndexColumns),
                () -> assertEquals("operator_user_id,created_at,id", operatorIndexColumns),
                () -> assertForeignKey(
                        ticketForeignKey,
                        "ticket_id",
                        "tickets",
                        "id"
                ),
                () -> assertForeignKey(
                        operatorForeignKey,
                        "operator_user_id",
                        "users",
                        "id"
                ),
                () -> assertEquals("InnoDB", table.get("ENGINE")),
                () -> assertEquals("utf8mb4_0900_ai_ci", table.get("TABLE_COLLATION"))
        );
    }

    private Map<String, Object> foreignKeyMetadata(String constraintName) {
        return jdbcTemplate.queryForMap("""
                SELECT k.COLUMN_NAME,
                       k.REFERENCED_TABLE_NAME,
                       k.REFERENCED_COLUMN_NAME,
                       r.DELETE_RULE,
                       r.UPDATE_RULE
                FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE k
                JOIN INFORMATION_SCHEMA.REFERENTIAL_CONSTRAINTS r
                  ON r.CONSTRAINT_SCHEMA = k.CONSTRAINT_SCHEMA
                 AND r.CONSTRAINT_NAME = k.CONSTRAINT_NAME
                 AND r.TABLE_NAME = k.TABLE_NAME
                WHERE k.TABLE_SCHEMA = DATABASE()
                  AND k.TABLE_NAME = 'ticket_operation_logs'
                  AND k.CONSTRAINT_NAME = ?
                """, constraintName);
    }

    private static void assertColumn(
            Map<String, Map<String, Object>> columns,
            String name,
            String type,
            String nullable
    ) {
        assertEquals(type, columns.get(name).get("COLUMN_TYPE"));
        assertEquals(nullable, columns.get(name).get("IS_NULLABLE"));
    }

    private static void assertForeignKey(
            Map<String, Object> foreignKey,
            String column,
            String referencedTable,
            String referencedColumn
    ) {
        assertEquals(column, foreignKey.get("COLUMN_NAME"));
        assertEquals(referencedTable, foreignKey.get("REFERENCED_TABLE_NAME"));
        assertEquals(referencedColumn, foreignKey.get("REFERENCED_COLUMN_NAME"));
        assertEquals("RESTRICT", foreignKey.get("DELETE_RULE"));
        assertEquals("RESTRICT", foreignKey.get("UPDATE_RULE"));
    }

    private UserAccount insertUser(String suffix, UserRole role) {
        UserAccount user = new UserAccount();
        user.setUsername(prefix() + suffix);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setDisplayName("P7-3-1 " + role.name());
        user.setRole(role);
        assertEquals(1, userAccountMapper.insert(user));
        assertNotNull(user.getId());
        assertTrue(passwordEncoder.matches(PASSWORD, user.getPasswordHash()));
        return user;
    }

    private Ticket insertTicket(String scenario, Long creatorUserId) {
        Ticket ticket = new Ticket();
        ticket.setTitle(prefix() + scenario);
        ticket.setDescription("P7-3-1操作日志持久化测试");
        ticket.setCreatorName(prefix() + "creator-" + scenario);
        ticket.setCreatorUserId(creatorUserId);
        ticket.setPriority(TicketPriority.HIGH);
        ticket.setStatus(TicketStatus.OPEN);
        assertEquals(1, ticketMapper.insert(ticket));
        assertNotNull(ticket.getId());
        preparedTicketIds.add(ticket.getId());
        return ticketMapper.selectById(ticket.getId());
    }

    private static TicketOperationLog newLog(
            Long ticketId,
            Long operatorUserId,
            TicketOperationType operationType,
            String beforeValue,
            String afterValue
    ) {
        TicketOperationLog log = new TicketOperationLog();
        log.setTicketId(ticketId);
        log.setOperatorUserId(operatorUserId);
        log.setOperationType(operationType);
        log.setBeforeValue(beforeValue);
        log.setAfterValue(afterValue);
        return log;
    }

    private String prefix() {
        if (testPrefix == null) {
            testPrefix = "p731_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20) + "_";
        }
        return testPrefix;
    }
}
