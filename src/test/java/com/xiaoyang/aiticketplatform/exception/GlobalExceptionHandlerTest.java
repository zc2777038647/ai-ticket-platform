package com.xiaoyang.aiticketplatform.exception;

import com.xiaoyang.aiticketplatform.dto.request.CreateTicketRequest;
import jakarta.validation.Valid;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private LocalValidatorFactoryBean validator;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .setMessageConverters(new JacksonJsonHttpMessageConverter())
                .build();
    }

    @AfterEach
    void tearDown() {
        validator.close();
    }

    @Test
    void shouldAcceptValidRequest() throws Exception {
        mockMvc.perform(post("/test/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("测试工单"))
                .andExpect(jsonPath("$.priority").value("HIGH"));
    }

    @Test
    void shouldReturnValidationErrorForBlankTitle() throws Exception {
        mockMvc.perform(post("/test/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "   ",
                                  "description": "测试描述",
                                  "creatorName": "测试用户",
                                  "priority": "HIGH"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000))
                .andExpect(jsonPath("$.message").value("请求参数校验失败"))
                .andExpect(jsonPath("$.data.title").value("标题不能为空"));
    }

    @Test
    void shouldReturnMessageNotReadableForUnknownPriority() throws Exception {
        mockMvc.perform(post("/test/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "测试工单",
                                  "description": "测试描述",
                                  "creatorName": "测试用户",
                                  "priority": "UNKNOWN"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value("请求体格式错误"))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void shouldReturnMessageNotReadableForMalformedJson() throws Exception {
        mockMvc.perform(post("/test/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"测试工单\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value("请求体格式错误"))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void shouldReturnInternalErrorWithoutExposingExceptionMessage() throws Exception {
        mockMvc.perform(get("/test/error"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(50000))
                .andExpect(jsonPath("$.message").value("服务器内部错误"))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(content().string(not(containsString("敏感内部错误"))));
    }

    private static String validRequestJson() {
        return """
                {
                  "title": "测试工单",
                  "description": "测试描述",
                  "creatorName": "测试用户",
                  "priority": "HIGH"
                }
                """;
    }

    @RestController
    static class TestController {

        @PostMapping("/test/tickets")
        CreateTicketRequest createTicket(@Valid @RequestBody CreateTicketRequest request) {
            return request;
        }

        @GetMapping("/test/error")
        void throwUnexpectedException() {
            throw new IllegalStateException("敏感内部错误");
        }
    }
}
