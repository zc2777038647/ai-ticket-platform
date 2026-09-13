package com.xiaoyang.aiticketplatform.dto.response.ai;

import com.xiaoyang.aiticketplatform.enums.TicketPriority;

import java.math.BigDecimal;

public record AiAnalysisResponse(
        String category,
        TicketPriority suggestedPriority,
        String reason,
        BigDecimal confidence
) {
}
