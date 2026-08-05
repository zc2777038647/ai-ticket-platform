package com.xiaoyang.aiticketplatform.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ApiResponseTest {

    @Test
    void shouldCreateSuccessResponse() {
        ApiResponse<String> response = ApiResponse.success("created-ticket");

        assertEquals(0, response.code());
        assertEquals("success", response.message());
        assertEquals("created-ticket", response.data());
    }

    @Test
    void shouldCreateFailureResponseWithoutData() {
        ApiResponse<Void> response = ApiResponse.failure(ErrorCode.INTERNAL_ERROR);

        assertEquals(50000, response.code());
        assertEquals("服务器内部错误", response.message());
        assertNull(response.data());
    }

    @Test
    void shouldCreateTicketNotFoundResponseWithoutData() {
        ApiResponse<Void> response = ApiResponse.failure(ErrorCode.TICKET_NOT_FOUND);

        assertEquals(40400, response.code());
        assertEquals("工单不存在", response.message());
        assertNull(response.data());
    }

    @Test
    void shouldExposeInvalidCredentialsErrorCode() {
        assertErrorCode(
                ErrorCode.INVALID_CREDENTIALS,
                40100,
                "用户名或密码错误"
        );
    }

    @Test
    void shouldExposeAuthorizationDeniedErrorCode() {
        assertErrorCode(
                ErrorCode.AUTHORIZATION_DENIED,
                40300,
                "权限不足，无法执行此操作"
        );
    }

    @Test
    void shouldExposeTicketStatusBusinessErrorCodes() {
        assertAll(
                () -> assertErrorCode(
                        ErrorCode.INVALID_TICKET_STATUS_TRANSITION,
                        40900,
                        "工单状态流转不合法"
                ),
                () -> assertErrorCode(
                        ErrorCode.TICKET_STATUS_CONFLICT,
                        40901,
                        "工单状态已发生变化，请刷新后重试"
                ),
                () -> assertErrorCode(
                        ErrorCode.USERNAME_ALREADY_EXISTS,
                        40902,
                        "用户名已存在"
                )
        );
    }

    @Test
    void shouldExposeTicketAssignmentBusinessErrorCodes() {
        assertAll(
                () -> assertErrorCode(
                        ErrorCode.ASSIGNEE_NOT_FOUND,
                        40401,
                        "处理人不存在"
                ),
                () -> assertErrorCode(
                        ErrorCode.INVALID_ASSIGNEE_ROLE,
                        40903,
                        "目标用户不是可指派的处理人"
                ),
                () -> assertErrorCode(
                        ErrorCode.TICKET_ALREADY_ASSIGNED,
                        40904,
                        "工单已指派给该处理人"
                ),
                () -> assertErrorCode(
                        ErrorCode.TICKET_ASSIGNMENT_CONFLICT,
                        40905,
                        "工单指派状态已发生变化，请刷新后重试"
                )
        );
    }

    private static void assertErrorCode(ErrorCode errorCode, int code, String message) {
        ApiResponse<Void> response = ApiResponse.failure(errorCode);
        assertEquals(code, response.code());
        assertEquals(message, response.message());
        assertNull(response.data());
    }
}
