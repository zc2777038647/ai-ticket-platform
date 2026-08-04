package com.xiaoyang.aiticketplatform.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApplicationJwtValidatorTest {

    private final ApplicationJwtValidator validator = new ApplicationJwtValidator();

    @Test
    void shouldAcceptCompleteValidClaims() {
        assertValid(validClaims());
    }

    @Test
    void shouldRejectMissingSubject() {
        assertInvalid(without("sub"));
    }

    @Test
    void shouldRejectNonNumericSubject() {
        assertInvalid(with("sub", "abc"));
    }

    @Test
    void shouldRejectNonPositiveSubject() {
        assertInvalid(with("sub", "0"));
    }

    @Test
    void shouldRejectBlankUsername() {
        assertInvalid(with("username", "   "));
    }

    @Test
    void shouldRejectMissingRole() {
        assertInvalid(without("role"));
    }

    @Test
    void shouldRejectUnknownRole() {
        assertInvalid(with("role", "UNKNOWN"));
    }

    @Test
    void shouldRejectBlankJti() {
        assertInvalid(with("jti", ""));
    }

    private void assertValid(Map<String, Object> claims) {
        OAuth2TokenValidatorResult result = validator.validate(jwt(claims));
        assertFalse(result.hasErrors());
    }

    private void assertInvalid(Map<String, Object> claims) {
        OAuth2TokenValidatorResult result = validator.validate(jwt(claims));
        assertTrue(result.hasErrors());
    }

    private static Jwt jwt(Map<String, Object> claims) {
        Instant now = Instant.now();
        return new Jwt("test-token", now, now.plusSeconds(300),
                Map.of("alg", "HS256"), claims);
    }

    private static Map<String, Object> validClaims() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", "100");
        claims.put("username", "test_user");
        claims.put("role", "USER");
        claims.put("jti", "test-jti");
        return claims;
    }

    private static Map<String, Object> without(String claimName) {
        Map<String, Object> claims = validClaims();
        claims.remove(claimName);
        return claims;
    }

    private static Map<String, Object> with(String claimName, Object value) {
        Map<String, Object> claims = validClaims();
        claims.put(claimName, value);
        return claims;
    }
}
