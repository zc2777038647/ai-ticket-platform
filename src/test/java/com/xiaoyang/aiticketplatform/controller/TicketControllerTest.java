package com.xiaoyang.aiticketplatform.controller;

import com.xiaoyang.aiticketplatform.common.ErrorCode;
import com.xiaoyang.aiticketplatform.dto.request.CreateTicketRequest;
import com.xiaoyang.aiticketplatform.dto.response.TicketResponse;
import com.xiaoyang.aiticketplatform.enums.TicketPriority;
import com.xiaoyang.aiticketplatform.enums.TicketStatus;
import com.xiaoyang.aiticketplatform.exception.BusinessException;
import com.xiaoyang.aiticketplatform.exception.GlobalExceptionHandler;
import com.xiaoyang.aiticketplatform.service.TicketService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TicketControllerTest {

    @Mock
    private TicketService ticketService;

    private LocalValidatorFactoryBean validator;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new TicketController(ticketService))
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
    void shouldCreateTicket() throws Exception {
        TicketResponse serviceResponse = new TicketResponse(
                100L,
                "测试工单",
                "测试描述",
                "测试用户",
                TicketPriority.HIGH,
                TicketStatus.OPEN
        );
        when(ticketService.createTicket(any(CreateTicketRequest.class))).thenReturn(serviceResponse);

        mockMvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.id").value(100))
                .andExpect(jsonPath("$.data.title").value("测试工单"))
                .andExpect(jsonPath("$.data.priority").value("HIGH"))
                .andExpect(jsonPath("$.data.status").value("OPEN"));

        ArgumentCaptor<CreateTicketRequest> requestCaptor = ArgumentCaptor.forClass(CreateTicketRequest.class);
        verify(ticketService, times(1)).createTicket(requestCaptor.capture());
        verifyNoMoreInteractions(ticketService);
        CreateTicketRequest capturedRequest = requestCaptor.getValue();
        assertAll(
                () -> assertEquals("测试工单", capturedRequest.title()),
                () -> assertEquals("测试描述", capturedRequest.description()),
                () -> assertEquals("测试用户", capturedRequest.creatorName()),
                () -> assertEquals(TicketPriority.HIGH, capturedRequest.priority())
        );
    }

    @Test
    void shouldRejectBlankTitleWithoutCallingService() throws Exception {
        mockMvc.perform(post("/api/tickets")
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

        verifyNoInteractions(ticketService);
    }

    @Test
    void shouldReturnInternalErrorWhenServiceFails() throws Exception {
        when(ticketService.createTicket(any(CreateTicketRequest.class)))
                .thenThrow(new IllegalStateException("敏感内部错误"));

        mockMvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(50000))
                .andExpect(jsonPath("$.message").value("服务器内部错误"))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(content().string(not(containsString("敏感内部错误"))));

        verify(ticketService, times(1)).createTicket(any(CreateTicketRequest.class));
        verifyNoMoreInteractions(ticketService);
    }

    @Test
    void shouldGetTicketById() throws Exception {
        TicketResponse serviceResponse = new TicketResponse(
                100L,
                "查询测试工单",
                "查询测试描述",
                "查询测试用户",
                TicketPriority.HIGH,
                TicketStatus.OPEN
        );
        when(ticketService.getTicketById(100L)).thenReturn(serviceResponse);

        mockMvc.perform(get("/api/tickets/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.id").value(100))
                .andExpect(jsonPath("$.data.title").value("查询测试工单"))
                .andExpect(jsonPath("$.data.description").value("查询测试描述"))
                .andExpect(jsonPath("$.data.creatorName").value("查询测试用户"))
                .andExpect(jsonPath("$.data.priority").value("HIGH"))
                .andExpect(jsonPath("$.data.status").value("OPEN"));

        verify(ticketService, times(1)).getTicketById(100L);
        verify(ticketService, never()).createTicket(any(CreateTicketRequest.class));
        verifyNoMoreInteractions(ticketService);
    }

    @Test
    void shouldReturnNotFoundWhenTicketDoesNotExist() throws Exception {
        when(ticketService.getTicketById(999L))
                .thenThrow(new BusinessException(ErrorCode.TICKET_NOT_FOUND));

        mockMvc.perform(get("/api/tickets/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40400))
                .andExpect(jsonPath("$.message").value("工单不存在"))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(content().string(not(containsString("BusinessException"))))
                .andExpect(content().string(not(containsString("stackTrace"))));

        verify(ticketService, times(1)).getTicketById(999L);
        verify(ticketService, never()).createTicket(any(CreateTicketRequest.class));
        verifyNoMoreInteractions(ticketService);
    }

    @Test
    void shouldRejectNonPositiveIdWithoutCallingService() throws Exception {
        mockMvc.perform(get("/api/tickets/0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000))
                .andExpect(jsonPath("$.message").value("请求参数校验失败"))
                .andExpect(jsonPath("$.data").value(nullValue()));

        verifyNoInteractions(ticketService);
    }

    @Test
    void shouldRejectNonNumericIdWithoutCallingService() throws Exception {
        mockMvc.perform(get("/api/tickets/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40002))
                .andExpect(jsonPath("$.message").value("请求参数格式错误"))
                .andExpect(jsonPath("$.data").value(nullValue()));

        verifyNoInteractions(ticketService);
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
}
