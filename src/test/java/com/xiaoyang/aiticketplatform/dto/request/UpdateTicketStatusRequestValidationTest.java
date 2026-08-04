package com.xiaoyang.aiticketplatform.dto.request;

import com.xiaoyang.aiticketplatform.enums.TicketStatus;
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

class UpdateTicketStatusRequestValidationTest {

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
    void shouldAcceptEveryRepresentativeTargetStatus() {
        for (TicketStatus status : new TicketStatus[]{
                TicketStatus.IN_PROGRESS,
                TicketStatus.RESOLVED,
                TicketStatus.CLOSED
        }) {
            UpdateTicketStatusRequest request = new UpdateTicketStatusRequest(status);
            assertTrue(validator.validate(request).isEmpty(), () -> "目标状态: " + status);
        }
    }

    @Test
    void shouldRejectNullTargetStatus() {
        UpdateTicketStatusRequest request = new UpdateTicketStatusRequest(null);

        Set<ConstraintViolation<UpdateTicketStatusRequest>> violations = validator.validate(request);

        assertEquals(1, violations.size());
        ConstraintViolation<UpdateTicketStatusRequest> violation = violations.iterator().next();
        assertEquals("status", violation.getPropertyPath().toString());
        assertEquals("目标状态不能为空", violation.getMessage());
    }
}
