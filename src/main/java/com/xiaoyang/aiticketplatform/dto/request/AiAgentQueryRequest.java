package com.xiaoyang.aiticketplatform.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AiAgentQueryRequest(
        @NotBlank(message = "查询问题不能为空")
        @Size(max = 1000, message = "查询问题长度不能超过1000个字符")
        String query
) {
}
