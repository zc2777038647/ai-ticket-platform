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

class AssignTicketRequestValidationTest {

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
    void shouldAcceptPositiveAssigneeUserId() {
        assertTrue(validator.validate(new AssignTicketRequest(100L)).isEmpty());
    }

    @Test
    void shouldRejectNullAssigneeUserId() {
        assertSingleViolation(null, "处理人ID不能为空");
    }

    @Test
    void shouldRejectZeroAssigneeUserId() {
        assertSingleViolation(0L, "处理人ID必须为正数");
    }

    @Test
    void shouldRejectNegativeAssigneeUserId() {
        assertSingleViolation(-1L, "处理人ID必须为正数");
    }

    private static void assertSingleViolation(Long assigneeUserId, String expectedMessage) {
        Set<ConstraintViolation<AssignTicketRequest>> violations = validator.validate(
                new AssignTicketRequest(assigneeUserId)
        );

        assertEquals(1, violations.size());
        ConstraintViolation<AssignTicketRequest> violation = violations.iterator().next();
        assertEquals("assigneeUserId", violation.getPropertyPath().toString());
        assertEquals(expectedMessage, violation.getMessage());
    }
}
