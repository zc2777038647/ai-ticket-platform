package com.xiaoyang.aiticketplatform.dto.request;

import com.xiaoyang.aiticketplatform.enums.TicketPriority;
import com.xiaoyang.aiticketplatform.enums.TicketStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record TicketPageQuery(
        @Min(value = 1, message = "页码必须大于等于1")
        Integer page,

        @Min(value = 1, message = "每页数量必须大于等于1")
        @Max(value = 100, message = "每页数量不能超过100")
        Integer size,

        TicketStatus status,

        TicketPriority priority,

        @Size(max = 64, message = "创建人名称长度不能超过64个字符")
        String creatorName,

        @Size(max = 100, message = "关键词长度不能超过100个字符")
        String keyword
) {
    public TicketPageQuery {
        page = page == null ? 1 : page;
        size = size == null ? 20 : size;
    }
}
