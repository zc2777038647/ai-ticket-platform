package com.xiaoyang.aiticketplatform.security;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RestAccessDeniedHandlerTest {

    @Test
    void shouldReturnUniformPublicForbiddenResponse() throws Exception {
        RestAccessDeniedHandler handler = new RestAccessDeniedHandler(new ObjectMapper());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer private-test-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(
                request,
                response,
                new AccessDeniedException("ROLE_AGENT or ROLE_ADMIN is required")
        );

        String body = response.getContentAsString();
        assertEquals(403, response.getStatus());
        assertTrue(response.getContentType().startsWith(MediaType.APPLICATION_JSON_VALUE));
        assertNull(response.getHeader(HttpHeaders.WWW_AUTHENTICATE));
        assertTrue(body.contains("\"code\":40300"));
        assertTrue(body.contains("\"message\":\"权限不足，无法执行此操作\""));
        assertTrue(body.contains("\"data\":null"));
        assertFalse(body.contains("AccessDeniedException"));
        assertFalse(body.contains("java.lang"));
        assertFalse(body.contains("ROLE_AGENT"));
        assertFalse(body.contains("ROLE_ADMIN"));
        assertFalse(body.contains("JWT"));
        assertFalse(body.contains("Authorization"));
        assertFalse(body.contains("stackTrace"));
        assertFalse(body.contains("private-test-token"));
    }
}
