package com.xiaoyang.aiticketplatform.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtConfigTest {

    private final JwtConfig jwtConfig = new JwtConfig();

    @Test
    void shouldCreateHmacSha256SecretKeyFromValidBase64() {
        SecretKey secretKey = jwtConfig.jwtSecretKey(validProperties());

        assertEquals("HmacSHA256", secretKey.getAlgorithm());
        assertEquals(32, secretKey.getEncoded().length);
    }

    @Test
    void shouldCreateJwtEncoder() {
        JwtEncoder jwtEncoder = jwtConfig.jwtEncoder(jwtConfig.jwtSecretKey(validProperties()));

        assertNotNull(jwtEncoder);
    }

    @Test
    void shouldCreateJwtDecoder() {
        JwtDecoder jwtDecoder = jwtConfig.jwtDecoder(
                jwtConfig.jwtSecretKey(validProperties()),
                validProperties()
        );

        assertNotNull(jwtDecoder);
    }

    @Test
    void shouldDecodeTokenWithCorrectIssuerAndRequiredClaims() {
        assertNotNull(decoder().decode(encode(claims -> { })));
    }

    @Test
    void shouldRejectTokenWithWrongIssuer() {
        assertThrows(JwtValidationException.class,
                () -> decoder().decode(encode(claims -> claims.issuer("wrong-issuer"))));
    }

    @Test
    void shouldRejectExpiredToken() {
        Instant expiredAt = Instant.now().minusSeconds(120);
        assertThrows(JwtValidationException.class,
                () -> decoder().decode(encode(claims -> claims
                        .issuedAt(expiredAt.minusSeconds(300))
                        .expiresAt(expiredAt))));
    }

    @Test
    void shouldRejectTokenMissingRequiredApplicationClaim() {
        assertThrows(JwtValidationException.class,
                () -> decoder().decode(encode(claims ->
                        claims.claims(values -> values.remove("username")))));
    }

    @Test
    void shouldRejectTokenWithUnknownRole() {
        assertThrows(JwtValidationException.class,
                () -> decoder().decode(encode(claims -> claims.claim("role", "UNKNOWN"))));
    }

    @Test
    void shouldRejectInvalidBase64Secret() {
        JwtProperties properties = new JwtProperties(
                "ai-ticket-platform",
                Duration.ofHours(2),
                "not-valid-base64!"
        );

        assertThrows(IllegalStateException.class, () -> jwtConfig.jwtSecretKey(properties));
    }

    @Test
    void shouldRejectSecretShorterThan256Bits() {
        String shortSecret = Base64.getEncoder().encodeToString(
                "test-only-short-secret".getBytes(StandardCharsets.UTF_8)
        );
        JwtProperties properties = new JwtProperties(
                "ai-ticket-platform",
                Duration.ofHours(2),
                shortSecret
        );

        assertThrows(IllegalStateException.class, () -> jwtConfig.jwtSecretKey(properties));
    }

    @Test
    void shouldRejectBlankIssuer() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new JwtProperties(" ", Duration.ofHours(2), testSecretBase64())
        );
    }

    @Test
    void shouldRejectZeroTtl() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new JwtProperties("ai-ticket-platform", Duration.ZERO, testSecretBase64())
        );
    }

    @Test
    void shouldRejectNegativeTtl() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new JwtProperties("ai-ticket-platform", Duration.ofSeconds(-1), testSecretBase64())
        );
    }

    @Test
    void shouldRejectBlankSecretConfiguration() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new JwtProperties("ai-ticket-platform", Duration.ofHours(2), "")
        );
    }

    private static JwtProperties validProperties() {
        return new JwtProperties(
                "ai-ticket-platform",
                Duration.ofHours(2),
                testSecretBase64()
        );
    }

    private static String testSecretBase64() {
        return Base64.getEncoder().encodeToString(
                "test-only-256-bit-secret-value!!".getBytes(StandardCharsets.UTF_8)
        );
    }

    private JwtDecoder decoder() {
        JwtProperties properties = validProperties();
        return jwtConfig.jwtDecoder(jwtConfig.jwtSecretKey(properties), properties);
    }

    private String encode(Consumer<JwtClaimsSet.Builder> customization) {
        JwtProperties properties = validProperties();
        Instant now = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(properties.getIssuer())
                .subject("100")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .id(UUID.randomUUID().toString())
                .claim("username", "test_user")
                .claim("role", "USER");
        customization.accept(claims);
        JwtEncoder encoder = jwtConfig.jwtEncoder(jwtConfig.jwtSecretKey(properties));
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).type("JWT").build(),
                claims.build()
        )).getTokenValue();
    }
}
