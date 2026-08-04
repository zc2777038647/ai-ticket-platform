package com.xiaoyang.aiticketplatform.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xiaoyang.aiticketplatform.entity.Ticket;
import com.xiaoyang.aiticketplatform.enums.TicketPriority;
import com.xiaoyang.aiticketplatform.enums.TicketStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class TicketPaginationPluginIntegrationTest {

    @Autowired
    private TicketMapper ticketMapper;

    private String creatorNamePrefix;

    @AfterTransaction
    void verifyPreparedTicketsWereRolledBack() {
        if (creatorNamePrefix != null) {
            assertEquals(0L, ticketMapper.selectCount(queryByCreatorNamePrefix()));
        }
    }

    @Test
    void shouldPaginateTicketsThroughMybatisPlusPluginAndMySql() {
        creatorNamePrefix = "pagination-test-" + UUID.randomUUID() + "-";
        for (int sequence = 1; sequence <= 3; sequence++) {
            Ticket ticket = new Ticket();
            ticket.setTitle("分页测试工单" + sequence);
            ticket.setDescription("验证 MyBatis-Plus 分页插件的真实查询" + sequence);
            ticket.setCreatorName(creatorNamePrefix + sequence);
            ticket.setPriority(TicketPriority.HIGH);
            ticket.setStatus(TicketStatus.OPEN);

            assertEquals(1, ticketMapper.insert(ticket));
            assertNotNull(ticket.getId());
            assertTrue(ticket.getId() > 0);
        }

        Page<Ticket> firstPage = new Page<>(1, 2);
        ticketMapper.selectPage(firstPage, queryByCreatorNamePrefix());

        assertEquals(1L, firstPage.getCurrent());
        assertEquals(2L, firstPage.getSize());
        assertEquals(3L, firstPage.getTotal());
        assertEquals(2L, firstPage.getPages());
        assertEquals(2, firstPage.getRecords().size());
        assertTrue(firstPage.getRecords().stream()
                .allMatch(ticket -> ticket.getCreatorName().startsWith(creatorNamePrefix)));
        assertTrue(firstPage.getRecords().get(0).getId() < firstPage.getRecords().get(1).getId());

        Page<Ticket> secondPage = new Page<>(2, 2);
        ticketMapper.selectPage(secondPage, queryByCreatorNamePrefix());

        assertEquals(2L, secondPage.getCurrent());
        assertEquals(2L, secondPage.getSize());
        assertEquals(3L, secondPage.getTotal());
        assertEquals(2L, secondPage.getPages());
        assertEquals(1, secondPage.getRecords().size());
        assertTrue(secondPage.getRecords().getFirst().getCreatorName().startsWith(creatorNamePrefix));

        Set<Long> firstPageIds = new HashSet<>(ticketIds(firstPage.getRecords()));
        Long secondPageId = secondPage.getRecords().getFirst().getId();
        assertFalse(firstPageIds.contains(secondPageId));
        assertTrue(firstPage.getRecords().getLast().getId() < secondPageId);
    }

    private LambdaQueryWrapper<Ticket> queryByCreatorNamePrefix() {
        return new LambdaQueryWrapper<Ticket>()
                .likeRight(Ticket::getCreatorName, creatorNamePrefix)
                .orderByAsc(Ticket::getId);
    }

    private static List<Long> ticketIds(List<Ticket> tickets) {
        return tickets.stream().map(Ticket::getId).toList();
    }
}
