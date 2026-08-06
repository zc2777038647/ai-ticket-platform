package com.xiaoyang.aiticketplatform.idempotency;

import com.xiaoyang.aiticketplatform.common.ErrorCode;
import com.xiaoyang.aiticketplatform.config.CreateTicketIdempotencyProperties;
import com.xiaoyang.aiticketplatform.dto.request.CreateTicketRequest;
import com.xiaoyang.aiticketplatform.dto.response.TicketResponse;
import com.xiaoyang.aiticketplatform.exception.BusinessException;
import com.xiaoyang.aiticketplatform.exception.IdempotencyRequestInProgressException;
import com.xiaoyang.aiticketplatform.service.TicketService;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.Objects;
import java.util.UUID;

@Component
public class CreateTicketIdempotencyCoordinator {

    private final TicketService ticketService;
    private final RedisCreateTicketIdempotencyStore idempotencyStore;
    private final CreateTicketRequestFingerprint requestFingerprint;
    private final CreateTicketIdempotencyProperties properties;
    private final ObjectMapper objectMapper;

    public CreateTicketIdempotencyCoordinator(
            TicketService ticketService,
            RedisCreateTicketIdempotencyStore idempotencyStore,
            CreateTicketRequestFingerprint requestFingerprint,
            CreateTicketIdempotencyProperties properties,
            ObjectMapper objectMapper
    ) {
        this.ticketService = ticketService;
        this.idempotencyStore = idempotencyStore;
        this.requestFingerprint = requestFingerprint;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public TicketResponse createTicket(
            Long creatorUserId,
            String idempotencyKey,
            CreateTicketRequest request
    ) {
        if (!properties.isEnabled()) {
            return ticketService.createTicket(request, creatorUserId);
        }

        validateInputs(creatorUserId, request);
        String fingerprint = requestFingerprint.fingerprint(request);
        String ownerToken = UUID.randomUUID().toString();
        IdempotencyAcquireResult acquireResult = idempotencyStore.acquire(
                creatorUserId,
                idempotencyKey,
                fingerprint,
                ownerToken
        );

        return switch (acquireResult.status()) {
            case ACQUIRED -> createAcquiredTicket(
                    creatorUserId, idempotencyKey, request, fingerprint, ownerToken);
            case IN_PROGRESS -> throw new IdempotencyRequestInProgressException(
                    acquireResult.retryAfterSeconds());
            case SUCCEEDED -> deserializeResponse(acquireResult.responsePayload());
            case PAYLOAD_MISMATCH -> throw new BusinessException(
                    ErrorCode.IDEMPOTENCY_KEY_REUSED);
            case DISABLED -> throw new IllegalStateException(
                    "幂等配置已开启，但 Redis Store 返回 DISABLED");
        };
    }

    private TicketResponse createAcquiredTicket(
            Long creatorUserId,
            String idempotencyKey,
            CreateTicketRequest request,
            String fingerprint,
            String ownerToken
    ) {
        TicketResponse response;
        try {
            response = ticketService.createTicket(request, creatorUserId);
        } catch (RuntimeException businessException) {
            releaseAfterBusinessFailure(
                    creatorUserId,
                    idempotencyKey,
                    fingerprint,
                    ownerToken,
                    businessException
            );
            throw businessException;
        }

        String responsePayload = serializeResponse(response);
        idempotencyStore.markSucceeded(
                creatorUserId,
                idempotencyKey,
                fingerprint,
                ownerToken,
                responsePayload
        );
        return response;
    }

    private void releaseAfterBusinessFailure(
            Long creatorUserId,
            String idempotencyKey,
            String fingerprint,
            String ownerToken,
            RuntimeException businessException
    ) {
        try {
            idempotencyStore.releaseProcessing(
                    creatorUserId,
                    idempotencyKey,
                    fingerprint,
                    ownerToken
            );
        } catch (RuntimeException releaseException) {
            businessException.addSuppressed(releaseException);
        }
    }

    private String serializeResponse(TicketResponse response) {
        if (response == null) {
            throw new IllegalStateException("创建工单服务返回了空响应");
        }
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JacksonException exception) {
            throw new IllegalStateException("无法序列化创建工单成功响应");
        }
    }

    private TicketResponse deserializeResponse(String responsePayload) {
        if (responsePayload == null || responsePayload.isBlank()) {
            throw new IllegalStateException("Redis 幂等成功响应为空");
        }
        try {
            TicketResponse response = objectMapper.readValue(responsePayload, TicketResponse.class);
            if (response == null) {
                throw new IllegalStateException("Redis 幂等成功响应解析为空");
            }
            return response;
        } catch (JacksonException exception) {
            throw new IllegalStateException("Redis 幂等成功响应无法解析");
        }
    }

    private void validateInputs(Long creatorUserId, CreateTicketRequest request) {
        if (creatorUserId == null || creatorUserId <= 0) {
            throw new IllegalArgumentException("creatorUserId 必须为正数");
        }
        Objects.requireNonNull(request, "创建工单请求不能为空");
    }
}
