package com.xiaoyang.aiticketplatform.migration;

import com.xiaoyang.aiticketplatform.entity.Ticket;
import com.xiaoyang.aiticketplatform.enums.TicketPriority;
import com.xiaoyang.aiticketplatform.enums.TicketStatus;
import com.xiaoyang.aiticketplatform.mapper.TicketMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class TicketStatusMigrationIntegrationTest {

    @Autowired
    private TicketMapper ticketMapper;

    @Test
    void shouldUseOpenDatabaseDefaultWhenStatusIsNotSet() {
        Ticket ticket = new Ticket();
        ticket.setTitle("状态默认值迁移集成测试");
        ticket.setDescription("验证未设置状态时由 MySQL 写入 OPEN");
        ticket.setCreatorName("migration-integration-test");
        ticket.setPriority(TicketPriority.HIGH);

        int affectedRows = ticketMapper.insert(ticket);

        assertEquals(1, affectedRows);
        assertNotNull(ticket.getId());
        assertTrue(ticket.getId() > 0);

        Ticket persistedTicket = ticketMapper.selectById(ticket.getId());

        assertNotNull(persistedTicket);
        assertEquals(TicketStatus.OPEN, persistedTicket.getStatus());
        assertNotNull(persistedTicket.getCreatedAt());
        assertNotNull(persistedTicket.getUpdatedAt());
    }
}
