package com.xiaoyang.aiticketplatform.idempotency;

import com.xiaoyang.aiticketplatform.common.ErrorCode;
import com.xiaoyang.aiticketplatform.config.CreateTicketIdempotencyProperties;
import com.xiaoyang.aiticketplatform.dto.request.CreateTicketRequest;
import com.xiaoyang.aiticketplatform.dto.response.TicketResponse;
import com.xiaoyang.aiticketplatform.enums.TicketPriority;
import com.xiaoyang.aiticketplatform.enums.TicketStatus;
import com.xiaoyang.aiticketplatform.exception.BusinessException;
import com.xiaoyang.aiticketplatform.exception.IdempotencyRequestInProgressException;
import com.xiaoyang.aiticketplatform.service.TicketService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateTicketIdempotencyCoordinatorTest {

    private static final Long USER_ID = 100L;
    private static final String IDEMPOTENCY_KEY = "request-123";
    private static final String FINGERPRINT = "a".repeat(64);
    private static final String PAYLOAD = "{\"id\":100}";

    @Mock
    private TicketService ticketService;
    @Mock
    private RedisCreateTicketIdempotencyStore store;
    @Mock
    private CreateTicketRequestFingerprint fingerprintGenerator;
    @Mock
    private CreateTicketIdempotencyProperties properties;
    @Mock
    private ObjectMapper objectMapper;

    private CreateTicketIdempotencyCoordinator coordinator;

    @BeforeEach
    void setUp() {
        coordinator = new CreateTicketIdempotencyCoordinator(
                ticketService, store, fingerprintGenerator, properties, objectMapper);
    }

    @Test
    void shouldCallServiceDirectlyAndOnlyWhenDisabled() {
        CreateTicketRequest request = request();
        TicketResponse response = response();
        when(properties.isEnabled()).thenReturn(false);
        when(ticketService.createTicket(request, USER_ID)).thenReturn(response);

        assertSame(response, coordinator.createTicket(USER_ID, null, request));

        verify(ticketService).createTicket(request, USER_ID);
        verifyNoInteractions(fingerprintGenerator, store, objectMapper);
    }

    @Test
    void shouldCreateSerializeAndCompleteWithSameFingerprintAndOwnerWhenAcquired()
            throws JacksonException {
        prepareEnabled();
        TicketResponse response = response();
        when(store.acquire(eq(USER_ID), eq(IDEMPOTENCY_KEY), eq(FINGERPRINT), anyString()))
                .thenReturn(result(IdempotencyAcquireStatus.ACQUIRED, 0, null));
        when(ticketService.createTicket(request(), USER_ID)).thenReturn(response);
        when(objectMapper.writeValueAsString(response)).thenReturn(PAYLOAD);

        assertSame(response, coordinator.createTicket(USER_ID, IDEMPOTENCY_KEY, request()));

        ArgumentCaptor<String> acquireFingerprint = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> acquireOwner = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> completeFingerprint = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> completeOwner = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> responsePayload = ArgumentCaptor.forClass(String.class);
        verify(store).acquire(
                eq(USER_ID), eq(IDEMPOTENCY_KEY), acquireFingerprint.capture(), acquireOwner.capture());
        verify(store).markSucceeded(
                eq(USER_ID), eq(IDEMPOTENCY_KEY), completeFingerprint.capture(),
                completeOwner.capture(), responsePayload.capture());
        assertNotNull(acquireOwner.getValue());
        assertFalse(acquireOwner.getValue().isBlank());
        assertEquals(FINGERPRINT, acquireFingerprint.getValue());
        assertEquals(acquireFingerprint.getValue(), completeFingerprint.getValue());
        assertEquals(acquireOwner.getValue(), completeOwner.getValue());
        assertEquals(PAYLOAD, responsePayload.getValue());
    }

    @Test
    void shouldRejectInProgressWithoutCallingService() {
        prepareEnabled();
        when(store.acquire(eq(USER_ID), eq(IDEMPOTENCY_KEY), eq(FINGERPRINT), anyString()))
                .thenReturn(result(IdempotencyAcquireStatus.IN_PROGRESS, 17, null));

        IdempotencyRequestInProgressException exception = assertThrows(
                IdempotencyRequestInProgressException.class,
                () -> coordinator.createTicket(USER_ID, IDEMPOTENCY_KEY, request()));

        assertEquals(17, exception.getRetryAfterSeconds());
        verifyNoInteractions(ticketService, objectMapper);
    }

    @Test
    void shouldDeserializeSucceededResponseWithoutCallingService() throws JacksonException {
        prepareEnabled();
        TicketResponse response = response();
        when(store.acquire(eq(USER_ID), eq(IDEMPOTENCY_KEY), eq(FINGERPRINT), anyString()))
                .thenReturn(result(IdempotencyAcquireStatus.SUCCEEDED, 0, PAYLOAD));
        when(objectMapper.readValue(PAYLOAD, TicketResponse.class)).thenReturn(response);

        assertSame(response, coordinator.createTicket(USER_ID, IDEMPOTENCY_KEY, request()));

        verifyNoInteractions(ticketService);
        verify(objectMapper).readValue(PAYLOAD, TicketResponse.class);
    }

    @Test
    void shouldRejectPayloadMismatchWithBusinessError() {
        prepareEnabled();
        when(store.acquire(eq(USER_ID), eq(IDEMPOTENCY_KEY), eq(FINGERPRINT), anyString()))
                .thenReturn(result(IdempotencyAcquireStatus.PAYLOAD_MISMATCH, 0, null));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> coordinator.createTicket(USER_ID, IDEMPOTENCY_KEY, request()));

        assertEquals(ErrorCode.IDEMPOTENCY_KEY_REUSED, exception.getErrorCode());
        verifyNoInteractions(ticketService, objectMapper);
    }

    @Test
    void shouldRejectUnexpectedDisabledStoreResult() {
        prepareEnabled();
        when(store.acquire(eq(USER_ID), eq(IDEMPOTENCY_KEY), eq(FINGERPRINT), anyString()))
                .thenReturn(result(IdempotencyAcquireStatus.DISABLED, 0, null));

        assertThrows(IllegalStateException.class,
                () -> coordinator.createTicket(USER_ID, IDEMPOTENCY_KEY, request()));
        verifyNoInteractions(ticketService, objectMapper);
    }

    @Test
    void shouldFailClosedWhenAcquireFails() {
        prepareEnabled();
        DataAccessResourceFailureException failure =
                new DataAccessResourceFailureException("redis unavailable");
        when(store.acquire(eq(USER_ID), eq(IDEMPOTENCY_KEY), eq(FINGERPRINT), anyString()))
                .thenThrow(failure);

        assertSame(failure, assertThrows(DataAccessResourceFailureException.class,
                () -> coordinator.createTicket(USER_ID, IDEMPOTENCY_KEY, request())));
        verifyNoInteractions(ticketService, objectMapper);
    }

    @Test
    void shouldReleaseProcessingAndRethrowOriginalBusinessFailure() {
        prepareAcquired();
        BusinessException failure = new BusinessException(ErrorCode.TICKET_NOT_FOUND);
        when(ticketService.createTicket(request(), USER_ID)).thenThrow(failure);
        when(store.releaseProcessing(eq(USER_ID), eq(IDEMPOTENCY_KEY), eq(FINGERPRINT), anyString()))
                .thenReturn(true);

        assertSame(failure, assertThrows(BusinessException.class,
                () -> coordinator.createTicket(USER_ID, IDEMPOTENCY_KEY, request())));
        verify(store).releaseProcessing(
                eq(USER_ID), eq(IDEMPOTENCY_KEY), eq(FINGERPRINT), anyString());
    }

    @Test
    void shouldKeepOriginalFailureWhenReleaseReturnsFalse() {
        prepareAcquired();
        IllegalStateException failure = new IllegalStateException("business failure");
        when(ticketService.createTicket(request(), USER_ID)).thenThrow(failure);
        when(store.releaseProcessing(eq(USER_ID), eq(IDEMPOTENCY_KEY), eq(FINGERPRINT), anyString()))
                .thenReturn(false);

        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> coordinator.createTicket(USER_ID, IDEMPOTENCY_KEY, request())));
    }

    @Test
    void shouldSuppressReleaseFailureUnderOriginalBusinessFailure() {
        prepareAcquired();
        IllegalStateException businessFailure = new IllegalStateException("business failure");
        IllegalStateException releaseFailure = new IllegalStateException("release failure");
        when(ticketService.createTicket(request(), USER_ID)).thenThrow(businessFailure);
        when(store.releaseProcessing(eq(USER_ID), eq(IDEMPOTENCY_KEY), eq(FINGERPRINT), anyString()))
                .thenThrow(releaseFailure);

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> coordinator.createTicket(USER_ID, IDEMPOTENCY_KEY, request()));

        assertSame(businessFailure, thrown);
        assertEquals(1, thrown.getSuppressed().length);
        assertSame(releaseFailure, thrown.getSuppressed()[0]);
    }

    @Test
    void shouldNotReleaseAfterResponseSerializationFailure() throws JacksonException {
        prepareAcquired();
        when(ticketService.createTicket(request(), USER_ID)).thenReturn(response());
        when(objectMapper.writeValueAsString(response())).thenThrow(jsonException());

        assertThrows(IllegalStateException.class,
                () -> coordinator.createTicket(USER_ID, IDEMPOTENCY_KEY, request()));

        verify(store, never()).releaseProcessing(any(), any(), any(), any());
        verify(store, never()).markSucceeded(any(), any(), any(), any(), any());
    }

    @Test
    void shouldNotReleaseAfterMarkSucceededFailure() throws JacksonException {
        prepareAcquired();
        DataAccessResourceFailureException redisFailure =
                new DataAccessResourceFailureException("redis unavailable");
        when(ticketService.createTicket(request(), USER_ID)).thenReturn(response());
        when(objectMapper.writeValueAsString(response())).thenReturn(PAYLOAD);
        org.mockito.Mockito.doThrow(redisFailure).when(store).markSucceeded(
                eq(USER_ID), eq(IDEMPOTENCY_KEY), eq(FINGERPRINT), anyString(), eq(PAYLOAD));

        assertSame(redisFailure, assertThrows(DataAccessResourceFailureException.class,
                () -> coordinator.createTicket(USER_ID, IDEMPOTENCY_KEY, request())));
        verify(store, never()).releaseProcessing(any(), any(), any(), any());
    }

    @Test
    void shouldRejectBlankSucceededPayload() {
        prepareEnabled();
        IdempotencyAcquireResult invalidResult = org.mockito.Mockito.mock(
                IdempotencyAcquireResult.class);
        when(invalidResult.status()).thenReturn(IdempotencyAcquireStatus.SUCCEEDED);
        when(invalidResult.responsePayload()).thenReturn(" ");
        when(store.acquire(eq(USER_ID), eq(IDEMPOTENCY_KEY), eq(FINGERPRINT), anyString()))
                .thenReturn(invalidResult);

        assertThrows(IllegalStateException.class,
                () -> coordinator.createTicket(USER_ID, IDEMPOTENCY_KEY, request()));
        verifyNoInteractions(ticketService, objectMapper);
    }

    @Test
    void shouldRejectCorruptSucceededPayload() throws JacksonException {
        prepareEnabled();
        when(store.acquire(eq(USER_ID), eq(IDEMPOTENCY_KEY), eq(FINGERPRINT), anyString()))
                .thenReturn(result(IdempotencyAcquireStatus.SUCCEEDED, 0, PAYLOAD));
        when(objectMapper.readValue(PAYLOAD, TicketResponse.class)).thenThrow(jsonException());

        assertThrows(IllegalStateException.class,
                () -> coordinator.createTicket(USER_ID, IDEMPOTENCY_KEY, request()));
        verifyNoInteractions(ticketService);
    }

    @Test
    void shouldRejectInvalidCreatorBeforeIdempotencyWork() {
        when(properties.isEnabled()).thenReturn(true);

        assertThrows(IllegalArgumentException.class,
                () -> coordinator.createTicket(0L, IDEMPOTENCY_KEY, request()));
        verifyNoInteractions(fingerprintGenerator, store, ticketService, objectMapper);
    }

    @Test
    void shouldRejectNullRequestBeforeIdempotencyWork() {
        when(properties.isEnabled()).thenReturn(true);

        assertThrows(NullPointerException.class,
                () -> coordinator.createTicket(USER_ID, IDEMPOTENCY_KEY, null));
        verifyNoInteractions(fingerprintGenerator, store, ticketService, objectMapper);
    }

    private void prepareEnabled() {
        when(properties.isEnabled()).thenReturn(true);
        when(fingerprintGenerator.fingerprint(request())).thenReturn(FINGERPRINT);
    }

    private void prepareAcquired() {
        prepareEnabled();
        when(store.acquire(eq(USER_ID), eq(IDEMPOTENCY_KEY), eq(FINGERPRINT), anyString()))
                .thenReturn(result(IdempotencyAcquireStatus.ACQUIRED, 0, null));
    }

    private static IdempotencyAcquireResult result(
            IdempotencyAcquireStatus status,
            long retryAfter,
            String payload
    ) {
        return new IdempotencyAcquireResult(status, retryAfter, payload);
    }

    private static CreateTicketRequest request() {
        return new CreateTicketRequest(
                "测试工单", "测试描述", "测试用户", TicketPriority.HIGH);
    }

    private static TicketResponse response() {
        return new TicketResponse(
                100L, "测试工单", "测试描述", "测试用户",
                TicketPriority.HIGH, TicketStatus.OPEN);
    }

    private static JacksonException jsonException() {
        try {
            new ObjectMapper().readTree("{");
            throw new AssertionError("Expected invalid JSON");
        } catch (JacksonException exception) {
            return exception;
        }
    }
}
