package com.xiaoyang.aiticketplatform.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jayway.jsonpath.JsonPath;
import com.xiaoyang.aiticketplatform.entity.UserAccount;
import com.xiaoyang.aiticketplatform.mapper.UserAccountMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BearerAuthenticationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private UserAccountMapper userAccountMapper;

    private String usernamePrefix;

    @AfterTransaction
    void verifyRegisteredUsersWereRolledBack() {
        if (usernamePrefix != null) {
            assertEquals(0L, userAccountMapper.selectCount(
                    new LambdaQueryWrapper<UserAccount>()
                            .likeRight(UserAccount::getUsername, usernamePrefix)
            ));
        }
    }

    @Test
    void shouldAllowAnonymousRegistrationAndLogin() throws Exception {
        Credentials credentials = registerUser();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(credentials.requestedUsername(), credentials.password())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
    }

    @Test
    void shouldAuthenticateValidBearerTokenAndReturnCurrentUser() throws Exception {
        Credentials credentials = registerUser();
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(credentials.requestedUsername(), credentials.password())))
                .andExpect(status().isOk())
                .andReturn();
        String accessToken = JsonPath.read(
                loginResult.getResponse().getContentAsString(),
                "$.data.accessToken"
        );

        mockMvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.username").value(credentials.normalizedUsername()))
                .andExpect(jsonPath("$.data.role").value("USER"))
                .andExpect(jsonPath("$.data.accessToken").doesNotExist())
                .andExpect(jsonPath("$.data.password").doesNotExist());
    }

    @Test
    void shouldRejectMissingBearerToken() throws Exception {
        assertPublicUnauthorized(mockMvc.perform(get("/api/auth/me")));
    }

    @Test
    void shouldRejectMalformedBearerTokenWithoutLeakingDetails() throws Exception {
        assertPublicUnauthorized(mockMvc.perform(get("/api/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer broken-test-token")));
    }

    @Test
    void shouldRejectTokenSignedWithDifferentKey() throws Exception {
        byte[] alternateKeyBytes = "alternate-test-signing-key-32-bytes".getBytes(StandardCharsets.UTF_8);
        JwtEncoder alternateEncoder = NimbusJwtEncoder
                .withSecretKey(new SecretKeySpec(alternateKeyBytes, "HmacSHA256"))
                .algorithm(MacAlgorithm.HS256)
                .build();

        assertPublicUnauthorized(mockMvc.perform(get("/api/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + encode(alternateEncoder, claims -> { }))));
    }

    @Test
    void shouldRejectExpiredToken() throws Exception {
        Instant expiredAt = Instant.now().minusSeconds(120);
        String token = encode(jwtEncoder, claims -> claims
                .issuedAt(expiredAt.minusSeconds(300))
                .expiresAt(expiredAt));

        assertPublicUnauthorized(mockMvc.perform(get("/api/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)));
    }

    @Test
    void shouldRejectTokenFromWrongIssuer() throws Exception {
        String token = encode(jwtEncoder, claims -> claims.issuer("wrong-issuer"));

        assertPublicUnauthorized(mockMvc.perform(get("/api/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)));
    }

    @Test
    void shouldRejectTokenWithUnknownRole() throws Exception {
        String token = encode(jwtEncoder, claims -> claims.claim("role", "UNKNOWN"));

        assertPublicUnauthorized(mockMvc.perform(get("/api/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)));
    }

    @Test
    void shouldRejectTokenMissingRequiredClaim() throws Exception {
        String token = encode(jwtEncoder, claims ->
                claims.claims(values -> values.remove("jti")));

        assertPublicUnauthorized(mockMvc.perform(get("/api/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)));
    }

    @Test
    void shouldRequireBearerTokenForTicketQuery() throws Exception {
        assertPublicUnauthorized(mockMvc.perform(get("/api/tickets")
                        .param("page", "1")
                        .param("size", "1")));
    }

    private void assertPublicUnauthorized(ResultActions resultActions) throws Exception {
        resultActions
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, containsString("Bearer")))
                .andExpect(jsonPath("$.code").value(40101))
                .andExpect(jsonPath("$.message").value("请先登录或提供有效访问令牌"))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(content().string(not(containsString("JwtException"))))
                .andExpect(content().string(not(containsString("invalid signature"))))
                .andExpect(content().string(not(containsString("broken-test-token"))));
    }

    private Credentials registerUser() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String requestedUsername = "Bearer_" + suffix;
        String normalizedUsername = requestedUsername.toLowerCase(Locale.ROOT);
        String password = "P_" + suffix;
        usernamePrefix = normalizedUsername;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(requestedUsername, password)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(0));
        return new Credentials(requestedUsername, normalizedUsername, password);
    }

    private static String encode(
            JwtEncoder encoder,
            Consumer<JwtClaimsSet.Builder> customization
    ) {
        Instant now = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer("ai-ticket-platform")
                .subject("100")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .id(UUID.randomUUID().toString())
                .claim("username", "test_user")
                .claim("role", "USER");
        customization.accept(claims);
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).type("JWT").build(),
                claims.build()
        )).getTokenValue();
    }

    private static String registerJson(String username, String password) {
        return """
                {
                  "username": "%s",
                  "password": "%s",
                  "displayName": "Bearer认证测试用户"
                }
                """.formatted(username, password);
    }

    private static String loginJson(String username, String password) {
        return """
                {
                  "username": "%s",
                  "password": "%s"
                }
                """.formatted(username, password);
    }

    private record Credentials(
            String requestedUsername,
            String normalizedUsername,
            String password
    ) {
    }
}
