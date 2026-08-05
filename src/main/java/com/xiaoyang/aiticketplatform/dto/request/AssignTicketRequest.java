package com.xiaoyang.aiticketplatform.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AssignTicketRequest(
        @NotNull(message = "处理人ID不能为空")
        @Positive(message = "处理人ID必须为正数")
        Long assigneeUserId
) {
}
