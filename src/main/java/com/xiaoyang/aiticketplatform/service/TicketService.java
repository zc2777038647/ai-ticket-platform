package com.xiaoyang.aiticketplatform.service;

import com.xiaoyang.aiticketplatform.dto.request.CreateTicketRequest;
import com.xiaoyang.aiticketplatform.dto.request.AssignTicketRequest;
import com.xiaoyang.aiticketplatform.dto.request.TicketPageQuery;
import com.xiaoyang.aiticketplatform.dto.request.UpdateTicketStatusRequest;
import com.xiaoyang.aiticketplatform.dto.response.PageResponse;
import com.xiaoyang.aiticketplatform.dto.response.TicketAssignmentResponse;
import com.xiaoyang.aiticketplatform.dto.response.TicketResponse;
import com.xiaoyang.aiticketplatform.dto.response.TicketOperationLogResponse;
import com.xiaoyang.aiticketplatform.dto.request.ai.AiAgentRequest;
import com.xiaoyang.aiticketplatform.dto.response.ai.AiAgentResponse;
import com.xiaoyang.aiticketplatform.dto.response.ai.AiAnalysisResponse;
import com.xiaoyang.aiticketplatform.dto.response.ai.AiReplyDraftResponse;
import com.xiaoyang.aiticketplatform.enums.UserRole;

public interface TicketService {

    TicketResponse createTicket(CreateTicketRequest request, Long creatorUserId);

    TicketResponse getTicketById(Long id, Long requesterUserId, UserRole requesterRole);

    PageResponse<TicketResponse> pageTickets(TicketPageQuery query);

    PageResponse<TicketResponse> pageMyTickets(TicketPageQuery query, Long creatorUserId);

    TicketResponse updateTicketStatus(
            Long id,
            UpdateTicketStatusRequest request,
            Long operatorUserId
    );

    TicketAssignmentResponse assignTicket(
            Long ticketId,
            AssignTicketRequest request,
            Long operatorUserId
    );

    AiAnalysisResponse analyzeTicket(Long id, Long requesterUserId, UserRole requesterRole);

    AiReplyDraftResponse draftTicketReply(Long id, Long requesterUserId, UserRole requesterRole);

    AiAgentResponse runTicketAgent(
            Long id,
            AiAgentRequest request,
            Long requesterUserId,
            UserRole requesterRole
    );

    TicketResponse getTicketForAiTool(Long id);

    java.util.List<TicketOperationLogResponse> getTicketHistoryForAiTool(Long id);
}
