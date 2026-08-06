package com.xiaoyang.aiticketplatform.exception;

import com.xiaoyang.aiticketplatform.common.ErrorCode;
import com.xiaoyang.aiticketplatform.dto.request.CreateTicketRequest;
import com.xiaoyang.aiticketplatform.dto.request.TicketPageQuery;
import com.xiaoyang.aiticketplatform.dto.request.UpdateTicketStatusRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private LocalValidatorFactoryBean validator;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .setMessageConverters(new JacksonJsonHttpMessageConverter())
                .build();
    }

    @AfterEach
    void tearDown() {
        validator.close();
    }

    @Test
    void shouldAcceptValidRequest() throws Exception {
        mockMvc.perform(post("/test/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("测试工单"))
                .andExpect(jsonPath("$.priority").value("HIGH"));
    }

    @Test
    void shouldReturnValidationErrorForBlankTitle() throws Exception {
        mockMvc.perform(post("/test/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "   ",
                                  "description": "测试描述",
                                  "creatorName": "测试用户",
                                  "priority": "HIGH"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000))
                .andExpect(jsonPath("$.message").value("请求参数校验失败"))
                .andExpect(jsonPath("$.data.title").value("标题不能为空"));
    }

    @Test
    void shouldReturnMessageNotReadableForUnknownPriority() throws Exception {
        mockMvc.perform(post("/test/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "测试工单",
                                  "description": "测试描述",
                                  "creatorName": "测试用户",
                                  "priority": "UNKNOWN"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value("请求体格式错误"))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void shouldReturnMessageNotReadableForMalformedJson() throws Exception {
        mockMvc.perform(post("/test/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"测试工单\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value("请求体格式错误"))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void shouldReturnInternalErrorWithoutExposingExceptionMessage() throws Exception {
        mockMvc.perform(get("/test/error"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(50000))
                .andExpect(jsonPath("$.message").value("服务器内部错误"))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(content().string(not(containsString("敏感内部错误"))));
    }

    @Test
    void shouldReturnNotFoundWithoutExposingExceptionInternals() throws Exception {
        mockMvc.perform(get("/test/tickets/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40400))
                .andExpect(jsonPath("$.message").value("工单不存在"))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(content().string(not(containsString("BusinessException"))))
                .andExpect(content().string(not(containsString("java.lang"))))
                .andExpect(content().string(not(containsString("stackTrace"))))
                .andExpect(content().string(not(containsString("敏感内部错误"))));
    }

    @Test
    void shouldReturnUnauthorizedForInvalidCredentials() throws Exception {
        mockMvc.perform(get("/test/auth/invalid-credentials"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100))
                .andExpect(jsonPath("$.message").value("用户名或密码错误"))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(content().string(not(containsString("用户不存在"))))
                .andExpect(content().string(not(containsString("密码不正确"))))
                .andExpect(content().string(not(containsString("BusinessException"))))
                .andExpect(content().string(not(containsString("stackTrace"))));
    }

    @Test
    void shouldReturnTooManyRequestsWithSafeRetryAfterHeader() throws Exception {
        mockMvc.perform(get("/test/auth/rate-limit"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "17"))
                .andExpect(jsonPath("$.code").value(42900))
                .andExpect(jsonPath("$.message").value("请求过于频繁，请稍后重试"))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(content().string(not(containsString("RateLimitExceededException"))))
                .andExpect(content().string(not(containsString("username"))))
                .andExpect(content().string(not(containsString("remoteAddr"))))
                .andExpect(content().string(not(containsString("stackTrace"))));
    }

    @Test
    void shouldReturnBadRequestForInvalidIdempotencyKey() throws Exception {
        mockMvc.perform(get("/test/idempotency/invalid-key"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40003))
                .andExpect(jsonPath("$.message").value("幂等键不合法"))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(content().string(not(containsString("Idempotency-Key"))));
    }

    @Test
    void shouldReturnConflictWithRetryAfterWhenIdempotencyRequestIsProcessing()
            throws Exception {
        mockMvc.perform(get("/test/idempotency/in-progress"))
                .andExpect(status().isConflict())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "17"))
                .andExpect(jsonPath("$.code").value(40906))
                .andExpect(jsonPath("$.message").value("相同请求正在处理中，请稍后重试"))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(content().string(not(containsString("ownerToken"))));
    }

    @Test
    void shouldReturnConflictWhenIdempotencyKeyIsReusedForDifferentPayload()
            throws Exception {
        mockMvc.perform(get("/test/idempotency/key-reused"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40907))
                .andExpect(jsonPath("$.message").value("幂等键已用于不同请求"))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(content().string(not(containsString("fingerprint"))));
    }

    @Test
    void shouldReturnForbiddenForAuthorizationDenied() throws Exception {
        mockMvc.perform(get("/test/auth/authorization-denied"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300))
                .andExpect(jsonPath("$.message").value("权限不足，无法执行此操作"))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(content().string(not(containsString("BusinessException"))))
                .andExpect(content().string(not(containsString("ROLE_AGENT"))))
                .andExpect(content().string(not(containsString("stackTrace"))));
    }

    @Test
    void shouldReturnConflictForInvalidTicketStatusTransition() throws Exception {
        mockMvc.perform(get("/test/tickets/invalid-status-transition"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40900))
                .andExpect(jsonPath("$.message").value("工单状态流转不合法"))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(content().string(not(containsString("BusinessException"))))
                .andExpect(content().string(not(containsString("java.lang"))))
                .andExpect(content().string(not(containsString("stackTrace"))));
    }

    @Test
    void shouldReturnConflictForConcurrentTicketStatusChange() throws Exception {
        mockMvc.perform(get("/test/tickets/status-conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40901))
                .andExpect(jsonPath("$.message").value("工单状态已发生变化，请刷新后重试"))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(content().string(not(containsString("BusinessException"))))
                .andExpect(content().string(not(containsString("java.lang"))))
                .andExpect(content().string(not(containsString("stackTrace"))));
    }

    @Test
    void shouldReturnConflictWhenUsernameAlreadyExists() throws Exception {
        mockMvc.perform(get("/test/users/username-conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40902))
                .andExpect(jsonPath("$.message").value("用户名已存在"))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(content().string(not(containsString("uk_users_username"))))
                .andExpect(content().string(not(containsString("DuplicateKeyException"))))
                .andExpect(content().string(not(containsString("BusinessException"))))
                .andExpect(content().string(not(containsString("stackTrace"))));
    }

    @Test
    void shouldReturnNotFoundWhenAssigneeDoesNotExist() throws Exception {
        assertAssignmentError(ErrorCode.ASSIGNEE_NOT_FOUND, 404);
    }

    @Test
    void shouldReturnConflictWhenAssigneeRoleIsInvalid() throws Exception {
        assertAssignmentError(ErrorCode.INVALID_ASSIGNEE_ROLE, 409);
    }

    @Test
    void shouldReturnConflictWhenTicketIsAlreadyAssigned() throws Exception {
        assertAssignmentError(ErrorCode.TICKET_ALREADY_ASSIGNED, 409);
    }

    @Test
    void shouldReturnConflictWhenTicketAssignmentIsStale() throws Exception {
        assertAssignmentError(ErrorCode.TICKET_ASSIGNMENT_CONFLICT, 409);
    }

    @Test
    void shouldSafelyHandleUnmappedBusinessErrorCode() throws Exception {
        mockMvc.perform(get("/test/tickets/unmapped-business-error"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(50000))
                .andExpect(jsonPath("$.message").value("服务器内部错误"))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(content().string(not(containsString("请求参数校验失败"))))
                .andExpect(content().string(not(containsString("BusinessException"))));
    }

    @Test
    void shouldReturnValidationErrorForNonPositivePathVariable() throws Exception {
        mockMvc.perform(get("/test/ids/0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000))
                .andExpect(jsonPath("$.message").value("请求参数校验失败"))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(content().string(not(containsString("HandlerMethodValidationException"))));
    }

    @Test
    void shouldReturnFieldErrorsForRequestBodyValidatedByHandlerMethodValidation() throws Exception {
        mockMvc.perform(patch("/test/tickets/100/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": null
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000))
                .andExpect(jsonPath("$.message").value("请求参数校验失败"))
                .andExpect(jsonPath("$.data.status").value("目标状态不能为空"));
    }

    @Test
    void shouldReturnParameterFormatErrorForNonNumericPathVariable() throws Exception {
        mockMvc.perform(get("/test/ids/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40002))
                .andExpect(jsonPath("$.message").value("请求参数格式错误"))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(content().string(not(containsString("MethodArgumentTypeMismatchException"))));
    }

    @Test
    void shouldReturnValidationErrorForInvalidModelAttribute() throws Exception {
        MvcResult result = mockMvc.perform(get("/test/pages").param("page", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000))
                .andExpect(jsonPath("$.message").value("请求参数校验失败"))
                .andExpect(jsonPath("$.data.page").value("页码必须大于等于1"))
                .andReturn();

        MethodArgumentNotValidException exception = assertInstanceOf(
                MethodArgumentNotValidException.class,
                result.getResolvedException()
        );
        FieldError fieldError = exception.getBindingResult().getFieldError("page");
        assertNotNull(fieldError);
        assertFalse(fieldError.isBindingFailure());
    }

    @Test
    void shouldReturnParameterFormatErrorForNonNumericModelAttributeField() throws Exception {
        MvcResult result = mockMvc.perform(get("/test/pages").param("page", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40002))
                .andExpect(jsonPath("$.message").value("请求参数格式错误"))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andReturn();

        assertModelAttributeBindingFailure(result, "page");
    }

    @Test
    void shouldReturnParameterFormatErrorForUnknownModelAttributeEnum() throws Exception {
        MvcResult result = mockMvc.perform(get("/test/pages").param("status", "UNKNOWN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40002))
                .andExpect(jsonPath("$.message").value("请求参数格式错误"))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andReturn();

        assertModelAttributeBindingFailure(result, "status");
    }

    private static void assertModelAttributeBindingFailure(MvcResult result, String fieldName) {
        MethodArgumentNotValidException exception = assertInstanceOf(
                MethodArgumentNotValidException.class,
                result.getResolvedException()
        );
        FieldError fieldError = exception.getBindingResult().getFieldError(fieldName);
        assertNotNull(fieldError);
        assertTrue(fieldError.isBindingFailure());
    }

    private void assertAssignmentError(ErrorCode errorCode, int expectedHttpStatus) throws Exception {
        mockMvc.perform(get("/test/tickets/assignment-errors/{errorCode}", errorCode.name()))
                .andExpect(status().is(expectedHttpStatus))
                .andExpect(jsonPath("$.code").value(errorCode.getCode()))
                .andExpect(jsonPath("$.message").value(errorCode.getMessage()))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(content().string(not(containsString("BusinessException"))))
                .andExpect(content().string(not(containsString("DataIntegrityViolationException"))))
                .andExpect(content().string(not(containsString("SQL"))))
                .andExpect(content().string(not(containsString("fk_tickets_assignee_user"))))
                .andExpect(content().string(not(containsString("ROLE_AGENT"))))
                .andExpect(content().string(not(containsString("stackTrace"))));
    }

    private static String validRequestJson() {
        return """
                {
                  "title": "测试工单",
                  "description": "测试描述",
                  "creatorName": "测试用户",
                  "priority": "HIGH"
                }
                """;
    }

    @RestController
    static class TestController {

        @PostMapping("/test/tickets")
        CreateTicketRequest createTicket(@Valid @RequestBody CreateTicketRequest request) {
            return request;
        }

        @GetMapping("/test/error")
        void throwUnexpectedException() {
            throw new IllegalStateException("敏感内部错误");
        }

        @GetMapping("/test/tickets/not-found")
        void throwTicketNotFoundException() {
            throw new BusinessException(ErrorCode.TICKET_NOT_FOUND);
        }

        @GetMapping("/test/auth/invalid-credentials")
        void throwInvalidCredentials() {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        @GetMapping("/test/auth/rate-limit")
        void throwRateLimitExceeded() {
            throw new RateLimitExceededException(17);
        }

        @GetMapping("/test/idempotency/invalid-key")
        void throwInvalidIdempotencyKey() {
            throw new InvalidIdempotencyKeyException();
        }

        @GetMapping("/test/idempotency/in-progress")
        void throwIdempotencyRequestInProgress() {
            throw new IdempotencyRequestInProgressException(17);
        }

        @GetMapping("/test/idempotency/key-reused")
        void throwIdempotencyKeyReused() {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REUSED);
        }

        @GetMapping("/test/auth/authorization-denied")
        void throwAuthorizationDenied() {
            throw new BusinessException(ErrorCode.AUTHORIZATION_DENIED);
        }

        @GetMapping("/test/tickets/invalid-status-transition")
        void throwInvalidStatusTransition() {
            throw new BusinessException(ErrorCode.INVALID_TICKET_STATUS_TRANSITION);
        }

        @GetMapping("/test/tickets/status-conflict")
        void throwTicketStatusConflict() {
            throw new BusinessException(ErrorCode.TICKET_STATUS_CONFLICT);
        }

        @GetMapping("/test/users/username-conflict")
        void throwUsernameAlreadyExists() {
            throw new BusinessException(ErrorCode.USERNAME_ALREADY_EXISTS);
        }

        @GetMapping("/test/tickets/assignment-errors/{errorCode}")
        void throwAssignmentError(@PathVariable ErrorCode errorCode) {
            throw new BusinessException(errorCode);
        }

        @GetMapping("/test/tickets/unmapped-business-error")
        void throwUnmappedBusinessError() {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }

        @GetMapping("/test/ids/{id}")
        Long getById(@PathVariable @Positive(message = "工单ID必须为正数") Long id) {
            return id;
        }

        @PatchMapping("/test/tickets/{id}/status")
        UpdateTicketStatusRequest updateTicketStatus(
                @PathVariable @Positive(message = "工单ID必须为正数") Long id,
                @Valid @RequestBody UpdateTicketStatusRequest request
        ) {
            return request;
        }

        @GetMapping("/test/pages")
        TicketPageQuery pageTickets(@Valid @ModelAttribute TicketPageQuery query) {
            return query;
        }
    }
}
