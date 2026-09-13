package com.xiaoyang.aiticketplatform.ai;

import com.xiaoyang.aiticketplatform.dto.request.ai.AiAnalysisRequest;
import com.xiaoyang.aiticketplatform.dto.request.ai.AiReplyDraftRequest;
import com.xiaoyang.aiticketplatform.dto.response.ai.AiAgentResponse;
import com.xiaoyang.aiticketplatform.dto.response.ai.AiAnalysisResponse;
import com.xiaoyang.aiticketplatform.dto.response.ai.AiReplyDraftResponse;

public interface AiServiceClient {

    AiAnalysisResponse analyze(AiAnalysisRequest request);

    AiReplyDraftResponse draftReply(AiReplyDraftRequest request);

    AiAgentResponse runAgent(Long ticketId, String query);
}
