package com.xiaoyang.aiticketplatform.service.impl;

import com.xiaoyang.aiticketplatform.config.JwtConfig;
import com.xiaoyang.aiticketplatform.config.JwtProperties;
import com.xiaoyang.aiticketplatform.entity.UserAccount;
import com.xiaoyang.aiticketplatform.enums.UserRole;
import com.xiaoyang.aiticketplatform.security.IssuedAccessToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class JwtTokenServiceImplTest {

    private Instant fixedInstant;
    private JwtDecoder jwtDecoder;
    private JwtTokenServiceImpl jwtTokenService;

    @BeforeEach
    void setUp() {
        JwtConfig jwtConfig = new JwtConfig();
        JwtProperties properties = testProperties();
        SecretKey secretKey = jwtConfig.jwtSecretKey(properties);
        JwtEncoder jwtEncoder = jwtConfig.jwtEncoder(secretKey);
        jwtDecoder = jwtConfig.jwtDecoder(secretKey, properties);
        fixedInstant = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Clock fixedClock = Clock.fixed(fixedInstant, ZoneOffset.UTC);
        jwtTokenService = new JwtTokenServiceImpl(jwtEncoder, properties, fixedClock);
    }

    @Test
    void shouldIssueAndDecodeAccessTokenWithExpectedClaims() {
        IssuedAccessToken issuedToken = jwtTokenService.issueAccessToken(testUser());
        Jwt jwt = jwtDecoder.decode(issuedToken.tokenValue());

        assertAll(
                () -> assertEquals("HS256", jwt.getHeaders().get("alg")),
                () -> assertEquals("JWT", jwt.getHeaders().get("typ")),
                () -> assertEquals("ai-ticket-platform", jwt.getClaimAsString("iss")),
                () -> assertEquals("100", jwt.getSubject()),
                () -> assertEquals(fixedInstant, jwt.getIssuedAt()),
                () -> assertEquals(fixedInstant.plus(Duration.ofHours(2)), jwt.getExpiresAt()),
                () -> assertNotNull(jwt.getId()),
                () -> assertEquals("test_user", jwt.getClaimAsString("username")),
                () -> assertEquals("USER", jwt.getClaimAsString("role")),
                () -> assertFalse(jwt.getClaims().containsKey("password")),
                () -> assertFalse(jwt.getClaims().containsKey("passwordHash")),
                () -> assertFalse(jwt.getClaims().containsKey("displayName"))
        );
    }

    @Test
    void shouldCreateDifferentJtiAndTokenForEachIssuance() {
        IssuedAccessToken firstToken = jwtTokenService.issueAccessToken(testUser());
        IssuedAccessToken secondToken = jwtTokenService.issueAccessToken(testUser());

        Jwt firstJwt = jwtDecoder.decode(firstToken.tokenValue());
        Jwt secondJwt = jwtDecoder.decode(secondToken.tokenValue());

        assertNotEquals(firstJwt.getId(), secondJwt.getId());
        assertNotEquals(firstToken.tokenValue(), secondToken.tokenValue());
        assertNotNull(firstJwt.getSubject());
        assertNotNull(secondJwt.getSubject());
    }

    @Test
    void shouldReturnExactIssuedAndExpirationTimes() {
        IssuedAccessToken issuedToken = jwtTokenService.issueAccessToken(testUser());

        assertEquals(fixedInstant, issuedToken.issuedAt());
        assertEquals(fixedInstant.plus(Duration.ofHours(2)), issuedToken.expiresAt());
        assertEquals(7200L, Duration.between(
                issuedToken.issuedAt(),
                issuedToken.expiresAt()
        ).toSeconds());
    }

    private static JwtProperties testProperties() {
        String testSecret = Base64.getEncoder().encodeToString(
                "test-only-256-bit-secret-value!!".getBytes(StandardCharsets.UTF_8)
        );
        return new JwtProperties("ai-ticket-platform", Duration.ofHours(2), testSecret);
    }

    private static UserAccount testUser() {
        UserAccount userAccount = new UserAccount();
        userAccount.setId(100L);
        userAccount.setUsername("test_user");
        userAccount.setDisplayName("测试用户");
        userAccount.setRole(UserRole.USER);
        return userAccount;
    }
}
