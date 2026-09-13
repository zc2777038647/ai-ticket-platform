package com.xiaoyang.aiticketplatform.dto.response;

import com.xiaoyang.aiticketplatform.enums.TicketOperationType;

import java.time.LocalDateTime;

public record TicketOperationLogResponse(
        Long id,
        Long ticketId,
        Long operatorUserId,
        TicketOperationType operationType,
        String beforeValue,
        String afterValue,
        LocalDateTime createdAt
) {
}
