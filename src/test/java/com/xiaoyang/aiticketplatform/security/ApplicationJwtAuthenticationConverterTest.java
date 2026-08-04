package com.xiaoyang.aiticketplatform.security;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApplicationJwtAuthenticationConverterTest {

    private final ApplicationJwtAuthenticationConverter converter =
            new ApplicationJwtAuthenticationConverter();

    @ParameterizedTest
    @CsvSource({
            "USER, ROLE_USER",
            "AGENT, ROLE_AGENT",
            "ADMIN, ROLE_ADMIN"
    })
    void shouldConvertRoleToExactlyOneAuthority(String role, String expectedAuthority) {
        Jwt jwt = jwt(role);

        AbstractAuthenticationToken authentication = converter.convert(jwt);

        assertEquals("100", authentication.getName());
        assertSame(jwt, authentication.getPrincipal());
        assertSame(jwt, authentication.getCredentials());
        assertTrue(jwt.getClaims().keySet().stream()
                .noneMatch(claim -> claim.equals("password") || claim.equals("passwordHash")));
        assertTrue(authentication.isAuthenticated());
        assertEquals(1, authentication.getAuthorities().size());
        assertEquals(expectedAuthority, authentication.getAuthorities().iterator().next().getAuthority());
    }

    private static Jwt jwt(String role) {
        Instant now = Instant.now();
        return new Jwt(
                "test-token",
                now,
                now.plusSeconds(300),
                Map.of("alg", "HS256"),
                Map.of(
                        "sub", "100",
                        "username", "test_user",
                        "role", role,
                        "jti", "test-jti"
                )
        );
    }
}
