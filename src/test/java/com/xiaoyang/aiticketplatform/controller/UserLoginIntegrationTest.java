package com.xiaoyang.aiticketplatform.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jayway.jsonpath.JsonPath;
import com.xiaoyang.aiticketplatform.entity.UserAccount;
import com.xiaoyang.aiticketplatform.enums.UserRole;
import com.xiaoyang.aiticketplatform.mapper.UserAccountMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
class UserLoginIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UserAccountMapper userAccountMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtDecoder jwtDecoder;

    private MockMvc mockMvc;
    private String usernamePrefix;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @AfterTransaction
    void verifyPreparedUsersWereRolledBack() {
        if (usernamePrefix != null) {
            assertEquals(0L, userAccountMapper.selectCount(
                    new LambdaQueryWrapper<UserAccount>()
                            .likeRight(UserAccount::getUsername, usernamePrefix)
            ));
        }
    }

    @Test
    void shouldRegisterThenLoginAndDecodeSignedAccessToken() throws Exception {
        String suffix = uniqueSuffix();
        String registeredUsername = "LoginUser_" + suffix;
        String normalizedUsername = registeredUsername.toLowerCase(Locale.ROOT);
        usernamePrefix = normalizedUsername;
        String rawPassword = testPassword(suffix);
        UserAccount persistedUser = registerAndLoadUser(
                registeredUsername,
                rawPassword,
                normalizedUsername
        );

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(registeredUsername.toUpperCase(Locale.ROOT), rawPassword)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresIn").value(7200))
                .andExpect(jsonPath("$.data.user.id").value(persistedUser.getId()))
                .andExpect(jsonPath("$.data.user.username").value(normalizedUsername))
                .andExpect(jsonPath("$.data.user.role").value("USER"))
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
                .andExpect(content().string(not(containsString(rawPassword))))
                .andReturn();

        String accessToken = JsonPath.read(
                loginResult.getResponse().getContentAsString(),
                "$.data.accessToken"
        );
        Jwt jwt = jwtDecoder.decode(accessToken);

        assertAll(
                () -> assertEquals("ai-ticket-platform", jwt.getClaimAsString("iss")),
                () -> assertEquals(persistedUser.getId().toString(), jwt.getSubject()),
                () -> assertEquals(normalizedUsername, jwt.getClaimAsString("username")),
                () -> assertEquals("USER", jwt.getClaimAsString("role")),
                () -> assertNotNull(jwt.getIssuedAt()),
                () -> assertNotNull(jwt.getExpiresAt()),
                () -> assertTrue(jwt.getExpiresAt().isAfter(jwt.getIssuedAt())),
                () -> assertEquals(7200L, Duration.between(
                        jwt.getIssuedAt(),
                        jwt.getExpiresAt()
                ).toSeconds()),
                () -> assertNotNull(jwt.getId()),
                () -> assertEquals("HS256", jwt.getHeaders().get("alg")),
                () -> assertFalse(jwt.getClaims().containsKey("password")),
                () -> assertFalse(jwt.getClaims().containsKey("passwordHash"))
        );
    }

    @Test
    void shouldReturnUniformUnauthorizedResponseForWrongPasswordWithoutChangingUser() throws Exception {
        String suffix = uniqueSuffix();
        String username = "WrongPassword_" + suffix;
        String normalizedUsername = username.toLowerCase(Locale.ROOT);
        usernamePrefix = normalizedUsername;
        UserAccount persistedUser = registerAndLoadUser(
                username,
                testPassword(suffix),
                normalizedUsername
        );
        String originalPasswordHash = persistedUser.getPasswordHash();

        performInvalidCredentialsLogin(username, "W_" + suffix);

        List<UserAccount> users = userAccountMapper.selectList(queryByUsername(normalizedUsername));
        assertEquals(1, users.size());
        assertTrue(originalPasswordHash.equals(users.getFirst().getPasswordHash()),
                "登录失败不得修改密码哈希");
    }

    @Test
    void shouldReturnSameUnauthorizedResponseForMissingUsername() throws Exception {
        String suffix = uniqueSuffix();
        String username = "MissingUser_" + suffix;
        usernamePrefix = username.toLowerCase(Locale.ROOT);

        performInvalidCredentialsLogin(username, testPassword(suffix));

        assertEquals(0L, userAccountMapper.selectCount(queryByUsername(usernamePrefix)));
    }

    @Test
    void shouldRejectInvalidLoginRequestWithoutChangingDatabase() throws Exception {
        String suffix = uniqueSuffix();
        String username = "InvalidLogin_" + suffix;
        usernamePrefix = username.toLowerCase(Locale.ROOT);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(username, "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000))
                .andExpect(jsonPath("$.data.password").value("密码不能为空"));

        assertEquals(0L, userAccountMapper.selectCount(queryByUsername(usernamePrefix)));
    }

    private UserAccount registerAndLoadUser(
            String requestedUsername,
            String rawPassword,
            String normalizedUsername
    ) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(requestedUsername, rawPassword)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(0));

        List<UserAccount> users = userAccountMapper.selectList(queryByUsername(normalizedUsername));
        assertEquals(1, users.size());
        UserAccount userAccount = users.getFirst();
        assertEquals(UserRole.USER, userAccount.getRole());
        assertFalse(rawPassword.equals(userAccount.getPasswordHash()),
                "数据库不得保存原始密码");
        assertTrue(passwordEncoder.matches(rawPassword, userAccount.getPasswordHash()));
        return userAccount;
    }

    private void performInvalidCredentialsLogin(String username, String password) throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(username, password)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100))
                .andExpect(jsonPath("$.message").value("用户名或密码错误"))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(content().string(not(containsString("用户不存在"))))
                .andExpect(content().string(not(containsString("密码不正确"))));
    }

    private static LambdaQueryWrapper<UserAccount> queryByUsername(String username) {
        return new LambdaQueryWrapper<UserAccount>().eq(UserAccount::getUsername, username);
    }

    private static String uniqueSuffix() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static String testPassword(String suffix) {
        return "T_" + suffix;
    }

    private static String registerJson(String username, String password) {
        return """
                {
                  "username": "%s",
                  "password": "%s",
                  "displayName": "登录集成测试用户"
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
}
