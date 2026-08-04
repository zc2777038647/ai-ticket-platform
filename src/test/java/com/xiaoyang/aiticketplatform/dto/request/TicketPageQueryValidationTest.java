package com.xiaoyang.aiticketplatform.dto.request;

import com.xiaoyang.aiticketplatform.enums.TicketPriority;
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

class TicketPageQueryValidationTest {

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
    void shouldApplyDefaultsWhenAllFieldsAreNull() {
        TicketPageQuery query = new TicketPageQuery(null, null, null, null, null, null);

        assertEquals(1, query.page());
        assertEquals(20, query.size());
        assertTrue(validator.validate(query).isEmpty());
    }

    @Test
    void shouldRetainAllLegalValues() {
        TicketPageQuery query = new TicketPageQuery(
                2,
                100,
                TicketStatus.OPEN,
                TicketPriority.HIGH,
                "张三",
                "登录异常"
        );

        assertEquals(2, query.page());
        assertEquals(100, query.size());
        assertEquals(TicketStatus.OPEN, query.status());
        assertEquals(TicketPriority.HIGH, query.priority());
        assertEquals("张三", query.creatorName());
        assertEquals("登录异常", query.keyword());
        assertTrue(validator.validate(query).isEmpty());
    }

    @Test
    void shouldRejectPageLessThanOne() {
        TicketPageQuery query = validQuery(0, 20, null, null);

        assertHasViolation(query, "page", "页码必须大于等于1");
    }

    @Test
    void shouldRejectSizeLessThanOne() {
        TicketPageQuery query = validQuery(1, 0, null, null);

        assertHasViolation(query, "size", "每页数量必须大于等于1");
    }

    @Test
    void shouldRejectSizeGreaterThanOneHundred() {
        TicketPageQuery query = validQuery(1, 101, null, null);

        assertHasViolation(query, "size", "每页数量不能超过100");
    }

    @Test
    void shouldRejectCreatorNameLongerThanSixtyFourCharacters() {
        TicketPageQuery query = validQuery(1, 20, "人".repeat(65), null);

        assertHasViolation(query, "creatorName", "创建人名称长度不能超过64个字符");
    }

    @Test
    void shouldRejectKeywordLongerThanOneHundredCharacters() {
        TicketPageQuery query = validQuery(1, 20, null, "词".repeat(101));

        assertHasViolation(query, "keyword", "关键词长度不能超过100个字符");
    }

    @Test
    void shouldAllowWhitespaceOptionalStringsForLaterServiceHandling() {
        TicketPageQuery query = validQuery(1, 20, "   ", "   ");

        assertTrue(validator.validate(query).isEmpty());
        assertEquals("   ", query.creatorName());
        assertEquals("   ", query.keyword());
    }

    private static TicketPageQuery validQuery(
            Integer page,
            Integer size,
            String creatorName,
            String keyword
    ) {
        return new TicketPageQuery(page, size, null, null, creatorName, keyword);
    }

    private static void assertHasViolation(
            TicketPageQuery query,
            String propertyName,
            String message
    ) {
        Set<ConstraintViolation<TicketPageQuery>> violations = validator.validate(query);

        assertTrue(
                violations.stream().anyMatch(violation ->
                        propertyName.equals(violation.getPropertyPath().toString())
                                && message.equals(violation.getMessage())),
                () -> "未找到预期校验错误: " + propertyName + " - " + message
        );
    }
}
