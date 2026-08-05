package com.xiaoyang.aiticketplatform.dto.response;

public record TicketAssignmentResponse(
        Long ticketId,
        Long assigneeUserId,
        String assigneeUsername,
        String assigneeDisplayName
) {
}
