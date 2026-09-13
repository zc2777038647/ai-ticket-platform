package com.xiaoyang.aiticketplatform.dto.request.ai;

public record AiReplyDraftRequest(
        Long ticketId,
        String title,
        String description,
        String creatorName
) {
}
