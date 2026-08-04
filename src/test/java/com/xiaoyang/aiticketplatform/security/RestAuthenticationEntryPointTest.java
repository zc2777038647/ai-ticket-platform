package com.xiaoyang.aiticketplatform.security;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RestAuthenticationEntryPointTest {

    @Test
    void shouldReturnUniformPublicUnauthorizedResponse() throws Exception {
        RestAuthenticationEntryPoint entryPoint = new RestAuthenticationEntryPoint(new ObjectMapper());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer private-test-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(
                request,
                response,
                new BadCredentialsException("invalid signature internal text")
        );

        String body = response.getContentAsString();
        assertEquals(401, response.getStatus());
        assertTrue(response.getContentType().startsWith(MediaType.APPLICATION_JSON_VALUE));
        assertTrue(response.getHeader(HttpHeaders.WWW_AUTHENTICATE).contains("Bearer"));
        assertTrue(body.contains("\"code\":40101"));
        assertTrue(body.contains("\"message\":\"请先登录或提供有效访问令牌\""));
        assertTrue(body.contains("\"data\":null"));
        assertFalse(body.contains("BadCredentialsException"));
        assertFalse(body.contains("private-test-token"));
        assertFalse(body.contains("Authorization"));
        assertFalse(body.contains("invalid signature"));
    }
}
