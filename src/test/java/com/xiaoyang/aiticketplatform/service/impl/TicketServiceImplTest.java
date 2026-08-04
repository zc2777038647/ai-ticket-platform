package com.xiaoyang.aiticketplatform.service.impl;

import com.xiaoyang.aiticketplatform.dto.request.CreateTicketRequest;
import com.xiaoyang.aiticketplatform.dto.response.TicketResponse;
import com.xiaoyang.aiticketplatform.entity.Ticket;
import com.xiaoyang.aiticketplatform.enums.TicketPriority;
import com.xiaoyang.aiticketplatform.enums.TicketStatus;
import com.xiaoyang.aiticketplatform.mapper.TicketMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketServiceImplTest {

    @Mock
    private TicketMapper ticketMapper;

    @InjectMocks
    private TicketServiceImpl ticketService;

    @Test
    void shouldCreateTicketAndReturnResponse() {
        CreateTicketRequest request = validRequest();
        when(ticketMapper.insert(any(Ticket.class))).thenAnswer(invocation -> {
            Ticket ticket = invocation.getArgument(0);
            assertNull(ticket.getId());
            ticket.setId(100L);
            return 1;
        });

        TicketResponse response = ticketService.createTicket(request);

        ArgumentCaptor<Ticket> ticketCaptor = ArgumentCaptor.forClass(Ticket.class);
        verify(ticketMapper, times(1)).insert(ticketCaptor.capture());
        verifyNoMoreInteractions(ticketMapper);

        Ticket insertedTicket = ticketCaptor.getValue();
        assertAll(
                () -> assertEquals("无法登录系统", insertedTicket.getTitle()),
                () -> assertEquals("用户输入正确密码后仍然无法登录", insertedTicket.getDescription()),
                () -> assertEquals("小杨", insertedTicket.getCreatorName()),
                () -> assertEquals(TicketPriority.HIGH, insertedTicket.getPriority()),
                () -> assertEquals(TicketStatus.OPEN, insertedTicket.getStatus()),
                () -> assertNull(insertedTicket.getCreatedAt()),
                () -> assertNull(insertedTicket.getUpdatedAt()),
                () -> assertEquals(100L, response.id()),
                () -> assertEquals("无法登录系统", response.title()),
                () -> assertEquals("用户输入正确密码后仍然无法登录", response.description()),
                () -> assertEquals("小杨", response.creatorName()),
                () -> assertEquals(TicketPriority.HIGH, response.priority()),
                () -> assertEquals(TicketStatus.OPEN, response.status())
        );
    }

    @Test
    void shouldThrowWhenInsertDoesNotAffectExactlyOneRow() {
        when(ticketMapper.insert(any(Ticket.class))).thenReturn(0);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> ticketService.createTicket(validRequest())
        );

        assertEquals("创建工单失败：数据库插入影响行数不是 1", exception.getMessage());
        verify(ticketMapper, times(1)).insert(any(Ticket.class));
        verifyNoMoreInteractions(ticketMapper);
    }

    @Test
    void shouldThrowWhenGeneratedIdIsNotBackfilled() {
        when(ticketMapper.insert(any(Ticket.class))).thenReturn(1);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> ticketService.createTicket(validRequest())
        );

        assertEquals("创建工单失败：数据库自增 ID 未回填", exception.getMessage());
        verify(ticketMapper, times(1)).insert(any(Ticket.class));
        verifyNoMoreInteractions(ticketMapper);
    }

    private static CreateTicketRequest validRequest() {
        return new CreateTicketRequest(
                "无法登录系统",
                "用户输入正确密码后仍然无法登录",
                "小杨",
                TicketPriority.HIGH
        );
    }
}
