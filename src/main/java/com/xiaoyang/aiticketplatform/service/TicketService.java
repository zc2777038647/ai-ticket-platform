package com.xiaoyang.aiticketplatform.service;

import com.xiaoyang.aiticketplatform.dto.request.CreateTicketRequest;
import com.xiaoyang.aiticketplatform.dto.request.AssignTicketRequest;
import com.xiaoyang.aiticketplatform.dto.request.TicketPageQuery;
import com.xiaoyang.aiticketplatform.dto.request.UpdateTicketStatusRequest;
import com.xiaoyang.aiticketplatform.dto.response.PageResponse;
import com.xiaoyang.aiticketplatform.dto.response.TicketAssignmentResponse;
import com.xiaoyang.aiticketplatform.dto.response.TicketResponse;
import com.xiaoyang.aiticketplatform.enums.UserRole;

public interface TicketService {

    TicketResponse createTicket(CreateTicketRequest request, Long creatorUserId);

    TicketResponse getTicketById(Long id, Long requesterUserId, UserRole requesterRole);

    PageResponse<TicketResponse> pageTickets(TicketPageQuery query);

    PageResponse<TicketResponse> pageMyTickets(TicketPageQuery query, Long creatorUserId);

    TicketResponse updateTicketStatus(Long id, UpdateTicketStatusRequest request);

    TicketAssignmentResponse assignTicket(Long ticketId, AssignTicketRequest request);
}
