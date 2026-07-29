package com.xiaoyang.aiticketplatform.mapper;

import com.xiaoyang.aiticketplatform.entity.Ticket;
import com.xiaoyang.aiticketplatform.enums.TicketPriority;
import com.xiaoyang.aiticketplatform.enums.TicketStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class TicketMapperIntegrationTest {

    @Autowired
    private TicketMapper ticketMapper;

    @Test
    void shouldInsertTicketAndReadDatabaseGeneratedValues() {
        Ticket ticket = new Ticket();
        ticket.setTitle("MyBatis-Plus 集成测试工单");
        ticket.setDescription("验证 TicketMapper 到 MySQL 的真实插入与查询链路");
        ticket.setCreatorName("integration-test");
        ticket.setPriority(TicketPriority.HIGH);
        ticket.setStatus(TicketStatus.OPEN);

        int affectedRows = ticketMapper.insert(ticket);

        assertEquals(1, affectedRows);
        assertNotNull(ticket.getId());
        assertTrue(ticket.getId() > 0);

        Ticket persistedTicket = ticketMapper.selectById(ticket.getId());

        assertNotNull(persistedTicket);
        assertAll(
                () -> assertEquals("MyBatis-Plus 集成测试工单", persistedTicket.getTitle()),
                () -> assertEquals("验证 TicketMapper 到 MySQL 的真实插入与查询链路", persistedTicket.getDescription()),
                () -> assertEquals("integration-test", persistedTicket.getCreatorName()),
                () -> assertEquals(TicketPriority.HIGH, persistedTicket.getPriority()),
                () -> assertEquals(TicketStatus.OPEN, persistedTicket.getStatus()),
                () -> assertNotNull(persistedTicket.getCreatedAt()),
                () -> assertNotNull(persistedTicket.getUpdatedAt())
        );
    }
}
