package com.xiaoyang.aiticketplatform.dto.request;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RegisterRequestValidationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidatorFactory() {
        validatorFactory.close();
    }

    @Test
    void shouldAcceptValidRequest() {
        RegisterRequest request = new RegisterRequest(
                "User_123",
                "x".repeat(64),
                "用".repeat(64)
        );

        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    void shouldRejectBlankUsername() {
        assertHasViolation(validRequest(""), "username", "用户名不能为空");
    }

    @Test
    void shouldRejectUsernameShorterThanFourCharacters() {
        assertHasViolation(validRequest("abc"), "username", "用户名长度必须在4到64个字符之间");
    }

    @Test
    void shouldRejectUsernameLongerThan64Characters() {
        assertHasViolation(validRequest("a".repeat(65)), "username", "用户名长度必须在4到64个字符之间");
    }

    @Test
    void shouldRejectUsernameContainingSpaces() {
        assertHasViolation(validRequest(" user "), "username", "用户名只能包含字母、数字和下划线");
    }

    @Test
    void shouldRejectUsernameContainingSymbol() {
        assertHasViolation(validRequest("user-name"), "username", "用户名只能包含字母、数字和下划线");
    }

    @Test
    void shouldRejectUsernameContainingChineseCharacters() {
        assertHasViolation(validRequest("用户1234"), "username", "用户名只能包含字母、数字和下划线");
    }

    @Test
    void shouldRejectBlankPassword() {
        RegisterRequest request = new RegisterRequest("valid_user", "", "测试用户");

        assertHasViolation(request, "password", "密码不能为空");
    }

    @Test
    void shouldRejectPasswordShorterThanEightCharacters() {
        RegisterRequest request = new RegisterRequest("valid_user", "x".repeat(7), "测试用户");

        assertHasViolation(request, "password", "密码长度必须在8到64个字符之间");
    }

    @Test
    void shouldRejectPasswordLongerThan64Characters() {
        RegisterRequest request = new RegisterRequest("valid_user", "x".repeat(65), "测试用户");

        assertHasViolation(request, "password", "密码长度必须在8到64个字符之间");
    }

    @Test
    void shouldRejectBlankDisplayName() {
        RegisterRequest request = new RegisterRequest("valid_user", "x".repeat(8), "   ");

        assertHasViolation(request, "displayName", "显示名称不能为空");
    }

    @Test
    void shouldRejectDisplayNameLongerThan64Characters() {
        RegisterRequest request = new RegisterRequest("valid_user", "x".repeat(8), "用".repeat(65));

        assertHasViolation(request, "displayName", "显示名称长度不能超过64个字符");
    }

    private static RegisterRequest validRequest(String username) {
        return new RegisterRequest(username, "x".repeat(8), "测试用户");
    }

    private static void assertHasViolation(
            RegisterRequest request,
            String propertyName,
            String message
    ) {
        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);

        assertTrue(
                violations.stream().anyMatch(violation ->
                        propertyName.equals(violation.getPropertyPath().toString())
                                && message.equals(violation.getMessage())),
                () -> "未找到预期校验错误: " + propertyName + " - " + message
        );
    }
}
