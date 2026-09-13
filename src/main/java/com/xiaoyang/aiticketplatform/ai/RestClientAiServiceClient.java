package com.xiaoyang.aiticketplatform.ai;

import com.xiaoyang.aiticketplatform.common.ErrorCode;
import com.xiaoyang.aiticketplatform.config.AiServiceProperties;
import com.xiaoyang.aiticketplatform.dto.request.ai.AiAgentRequest;
import com.xiaoyang.aiticketplatform.dto.request.ai.AiAnalysisRequest;
import com.xiaoyang.aiticketplatform.dto.request.ai.AiReplyDraftRequest;
import com.xiaoyang.aiticketplatform.dto.response.ai.AiAgentResponse;
import com.xiaoyang.aiticketplatform.dto.response.ai.AiAnalysisResponse;
import com.xiaoyang.aiticketplatform.dto.response.ai.AiReplyDraftResponse;
import com.xiaoyang.aiticketplatform.exception.BusinessException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class RestClientAiServiceClient implements AiServiceClient {

    private final HttpClient httpClient;
    private final AiServiceProperties properties;
    private final ObjectMapper objectMapper;

    public RestClientAiServiceClient(
            HttpClient httpClient,
            AiServiceProperties properties,
            ObjectMapper objectMapper
    ) {
        this.httpClient = httpClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public AiAnalysisResponse analyze(AiAnalysisRequest request) {
        return post("/internal/ai/ticket-analysis", request, AiAnalysisResponse.class);
    }

    @Override
    public AiReplyDraftResponse draftReply(AiReplyDraftRequest request) {
        return post("/internal/ai/ticket-reply-draft", request, AiReplyDraftResponse.class);
    }

    @Override
    public AiAgentResponse runAgent(Long ticketId, String query) {
        return post("/internal/ai/agent", new AiAgentRequest(ticketId, query), AiAgentResponse.class);
    }

    private <T> T post(String path, Object request, Class<T> responseType) {
        if (!properties.isEnabled()) {
            throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE);
        }
        try {
            String requestJson = objectMapper.writeValueAsString(request);
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(properties.getBaseUrl().replaceAll("/$", "") + path))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("X-Internal-AI-Token", properties.getInternalToken())
                    .timeout(properties.getReadTimeout())
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                    .build();
            HttpResponse<String> response = httpClient.send(
                    httpRequest,
                    HttpResponse.BodyHandlers.ofString()
            );
            if (response.statusCode() >= 400 && response.statusCode() < 500) {
                throw new BusinessException(ErrorCode.AI_RESPONSE_INVALID);
            }
            if (response.statusCode() >= 500) {
                throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE);
            }
            T result = objectMapper.readValue(response.body(), responseType);
            if (result == null) {
                throw new BusinessException(ErrorCode.AI_RESPONSE_INVALID);
            }
            return result;
        } catch (BusinessException exception) {
            throw exception;
        } catch (JacksonException exception) {
            throw new BusinessException(ErrorCode.AI_RESPONSE_INVALID);
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE);
        }
    }
}
