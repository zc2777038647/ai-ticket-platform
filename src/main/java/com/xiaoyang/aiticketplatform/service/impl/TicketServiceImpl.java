package com.xiaoyang.aiticketplatform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xiaoyang.aiticketplatform.common.ErrorCode;
import com.xiaoyang.aiticketplatform.dto.request.AssignTicketRequest;
import com.xiaoyang.aiticketplatform.dto.request.CreateTicketRequest;
import com.xiaoyang.aiticketplatform.dto.request.TicketPageQuery;
import com.xiaoyang.aiticketplatform.dto.request.UpdateTicketStatusRequest;
import com.xiaoyang.aiticketplatform.dto.response.PageResponse;
import com.xiaoyang.aiticketplatform.dto.response.TicketAssignmentResponse;
import com.xiaoyang.aiticketplatform.dto.response.TicketResponse;
import com.xiaoyang.aiticketplatform.entity.Ticket;
import com.xiaoyang.aiticketplatform.entity.UserAccount;
import com.xiaoyang.aiticketplatform.enums.TicketStatus;
import com.xiaoyang.aiticketplatform.enums.UserRole;
import com.xiaoyang.aiticketplatform.exception.BusinessException;
import com.xiaoyang.aiticketplatform.mapper.TicketMapper;
import com.xiaoyang.aiticketplatform.mapper.UserAccountMapper;
import com.xiaoyang.aiticketplatform.service.TicketService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;

@Service
public class TicketServiceImpl implements TicketService {

    private final TicketMapper ticketMapper;
    private final UserAccountMapper userAccountMapper;

    public TicketServiceImpl(TicketMapper ticketMapper, UserAccountMapper userAccountMapper) {
        this.ticketMapper = ticketMapper;
        this.userAccountMapper = userAccountMapper;
    }

    @Override
    @Transactional
    public TicketResponse createTicket(CreateTicketRequest request, Long creatorUserId) {
        if (creatorUserId == null || creatorUserId <= 0) {
            throw new IllegalArgumentException("creatorUserId 必须为正数");
        }

        Ticket ticket = new Ticket();
        ticket.setTitle(request.title());
        ticket.setDescription(request.description());
        ticket.setCreatorName(request.creatorName());
        ticket.setCreatorUserId(creatorUserId);
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
    public TicketResponse getTicketById(Long id, Long requesterUserId, UserRole requesterRole) {
        validateRequester(requesterUserId, requesterRole);

        Ticket ticket = switch (requesterRole) {
            case USER -> ticketMapper.selectOne(new LambdaQueryWrapper<Ticket>()
                    .eq(Ticket::getId, id)
                    .eq(Ticket::getCreatorUserId, requesterUserId));
            case AGENT, ADMIN -> ticketMapper.selectById(id);
        };
        if (ticket == null) {
            throw new BusinessException(ErrorCode.TICKET_NOT_FOUND);
        }

        return toResponse(ticket);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<TicketResponse> pageTickets(TicketPageQuery query) {
        return queryTickets(query, null);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<TicketResponse> pageMyTickets(TicketPageQuery query, Long creatorUserId) {
        if (creatorUserId == null || creatorUserId <= 0) {
            throw new IllegalArgumentException("creatorUserId 必须为正数");
        }
        return queryTickets(query, creatorUserId);
    }

    private PageResponse<TicketResponse> queryTickets(TicketPageQuery query, Long creatorUserId) {
        Page<Ticket> page = new Page<>(query.page(), query.size());
        LambdaQueryWrapper<Ticket> wrapper = new LambdaQueryWrapper<>();

        wrapper.eq(creatorUserId != null, Ticket::getCreatorUserId, creatorUserId);
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

    private void validateRequester(Long requesterUserId, UserRole requesterRole) {
        if (requesterUserId == null || requesterUserId <= 0) {
            throw new IllegalArgumentException("requesterUserId 必须为正数");
        }
        if (requesterRole == null) {
            throw new IllegalArgumentException("requesterRole 不能为空");
        }
    }

    @Override
    @Transactional
    public TicketResponse updateTicketStatus(Long id, UpdateTicketStatusRequest request) {
        Ticket ticket = ticketMapper.selectById(id);
        if (ticket == null) {
            throw new BusinessException(ErrorCode.TICKET_NOT_FOUND);
        }

        TicketStatus currentStatus = ticket.getStatus();
        TicketStatus targetStatus = request.status();
        if (!currentStatus.canTransitionTo(targetStatus)) {
            throw new BusinessException(ErrorCode.INVALID_TICKET_STATUS_TRANSITION);
        }

        LambdaUpdateWrapper<Ticket> updateWrapper = Wrappers.lambdaUpdate(Ticket.class)
                .eq(Ticket::getId, id)
                .eq(Ticket::getStatus, currentStatus)
                .set(Ticket::getStatus, targetStatus);
        int affectedRows = ticketMapper.update(null, updateWrapper);

        if (affectedRows == 0) {
            throw new BusinessException(ErrorCode.TICKET_STATUS_CONFLICT);
        }
        if (affectedRows != 1) {
            throw new IllegalStateException("更新工单状态失败：数据库更新影响行数不是 1");
        }

        ticket.setStatus(targetStatus);
        return toResponse(ticket);
    }

    @Override
    @Transactional
    public TicketAssignmentResponse assignTicket(Long ticketId, AssignTicketRequest request) {
        if (ticketId == null || ticketId <= 0) {
            throw new IllegalArgumentException("ticketId 必须为正数");
        }

        Ticket ticket = ticketMapper.selectById(ticketId);
        if (ticket == null) {
            throw new BusinessException(ErrorCode.TICKET_NOT_FOUND);
        }

        Long targetAssigneeUserId = request.assigneeUserId();
        UserAccount targetAssignee = userAccountMapper.selectById(targetAssigneeUserId);
        if (targetAssignee == null) {
            throw new BusinessException(ErrorCode.ASSIGNEE_NOT_FOUND);
        }
        if (targetAssignee.getRole() != UserRole.AGENT) {
            throw new BusinessException(ErrorCode.INVALID_ASSIGNEE_ROLE);
        }

        Long currentAssigneeUserId = ticket.getAssigneeUserId();
        if (Objects.equals(currentAssigneeUserId, targetAssigneeUserId)) {
            throw new BusinessException(ErrorCode.TICKET_ALREADY_ASSIGNED);
        }

        LambdaUpdateWrapper<Ticket> updateWrapper = Wrappers.lambdaUpdate(Ticket.class)
                .eq(Ticket::getId, ticketId)
                .set(Ticket::getAssigneeUserId, targetAssigneeUserId);
        if (currentAssigneeUserId == null) {
            updateWrapper.isNull(Ticket::getAssigneeUserId);
        } else {
            updateWrapper.eq(Ticket::getAssigneeUserId, currentAssigneeUserId);
        }

        int affectedRows = ticketMapper.update(null, updateWrapper);
        if (affectedRows == 0) {
            throw new BusinessException(ErrorCode.TICKET_ASSIGNMENT_CONFLICT);
        }
        if (affectedRows != 1) {
            throw new IllegalStateException("指派工单失败：数据库更新影响行数不是 1");
        }

        ticket.setAssigneeUserId(targetAssigneeUserId);
        return new TicketAssignmentResponse(
                ticket.getId(),
                targetAssignee.getId(),
                targetAssignee.getUsername(),
                targetAssignee.getDisplayName()
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
