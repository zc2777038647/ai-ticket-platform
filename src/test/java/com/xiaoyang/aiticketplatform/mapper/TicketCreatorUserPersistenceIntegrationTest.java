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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class TicketCreatorUserPersistenceIntegrationTest {

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
    void shouldPersistTicketBoundToRealUserAndQueryByCreatorUserId() {
        UserAccount user = insertUser();
        Ticket ticket = newTicket("bound", user.getId());

        assertEquals(1, ticketMapper.insert(ticket));
        assertNotNull(ticket.getId());
        assertTrue(ticket.getId() > 0);

        Ticket persisted = ticketMapper.selectById(ticket.getId());
        List<Ticket> ticketsByCreatorUserId = ticketMapper.selectList(
                new LambdaQueryWrapper<Ticket>()
                        .eq(Ticket::getCreatorUserId, user.getId())
        );

        assertNotNull(persisted);
        assertAll(
                () -> assertEquals(user.getId(), persisted.getCreatorUserId()),
                () -> assertEquals(ticket.getCreatorName(), persisted.getCreatorName()),
                () -> assertEquals(TicketPriority.HIGH, persisted.getPriority()),
                () -> assertEquals(TicketStatus.OPEN, persisted.getStatus()),
                () -> assertNotNull(persisted.getCreatedAt()),
                () -> assertNotNull(persisted.getUpdatedAt()),
                () -> assertEquals(1, ticketsByCreatorUserId.size()),
                () -> assertEquals(ticket.getId(), ticketsByCreatorUserId.getFirst().getId())
        );
    }

    @Test
    void shouldKeepNullCreatorUserIdCompatibleWithHistoricalTickets() {
        Ticket ticket = newTicket("historical", null);

        assertEquals(1, ticketMapper.insert(ticket));
        assertNotNull(ticket.getId());

        Ticket persisted = ticketMapper.selectById(ticket.getId());
        assertNotNull(persisted);
        assertAll(
                () -> assertNull(persisted.getCreatorUserId()),
                () -> assertEquals(ticket.getCreatorName(), persisted.getCreatorName()),
                () -> assertEquals(TicketStatus.OPEN, persisted.getStatus()),
                () -> assertNotNull(persisted.getCreatedAt()),
                () -> assertNotNull(persisted.getUpdatedAt())
        );
    }

    @Test
    void shouldRejectCreatorUserIdThatDoesNotExist() {
        assertEquals(0L, userAccountMapper.selectCount(
                new LambdaQueryWrapper<UserAccount>()
                        .eq(UserAccount::getId, Long.MAX_VALUE)
        ));
        Ticket ticket = newTicket("invalid-foreign-key", Long.MAX_VALUE);

        assertThrows(DataIntegrityViolationException.class,
                () -> ticketMapper.insert(ticket));
    }

    @Test
    void shouldExposeExpectedColumnIndexAndForeignKeyMetadata() {
        Map<String, Object> column = jdbcTemplate.queryForMap("""
                SELECT COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT
                FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'tickets'
                  AND COLUMN_NAME = 'creator_user_id'
                """);
        Integer indexColumns = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM INFORMATION_SCHEMA.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'tickets'
                  AND INDEX_NAME = 'idx_tickets_creator_user_id'
                  AND COLUMN_NAME = 'creator_user_id'
                """, Integer.class);
        Map<String, Object> foreignKey = jdbcTemplate.queryForMap("""
                SELECT REFERENCED_TABLE_NAME, REFERENCED_COLUMN_NAME
                FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'tickets'
                  AND COLUMN_NAME = 'creator_user_id'
                  AND CONSTRAINT_NAME = 'fk_tickets_creator_user'
                """);
        Map<String, Object> rules = jdbcTemplate.queryForMap("""
                SELECT DELETE_RULE, UPDATE_RULE
                FROM INFORMATION_SCHEMA.REFERENTIAL_CONSTRAINTS
                WHERE CONSTRAINT_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'tickets'
                  AND CONSTRAINT_NAME = 'fk_tickets_creator_user'
                """);

        assertAll(
                () -> assertEquals("bigint unsigned", column.get("COLUMN_TYPE")),
                () -> assertEquals("YES", column.get("IS_NULLABLE")),
                () -> assertNull(column.get("COLUMN_DEFAULT")),
                () -> assertEquals(1, indexColumns),
                () -> assertEquals("users", foreignKey.get("REFERENCED_TABLE_NAME")),
                () -> assertEquals("id", foreignKey.get("REFERENCED_COLUMN_NAME")),
                () -> assertEquals("RESTRICT", rules.get("DELETE_RULE")),
                () -> assertEquals("RESTRICT", rules.get("UPDATE_RULE"))
        );
    }

    private UserAccount insertUser() {
        UserAccount user = new UserAccount();
        user.setUsername(prefix() + "user");
        user.setPasswordHash(TEST_HASH_PREFIX + UUID.randomUUID());
        user.setDisplayName("工单创建用户关联测试");
        user.setRole(UserRole.USER);
        assertEquals(1, userAccountMapper.insert(user));
        assertNotNull(user.getId());
        assertTrue(user.getId() > 0);
        return user;
    }

    private Ticket newTicket(String scenario, Long creatorUserId) {
        Ticket ticket = new Ticket();
        ticket.setTitle("creator-user-" + scenario);
        ticket.setDescription("工单创建用户关联持久化测试");
        ticket.setCreatorName(prefix() + scenario);
        ticket.setCreatorUserId(creatorUserId);
        ticket.setPriority(TicketPriority.HIGH);
        ticket.setStatus(TicketStatus.OPEN);
        return ticket;
    }

    private String prefix() {
        if (testPrefix == null) {
            testPrefix = "p61_" + UUID.randomUUID().toString().replace("-", "") + "_";
        }
        return testPrefix;
    }
}
