package com.xiaoyang.aiticketplatform.config;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreateTicketIdempotencyPropertiesTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @Test
    void shouldExposeExpectedDefaults() {
        CreateTicketIdempotencyProperties properties = new CreateTicketIdempotencyProperties();

        assertTrue(properties.isEnabled());
        assertEquals("ai-ticket:idempotency:create-ticket", properties.getKeyPrefix());
        assertEquals(120, properties.getProcessingTtlSeconds());
        assertEquals(86400, properties.getSuccessTtlSeconds());
        assertEquals(8, properties.getMinimumKeyLength());
        assertEquals(128, properties.getMaximumKeyLength());
        assertTrue(validator.validate(properties).isEmpty());
    }

    @Test
    void shouldRejectBlankKeyPrefix() {
        CreateTicketIdempotencyProperties properties = validProperties();
        properties.setKeyPrefix(" ");

        assertViolation(properties, "keyPrefix");
    }

    @Test
    void shouldRejectNonPositiveTtls() {
        CreateTicketIdempotencyProperties properties = validProperties();
        properties.setProcessingTtlSeconds(0);
        properties.setSuccessTtlSeconds(-1);

        Set<ConstraintViolation<CreateTicketIdempotencyProperties>> violations =
                validator.validate(properties);
        assertEquals(2, violations.size());
    }

    @Test
    void shouldRejectNonPositiveMinimumKeyLength() {
        CreateTicketIdempotencyProperties properties = validProperties();
        properties.setMinimumKeyLength(0);

        assertViolation(properties, "minimumKeyLength");
    }

    @Test
    void shouldRejectMaximumSmallerThanMinimum() {
        CreateTicketIdempotencyProperties properties = validProperties();
        properties.setMinimumKeyLength(9);
        properties.setMaximumKeyLength(8);

        assertViolation(properties, "keyLengthRangeValid");
    }

    @Test
    void shouldAcceptValidCustomValues() {
        CreateTicketIdempotencyProperties properties = validProperties();
        properties.setEnabled(false);
        properties.setKeyPrefix("custom:idempotency");
        properties.setProcessingTtlSeconds(10);
        properties.setSuccessTtlSeconds(20);
        properties.setMinimumKeyLength(4);
        properties.setMaximumKeyLength(32);

        assertTrue(validator.validate(properties).isEmpty());
    }

    private static CreateTicketIdempotencyProperties validProperties() {
        return new CreateTicketIdempotencyProperties();
    }

    private static void assertViolation(
            CreateTicketIdempotencyProperties properties,
            String propertyPath
    ) {
        assertFalse(validator.validate(properties).isEmpty());
        assertTrue(validator.validate(properties).stream()
                .anyMatch(violation -> violation.getPropertyPath().toString().equals(propertyPath)));
    }
}
