package com.xiaoyang.aiticketplatform.dto.request;

import com.xiaoyang.aiticketplatform.enums.TicketStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateTicketStatusRequest(
        @NotNull(message = "目标状态不能为空")
        TicketStatus status
) {
}
