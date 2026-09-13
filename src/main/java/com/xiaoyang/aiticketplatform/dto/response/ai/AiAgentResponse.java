package com.xiaoyang.aiticketplatform.dto.response.ai;

import java.util.List;

public record AiAgentResponse(
        String answer,
        List<AiSourceResponse> sources,
        int toolCalls
) {
}
