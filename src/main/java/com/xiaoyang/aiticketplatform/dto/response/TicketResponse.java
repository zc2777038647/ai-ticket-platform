package com.xiaoyang.aiticketplatform.dto.response;

import com.xiaoyang.aiticketplatform.enums.TicketPriority;
import com.xiaoyang.aiticketplatform.enums.TicketStatus;

public record TicketResponse(
        Long id,
        String title,
        String description,
        String creatorName,
        TicketPriority priority,
        TicketStatus status
) {
}
