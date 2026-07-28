package com.xiaoyang.aiticketplatform.dto.request;

import com.xiaoyang.aiticketplatform.enums.TicketPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateTicketRequest(
        @NotBlank(message = "标题不能为空")
        @Size(max = 120, message = "标题长度不能超过120个字符")
        String title,

        @NotBlank(message = "描述不能为空")
        @Size(max = 2000, message = "描述长度不能超过2000个字符")
        String description,

        @NotBlank(message = "创建人名称不能为空")
        @Size(max = 64, message = "创建人名称长度不能超过64个字符")
        String creatorName,

        @NotNull(message = "优先级不能为空")
        TicketPriority priority
) {
}
