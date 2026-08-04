package com.xiaoyang.aiticketplatform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xiaoyang.aiticketplatform.dto.request.TicketPageQuery;
import com.xiaoyang.aiticketplatform.dto.response.PageResponse;
import com.xiaoyang.aiticketplatform.dto.response.TicketResponse;
import com.xiaoyang.aiticketplatform.entity.Ticket;
import com.xiaoyang.aiticketplatform.enums.TicketPriority;
import com.xiaoyang.aiticketplatform.enums.TicketStatus;
import com.xiaoyang.aiticketplatform.mapper.TicketMapper;
import com.xiaoyang.aiticketplatform.service.TicketService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class TicketPaginationServiceIntegrationTest {

    @Autowired
    private TicketService ticketService;

    @Autowired
    private TicketMapper ticketMapper;

    private String cleanupToken;

    @AfterTransaction
    void verifyPreparedTicketsWereRolledBack() {
        if (cleanupToken != null) {
            assertEquals(0L, ticketMapper.selectCount(new LambdaQueryWrapper<Ticket>()
                    .like(Ticket::getCreatorName, cleanupToken)
                    .or()
                    .like(Ticket::getTitle, cleanupToken)
                    .or()
                    .like(Ticket::getDescription, cleanupToken)));
        }
    }

    @Test
    void shouldFilterPaginateAndOrderTicketsThroughRealMySql() {
        cleanupToken = UUID.randomUUID().toString();
        String creatorName = "service-page-" + cleanupToken;
        String otherCreatorName = "other-user-" + cleanupToken;
        String keywordToken = "key-" + cleanupToken;
        LocalDateTime laterTime = LocalDateTime.of(2030, 6, 1, 12, 0);
        LocalDateTime earlierTime = LocalDateTime.of(2030, 5, 1, 12, 0);

        Ticket matchByTitleLater = insertTicket(
                keywordToken + " 标题命中",
                "普通描述",
                creatorName,
                TicketPriority.HIGH,
                TicketStatus.OPEN,
                laterTime
        );
        Ticket matchByDescriptionEarlier = insertTicket(
                "普通标题一",
                "描述命中 " + keywordToken,
                creatorName,
                TicketPriority.HIGH,
                TicketStatus.OPEN,
                earlierTime
        );
        Ticket matchByTitleSameTime = insertTicket(
                keywordToken + " 另一个标题命中",
                "普通描述",
                creatorName,
                TicketPriority.HIGH,
                TicketStatus.OPEN,
                earlierTime
        );
        Ticket excludedByStatus = insertTicket(
                "普通标题二",
                "已解决但描述命中 " + keywordToken,
                creatorName,
                TicketPriority.HIGH,
                TicketStatus.RESOLVED,
                laterTime
        );
        Ticket excludedByPriority = insertTicket(
                keywordToken + " 低优先级",
                "普通描述",
                creatorName,
                TicketPriority.LOW,
                TicketStatus.OPEN,
                laterTime
        );
        Ticket excludedByCreator = insertTicket(
                keywordToken + " 其他创建人",
                "普通描述",
                otherCreatorName,
                TicketPriority.HIGH,
                TicketStatus.OPEN,
                laterTime
        );
        Ticket excludedByKeyword = insertTicket(
                "无关标题",
                "无关描述",
                creatorName,
                TicketPriority.HIGH,
                TicketStatus.OPEN,
                laterTime
        );

        PageResponse<TicketResponse> firstPage = ticketService.pageTickets(new TicketPageQuery(
                1,
                2,
                TicketStatus.OPEN,
                TicketPriority.HIGH,
                "  " + creatorName + "  ",
                "  " + keywordToken + "  "
        ));
        PageResponse<TicketResponse> secondPage = ticketService.pageTickets(new TicketPageQuery(
                2,
                2,
                TicketStatus.OPEN,
                TicketPriority.HIGH,
                "  " + creatorName + "  ",
                "  " + keywordToken + "  "
        ));

        assertPage(firstPage, 3, 2, 1, 2, 2);
        assertPage(secondPage, 3, 2, 2, 2, 1);
        assertEquals(
                List.of(matchByTitleLater.getId(), matchByTitleSameTime.getId()),
                ticketIds(firstPage.records())
        );
        assertEquals(List.of(matchByDescriptionEarlier.getId()), ticketIds(secondPage.records()));
        assertInstanceOf(TicketResponse.class, firstPage.records().getFirst());

        Set<Long> returnedIds = new HashSet<>(ticketIds(firstPage.records()));
        returnedIds.addAll(ticketIds(secondPage.records()));
        assertEquals(3, returnedIds.size());
        assertFalse(returnedIds.contains(excludedByStatus.getId()));
        assertFalse(returnedIds.contains(excludedByPriority.getId()));
        assertFalse(returnedIds.contains(excludedByCreator.getId()));
        assertFalse(returnedIds.contains(excludedByKeyword.getId()));
        assertTrue(firstPage.records().stream()
                .anyMatch(ticket -> ticket.title().contains(keywordToken)));
        assertTrue(secondPage.records().stream()
                .anyMatch(ticket -> ticket.description().contains(keywordToken)));
    }

    @Test
    void shouldIgnoreBlankCreatorNameAndKeywordConditions() {
        cleanupToken = UUID.randomUUID().toString();
        Ticket expected = insertTicket(
                "blank-filter-" + cleanupToken,
                "空白筛选条件测试",
                "blank-user-" + cleanupToken,
                TicketPriority.URGENT,
                TicketStatus.CLOSED,
                LocalDateTime.of(2099, 1, 1, 0, 0)
        );

        PageResponse<TicketResponse> response = ticketService.pageTickets(new TicketPageQuery(
                1,
                100,
                TicketStatus.CLOSED,
                TicketPriority.URGENT,
                "   ",
                "   "
        ));

        assertTrue(response.records().stream()
                .anyMatch(ticket -> expected.getId().equals(ticket.id())));
    }

    private Ticket insertTicket(
            String title,
            String description,
            String creatorName,
            TicketPriority priority,
            TicketStatus status,
            LocalDateTime timestamp
    ) {
        Ticket ticket = new Ticket();
        ticket.setTitle(title);
        ticket.setDescription(description);
        ticket.setCreatorName(creatorName);
        ticket.setPriority(priority);
        ticket.setStatus(status);
        ticket.setCreatedAt(timestamp);
        ticket.setUpdatedAt(timestamp);

        assertEquals(1, ticketMapper.insert(ticket));
        assertNotNull(ticket.getId());
        assertTrue(ticket.getId() > 0);
        return ticket;
    }

    private static void assertPage(
            PageResponse<TicketResponse> response,
            long total,
            long pages,
            long current,
            long size,
            int recordCount
    ) {
        assertEquals(total, response.total());
        assertEquals(pages, response.pages());
        assertEquals(current, response.current());
        assertEquals(size, response.size());
        assertEquals(recordCount, response.records().size());
    }

    private static List<Long> ticketIds(List<TicketResponse> tickets) {
        return tickets.stream().map(TicketResponse::id).toList();
    }
}
