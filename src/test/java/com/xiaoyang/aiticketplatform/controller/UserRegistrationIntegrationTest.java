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
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
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
class UserRegistrationIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UserAccountMapper userAccountMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private MockMvc mockMvc;
    private String usernamePrefix;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

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
    void shouldRegisterUserThroughCompleteApplicationChain() throws Exception {
        String suffix = uniqueSuffix();
        String requestedUsername = "TestUser_" + suffix;
        String normalizedUsername = requestedUsername.toLowerCase(Locale.ROOT);
        usernamePrefix = normalizedUsername;
        String rawPassword = testPassword(suffix);

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson(requestedUsername, rawPassword, " 注册测试用户 ")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.username").value(normalizedUsername))
                .andExpect(jsonPath("$.data.displayName").value("注册测试用户"))
                .andExpect(jsonPath("$.data.role").value("USER"))
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
                .andExpect(content().string(not(containsString(rawPassword))))
                .andReturn();

        List<UserAccount> users = userAccountMapper.selectList(queryByUsername(normalizedUsername));
        assertEquals(1, users.size());
        UserAccount persistedUser = users.getFirst();
        Number responseId = JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");

        assertAll(
                () -> assertNotNull(persistedUser.getId()),
                () -> assertEquals(persistedUser.getId().longValue(), responseId.longValue()),
                () -> assertEquals(normalizedUsername, persistedUser.getUsername()),
                () -> assertEquals("注册测试用户", persistedUser.getDisplayName()),
                () -> assertEquals(UserRole.USER, persistedUser.getRole()),
                () -> assertFalse(rawPassword.equals(persistedUser.getPasswordHash()),
                        "数据库不得保存原始密码"),
                () -> assertTrue(passwordEncoder.matches(rawPassword, persistedUser.getPasswordHash())),
                () -> assertNotNull(persistedUser.getCreatedAt()),
                () -> assertNotNull(persistedUser.getUpdatedAt())
        );
    }

    @Test
    void shouldRejectUsernameThatDiffersOnlyByCase() throws Exception {
        String suffix = uniqueSuffix();
        String firstUsername = "CaseUser_" + suffix;
        String secondUsername = "CASEUSER_" + suffix;
        String normalizedUsername = firstUsername.toLowerCase(Locale.ROOT);
        usernamePrefix = normalizedUsername;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson(firstUsername, testPassword(suffix), "用户一")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson(secondUsername, testPassword(suffix + "x"), "用户二")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40902))
                .andExpect(jsonPath("$.message").value("用户名已存在"));

        assertEquals(1L, userAccountMapper.selectCount(queryByUsername(normalizedUsername)));
    }

    @Test
    void shouldNotPersistUserWhenValidationFails() throws Exception {
        String suffix = uniqueSuffix();
        String username = "Invalid_" + suffix;
        usernamePrefix = username.toLowerCase(Locale.ROOT);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson(username, "short", "校验失败用户")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000))
                .andExpect(jsonPath("$.data.password").value("密码长度必须在8到64个字符之间"));

        assertEquals(0L, userAccountMapper.selectCount(queryByUsername(usernamePrefix)));
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

    private static String requestJson(String username, String password, String displayName) {
        return """
                {
                  "username": "%s",
                  "password": "%s",
                  "displayName": "%s"
                }
                """.formatted(username, password, displayName);
    }
}
