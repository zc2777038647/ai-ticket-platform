package com.xiaoyang.aiticketplatform.dto.request;

import com.xiaoyang.aiticketplatform.enums.TicketPriority;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CreateTicketRequestValidationTest {

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
    void shouldHaveNoViolationsWhenAllFieldsAreValid() {
        CreateTicketRequest request = new CreateTicketRequest(
                "工".repeat(120),
                "描".repeat(2000),
                "用".repeat(64),
                TicketPriority.URGENT
        );

        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    void shouldRejectEmptyTitle() {
        CreateTicketRequest request = validRequest("", "工单描述", "创建人", TicketPriority.MEDIUM);

        assertHasViolation(request, "title", "标题不能为空");
    }

    @Test
    void shouldRejectBlankTitle() {
        CreateTicketRequest request = validRequest("   ", "工单描述", "创建人", TicketPriority.MEDIUM);

        assertHasViolation(request, "title", "标题不能为空");
    }

    @Test
    void shouldRejectTitleLongerThan120Characters() {
        CreateTicketRequest request = validRequest("工".repeat(121), "工单描述", "创建人", TicketPriority.MEDIUM);

        assertHasViolation(request, "title", "标题长度不能超过120个字符");
    }

    @Test
    void shouldRejectEmptyDescription() {
        CreateTicketRequest request = validRequest("工单标题", "", "创建人", TicketPriority.MEDIUM);

        assertHasViolation(request, "description", "描述不能为空");
    }

    @Test
    void shouldRejectDescriptionLongerThan2000Characters() {
        CreateTicketRequest request = validRequest("工单标题", "描".repeat(2001), "创建人", TicketPriority.MEDIUM);

        assertHasViolation(request, "description", "描述长度不能超过2000个字符");
    }

    @Test
    void shouldRejectEmptyCreatorName() {
        CreateTicketRequest request = validRequest("工单标题", "工单描述", "", TicketPriority.MEDIUM);

        assertHasViolation(request, "creatorName", "创建人名称不能为空");
    }

    @Test
    void shouldRejectCreatorNameLongerThan64Characters() {
        CreateTicketRequest request = validRequest("工单标题", "工单描述", "用".repeat(65), TicketPriority.MEDIUM);

        assertHasViolation(request, "creatorName", "创建人名称长度不能超过64个字符");
    }

    @Test
    void shouldRejectNullPriority() {
        CreateTicketRequest request = validRequest("工单标题", "工单描述", "创建人", null);

        assertHasViolation(request, "priority", "优先级不能为空");
    }

    private static CreateTicketRequest validRequest(
            String title,
            String description,
            String creatorName,
            TicketPriority priority
    ) {
        return new CreateTicketRequest(title, description, creatorName, priority);
    }

    private static void assertHasViolation(
            CreateTicketRequest request,
            String propertyName,
            String message
    ) {
        Set<ConstraintViolation<CreateTicketRequest>> violations = validator.validate(request);

        assertTrue(
                violations.stream().anyMatch(violation ->
                        propertyName.equals(violation.getPropertyPath().toString())
                                && message.equals(violation.getMessage())),
                () -> "未找到预期校验错误: " + propertyName + " - " + message
        );
    }
}
