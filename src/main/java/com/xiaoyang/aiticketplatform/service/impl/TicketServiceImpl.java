package com.xiaoyang.aiticketplatform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xiaoyang.aiticketplatform.common.ErrorCode;
import com.xiaoyang.aiticketplatform.dto.request.CreateTicketRequest;
import com.xiaoyang.aiticketplatform.dto.request.TicketPageQuery;
import com.xiaoyang.aiticketplatform.dto.response.PageResponse;
import com.xiaoyang.aiticketplatform.dto.response.TicketResponse;
import com.xiaoyang.aiticketplatform.entity.Ticket;
import com.xiaoyang.aiticketplatform.enums.TicketStatus;
import com.xiaoyang.aiticketplatform.exception.BusinessException;
import com.xiaoyang.aiticketplatform.mapper.TicketMapper;
import com.xiaoyang.aiticketplatform.service.TicketService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

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

        return toResponse(ticket);
    }

    @Override
    @Transactional(readOnly = true)
    public TicketResponse getTicketById(Long id) {
        Ticket ticket = ticketMapper.selectById(id);
        if (ticket == null) {
            throw new BusinessException(ErrorCode.TICKET_NOT_FOUND);
        }

        return toResponse(ticket);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<TicketResponse> pageTickets(TicketPageQuery query) {
        Page<Ticket> page = new Page<>(query.page(), query.size());
        LambdaQueryWrapper<Ticket> wrapper = new LambdaQueryWrapper<>();

        wrapper.eq(query.status() != null, Ticket::getStatus, query.status());
        wrapper.eq(query.priority() != null, Ticket::getPriority, query.priority());

        if (StringUtils.hasText(query.creatorName())) {
            wrapper.eq(Ticket::getCreatorName, query.creatorName().trim());
        }
        if (StringUtils.hasText(query.keyword())) {
            String keyword = query.keyword().trim();
            wrapper.and(keywordWrapper -> keywordWrapper
                    .like(Ticket::getTitle, keyword)
                    .or()
                    .like(Ticket::getDescription, keyword));
        }

        wrapper.orderByDesc(Ticket::getCreatedAt)
                .orderByDesc(Ticket::getId);

        Page<Ticket> result = ticketMapper.selectPage(page, wrapper);
        List<TicketResponse> records = result.getRecords().stream()
                .map(this::toResponse)
                .toList();

        return new PageResponse<>(
                records,
                result.getTotal(),
                result.getPages(),
                result.getCurrent(),
                result.getSize()
        );
    }

    private TicketResponse toResponse(Ticket ticket) {
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
