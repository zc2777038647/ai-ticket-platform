package com.xiaoyang.aiticketplatform.dto.request.ai;

import com.xiaoyang.aiticketplatform.enums.TicketPriority;

public record AiAnalysisRequest(
        Long ticketId,
        String title,
        String description,
        TicketPriority currentPriority
) {
}
