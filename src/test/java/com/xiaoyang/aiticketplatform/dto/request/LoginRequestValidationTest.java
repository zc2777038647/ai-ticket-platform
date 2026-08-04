package com.xiaoyang.aiticketplatform.dto.request;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginRequestValidationTest {

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
    void shouldAcceptValidLoginRequest() {
        assertTrue(validator.validate(validRequest()).isEmpty());
    }

    @Test
    void shouldRejectNullUsername() {
        assertHasViolation(new LoginRequest(null, "test-secret"), "username", "用户名不能为空");
    }

    @Test
    void shouldRejectBlankUsername() {
        assertHasViolation(new LoginRequest("   ", "test-secret"), "username", "用户名不能为空");
    }

    @Test
    void shouldRejectUsernameLongerThan64Characters() {
        assertHasViolation(
                new LoginRequest("u".repeat(65), "test-secret"),
                "username",
                "用户名长度不能超过64个字符"
        );
    }

    @Test
    void shouldRejectNullPassword() {
        assertHasViolation(new LoginRequest("test_user", null), "password", "密码不能为空");
    }

    @Test
    void shouldRejectBlankPassword() {
        assertHasViolation(new LoginRequest("test_user", "   "), "password", "密码不能为空");
    }

    @Test
    void shouldRejectPasswordLongerThan64Characters() {
        assertHasViolation(
                new LoginRequest("test_user", "p".repeat(65)),
                "password",
                "密码长度不能超过64个字符"
        );
    }

    @Test
    void shouldNotTrimUsernameInDto() {
        LoginRequest request = new LoginRequest(" test_user ", "test-secret");

        assertTrue(validator.validate(request).isEmpty());
        assertEquals(" test_user ", request.username());
    }

    @Test
    void shouldAllowUsernameOutsideCurrentRegistrationPattern() {
        LoginRequest request = new LoginRequest("user-name", "test-secret");

        assertTrue(validator.validate(request).isEmpty());
    }

    private static LoginRequest validRequest() {
        return new LoginRequest("Test_User", "test-secret");
    }

    private static void assertHasViolation(
            LoginRequest request,
            String propertyName,
            String message
    ) {
        Set<ConstraintViolation<LoginRequest>> violations = validator.validate(request);
        assertTrue(
                violations.stream().anyMatch(violation ->
                        propertyName.equals(violation.getPropertyPath().toString())
                                && message.equals(violation.getMessage())),
                () -> "未找到预期校验错误: " + propertyName + " - " + message
        );
    }
}
