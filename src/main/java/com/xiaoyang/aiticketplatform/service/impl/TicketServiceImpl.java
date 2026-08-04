package com.xiaoyang.aiticketplatform.service.impl;

import com.xiaoyang.aiticketplatform.common.ErrorCode;
import com.xiaoyang.aiticketplatform.dto.request.CreateTicketRequest;
import com.xiaoyang.aiticketplatform.dto.response.TicketResponse;
import com.xiaoyang.aiticketplatform.entity.Ticket;
import com.xiaoyang.aiticketplatform.enums.TicketStatus;
import com.xiaoyang.aiticketplatform.exception.BusinessException;
import com.xiaoyang.aiticketplatform.mapper.TicketMapper;
import com.xiaoyang.aiticketplatform.service.TicketService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TicketServiceImpl implements TicketService {

    private final TicketMapper ticketMapper;

    public TicketServiceImpl(TicketMapper ticketMapper) {
        this.ticketMapper = ticketMapper;
    }

    @Override
    @Transactional
    public TicketResponse createTicket(CreateTicketRequest request) {
        Ticket ticket = new Ticket();
        ticket.setTitle(request.title());
        ticket.setDescription(request.description());
        ticket.setCreatorName(request.creatorName());
        ticket.setPriority(request.priority());
        ticket.setStatus(TicketStatus.OPEN);

        int affectedRows = ticketMapper.insert(ticket);
        if (affectedRows != 1) {
            throw new IllegalStateException("创建工单失败：数据库插入影响行数不是 1");
        }
        if (ticket.getId() == null) {
            throw new IllegalStateException("创建工单失败：数据库自增 ID 未回填");
        }

        return new TicketResponse(
                ticket.getId(),
                ticket.getTitle(),
                ticket.getDescription(),
                ticket.getCreatorName(),
                ticket.getPriority(),
                ticket.getStatus()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public TicketResponse getTicketById(Long id) {
        Ticket ticket = ticketMapper.selectById(id);
        if (ticket == null) {
            throw new BusinessException(ErrorCode.TICKET_NOT_FOUND);
        }

        return new TicketResponse(
                ticket.getId(),
                ticket.getTitle(),
                ticket.getDescription(),
                ticket.getCreatorName(),
                ticket.getPriority(),
                ticket.getStatus()
        );
    }
}
