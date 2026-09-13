package com.xiaoyang.aiticketplatform.security;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "management.endpoints.web.exposure.include=health,info",
                "management.endpoint.health.show-details=when-authorized",
                "management.endpoint.health.roles=ADMIN"
        }
)
@Import(SecurityHardeningIntegrationTest.SecurityHardeningTestConfiguration.class)
class SecurityHardeningIntegrationTest {

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Test
    void shouldDenyRealUnmatchedEndpointForAnonymousUser() throws Exception {
        HttpResponse<String> response = get("/api/security-test/unmatched");

        assertEquals(HttpStatus.UNAUTHORIZED.value(), response.statusCode());
    }

    @Test
    void shouldDenyRealUnmatchedEndpointForEveryAuthenticatedRole() throws Exception {
        for (String role : new String[]{"USER", "AGENT", "ADMIN"}) {
            HttpResponse<String> response = getAs(role, "/api/security-test/unmatched");

            assertEquals(HttpStatus.FORBIDDEN.value(), response.statusCode(), role);
        }
    }

    @Test
    void shouldExposeOnlyBasicHealthToAnonymousUser() throws Exception {
        HttpResponse<String> response = get("/actuator/health");

        assertEquals(HttpStatus.OK.value(), response.statusCode());
        assertBasicHealth(response.body());
    }

    @Test
    void shouldExposeOnlyBasicHealthToUser() throws Exception {
        HttpResponse<String> response = getAs("USER", "/actuator/health");

        assertEquals(HttpStatus.OK.value(), response.statusCode());
        assertBasicHealth(response.body());
    }

    @Test
    void shouldExposeOnlyBasicHealthToAgent() throws Exception {
        HttpResponse<String> response = getAs("AGENT", "/actuator/health");

        assertEquals(HttpStatus.OK.value(), response.statusCode());
        assertBasicHealth(response.body());
    }

    @Test
    void shouldExposeDetailedHealthToAdmin() throws Exception {
        HttpResponse<String> response = getAs("ADMIN", "/actuator/health");

        assertEquals(HttpStatus.OK.value(), response.statusCode());
        assertTrue(response.body().contains("\"components\""));
    }

    @Test
    void shouldProtectActuatorInfoForAdminOnly() throws Exception {
        assertEquals(HttpStatus.UNAUTHORIZED.value(), get("/actuator/info").statusCode());
        assertEquals(HttpStatus.FORBIDDEN.value(), getAs("USER", "/actuator/info").statusCode());
        assertEquals(HttpStatus.FORBIDDEN.value(), getAs("AGENT", "/actuator/info").statusCode());
        assertEquals(HttpStatus.OK.value(), getAs("ADMIN", "/actuator/info").statusCode());
    }

    @Test
    void shouldProtectActuatorRootForAdminOnly() throws Exception {
        assertEquals(HttpStatus.UNAUTHORIZED.value(), get("/actuator").statusCode());
        assertEquals(HttpStatus.FORBIDDEN.value(), getAs("USER", "/actuator").statusCode());
        assertEquals(HttpStatus.FORBIDDEN.value(), getAs("AGENT", "/actuator").statusCode());

        HttpResponse<String> adminResponse = getAs("ADMIN", "/actuator");
        assertEquals(HttpStatus.OK.value(), adminResponse.statusCode());
        assertTrue(adminResponse.body().contains("health"));
        assertTrue(adminResponse.body().contains("info"));
    }

    @Test
    void shouldAllowErrorDispatchAfterAuthorizedRequest() throws Exception {
        HttpResponse<String> response = get("/actuator/health?securityTestTriggerError=true");

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), response.statusCode());
        assertFalse(response.body().contains("40300"));
        assertFalse(response.body().contains("40101"));
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        return send(HttpRequest.newBuilder(uri(path)).GET().build());
    }

    private HttpResponse<String> getAs(String role, String path) throws IOException, InterruptedException {
        return send(HttpRequest.newBuilder(uri(path))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(role))
                .GET()
                .build());
    }

    private HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException {
        return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private String token(String role) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("ai-ticket-platform")
                .subject("9001")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .id(UUID.randomUUID().toString())
                .claim("username", "security_hardening_test")
                .claim("role", role)
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).type("JWT").build(),
                claims
        )).getTokenValue();
    }

    private static void assertBasicHealth(String body) {
        assertTrue(body.contains("\"status\""));
        assertFalse(body.contains("\"components\""));
        assertFalse(body.contains("\"db\""));
        assertFalse(body.contains("\"redis\""));
        assertFalse(body.contains("\"diskSpace\""));
        assertFalse(body.contains("\"ssl\""));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class SecurityHardeningTestConfiguration {

        @Bean
        UnmatchedEndpoint unmatchedEndpoint() {
            return new UnmatchedEndpoint();
        }

        @Bean
        FilterRegistrationBean<Filter> errorDispatchTriggerFilter() {
            FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
            registration.setFilter(new ErrorDispatchTriggerFilter());
            registration.addUrlPatterns("/actuator/health");
            registration.setDispatcherTypes(DispatcherType.REQUEST);
            registration.setOrder(Integer.MAX_VALUE);
            return registration;
        }
    }

    @RestController
    static class UnmatchedEndpoint {

        @GetMapping("/api/security-test/unmatched")
        String unmatched() {
            return "must be denied by default";
        }
    }

    static class ErrorDispatchTriggerFilter implements Filter {

        @Override
        public void doFilter(
                ServletRequest request,
                ServletResponse response,
                FilterChain chain
        ) throws IOException, ServletException {
            HttpServletRequest httpRequest = (HttpServletRequest) request;
            HttpServletResponse httpResponse = (HttpServletResponse) response;
            if (httpRequest.getDispatcherType() == DispatcherType.REQUEST
                    && "true".equals(httpRequest.getParameter("securityTestTriggerError"))) {
                httpResponse.sendError(HttpStatus.INTERNAL_SERVER_ERROR.value(), "security test error");
                return;
            }
            chain.doFilter(request, response);
        }
    }
}
