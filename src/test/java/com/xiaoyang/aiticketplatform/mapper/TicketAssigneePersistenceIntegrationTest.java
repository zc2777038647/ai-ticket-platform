package com.xiaoyang.aiticketplatform.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xiaoyang.aiticketplatform.entity.Ticket;
import com.xiaoyang.aiticketplatform.entity.UserAccount;
import com.xiaoyang.aiticketplatform.enums.TicketPriority;
import com.xiaoyang.aiticketplatform.enums.TicketStatus;
import com.xiaoyang.aiticketplatform.enums.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class TicketAssigneePersistenceIntegrationTest {

    private static final String TEST_HASH_PREFIX = "{test-only-hash}:";

    @Autowired
    private UserAccountMapper userAccountMapper;

    @Autowired
    private TicketMapper ticketMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
    void shouldPersistTicketWithDifferentCreatorAndRealAgentAssignee() {
        UserAccount creator = insertUser("creator", UserRole.USER);
        UserAccount assignee = insertUser("assignee", UserRole.AGENT);
        Ticket ticket = newTicket("assigned", creator.getId(), assignee.getId());

        assertEquals(1, ticketMapper.insert(ticket));
        assertNotNull(ticket.getId());
        assertTrue(ticket.getId() > 0);

        Ticket persisted = ticketMapper.selectById(ticket.getId());
        assertNotNull(persisted);
        assertAll(
                () -> assertEquals(creator.getId(), persisted.getCreatorUserId()),
                () -> assertEquals(assignee.getId(), persisted.getAssigneeUserId()),
                () -> assertNotEquals(persisted.getCreatorUserId(), persisted.getAssigneeUserId()),
                () -> assertEquals(ticket.getTitle(), persisted.getTitle()),
                () -> assertEquals(ticket.getCreatorName(), persisted.getCreatorName()),
                () -> assertEquals(TicketPriority.HIGH, persisted.getPriority()),
                () -> assertEquals(TicketStatus.OPEN, persisted.getStatus()),
                () -> assertNotNull(persisted.getCreatedAt()),
                () -> assertNotNull(persisted.getUpdatedAt())
        );
    }

    @Test
    void shouldQueryTicketByAssigneeUserIdWithLambdaWrapper() {
        UserAccount assignee = insertUser("query_assignee", UserRole.AGENT);
        Ticket assignedTicket = newTicket("query_assigned", null, assignee.getId());
        Ticket unassignedTicket = newTicket("query_unassigned", null, null);
        assertEquals(1, ticketMapper.insert(assignedTicket));
        assertEquals(1, ticketMapper.insert(unassignedTicket));

        List<Ticket> result = ticketMapper.selectList(
                new LambdaQueryWrapper<Ticket>()
                        .eq(Ticket::getAssigneeUserId, assignee.getId())
        );

        assertAll(
                () -> assertEquals(1, result.size()),
                () -> assertEquals(assignedTicket.getId(), result.getFirst().getId()),
                () -> assertEquals(assignee.getId(), result.getFirst().getAssigneeUserId())
        );
    }

    @Test
    void shouldPersistUnassignedTicketWithRealCreator() {
        UserAccount creator = insertUser("unassigned_creator", UserRole.USER);
        Ticket ticket = newTicket("unassigned", creator.getId(), null);

        assertEquals(1, ticketMapper.insert(ticket));
        assertNotNull(ticket.getId());

        Ticket persisted = ticketMapper.selectById(ticket.getId());
        assertNotNull(persisted);
        assertAll(
                () -> assertEquals(creator.getId(), persisted.getCreatorUserId()),
                () -> assertNull(persisted.getAssigneeUserId()),
                () -> assertEquals(ticket.getTitle(), persisted.getTitle()),
                () -> assertEquals(TicketPriority.HIGH, persisted.getPriority()),
                () -> assertEquals(TicketStatus.OPEN, persisted.getStatus()),
                () -> assertNotNull(persisted.getCreatedAt()),
                () -> assertNotNull(persisted.getUpdatedAt())
        );
    }

    @Test
    void shouldRejectAssigneeUserIdThatDoesNotExist() {
        assertEquals(0L, userAccountMapper.selectCount(
                new LambdaQueryWrapper<UserAccount>()
                        .eq(UserAccount::getId, Long.MAX_VALUE)
        ));
        Ticket ticket = newTicket("invalid_assignee", null, Long.MAX_VALUE);

        assertThrows(
                DataIntegrityViolationException.class,
                () -> ticketMapper.insert(ticket)
        );
    }

    @Test
    void shouldExposeExpectedAssigneeColumnIndexAndForeignKeyMetadata() {
        Map<String, Object> column = jdbcTemplate.queryForMap("""
                SELECT COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT
                FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'tickets'
                  AND COLUMN_NAME = 'assignee_user_id'
                """);
        Map<String, Object> index = jdbcTemplate.queryForMap("""
                SELECT INDEX_NAME, COLUMN_NAME, NON_UNIQUE
                FROM INFORMATION_SCHEMA.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'tickets'
                  AND INDEX_NAME = 'idx_tickets_assignee_user_id'
                  AND COLUMN_NAME = 'assignee_user_id'
                """);
        Map<String, Object> foreignKey = jdbcTemplate.queryForMap("""
                SELECT CONSTRAINT_NAME, COLUMN_NAME, REFERENCED_TABLE_NAME, REFERENCED_COLUMN_NAME
                FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'tickets'
                  AND CONSTRAINT_NAME = 'fk_tickets_assignee_user'
                  AND COLUMN_NAME = 'assignee_user_id'
                """);
        Map<String, Object> rules = jdbcTemplate.queryForMap("""
                SELECT DELETE_RULE, UPDATE_RULE
                FROM INFORMATION_SCHEMA.REFERENTIAL_CONSTRAINTS
                WHERE CONSTRAINT_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'tickets'
                  AND CONSTRAINT_NAME = 'fk_tickets_assignee_user'
                """);

        assertAll(
                () -> assertEquals("bigint unsigned", column.get("COLUMN_TYPE")),
                () -> assertEquals("YES", column.get("IS_NULLABLE")),
                () -> assertNull(column.get("COLUMN_DEFAULT")),
                () -> assertEquals("idx_tickets_assignee_user_id", index.get("INDEX_NAME")),
                () -> assertEquals("assignee_user_id", index.get("COLUMN_NAME")),
                () -> assertEquals(1L, ((Number) index.get("NON_UNIQUE")).longValue()),
                () -> assertEquals("fk_tickets_assignee_user", foreignKey.get("CONSTRAINT_NAME")),
                () -> assertEquals("assignee_user_id", foreignKey.get("COLUMN_NAME")),
                () -> assertEquals("users", foreignKey.get("REFERENCED_TABLE_NAME")),
                () -> assertEquals("id", foreignKey.get("REFERENCED_COLUMN_NAME")),
                () -> assertEquals("RESTRICT", rules.get("DELETE_RULE")),
                () -> assertEquals("RESTRICT", rules.get("UPDATE_RULE"))
        );
    }

    private UserAccount insertUser(String suffix, UserRole role) {
        UserAccount user = new UserAccount();
        user.setUsername(prefix() + suffix);
        user.setPasswordHash(TEST_HASH_PREFIX + UUID.randomUUID());
        user.setDisplayName("工单处理人持久化测试");
        user.setRole(role);
        assertEquals(1, userAccountMapper.insert(user));
        assertNotNull(user.getId());
        assertTrue(user.getId() > 0);
        return user;
    }

    private Ticket newTicket(String scenario, Long creatorUserId, Long assigneeUserId) {
        Ticket ticket = new Ticket();
        ticket.setTitle("assignee-" + scenario);
        ticket.setDescription("工单处理人持久化测试");
        ticket.setCreatorName(prefix() + scenario);
        ticket.setCreatorUserId(creatorUserId);
        ticket.setAssigneeUserId(assigneeUserId);
        ticket.setPriority(TicketPriority.HIGH);
        ticket.setStatus(TicketStatus.OPEN);
        return ticket;
    }

    private String prefix() {
        if (testPrefix == null) {
            testPrefix = "p71_" + UUID.randomUUID().toString().replace("-", "") + "_";
        }
        return testPrefix;
    }
}
