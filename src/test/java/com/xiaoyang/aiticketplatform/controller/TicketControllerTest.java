package com.xiaoyang.aiticketplatform.controller;

import com.xiaoyang.aiticketplatform.common.ErrorCode;
import com.xiaoyang.aiticketplatform.dto.request.CreateTicketRequest;
import com.xiaoyang.aiticketplatform.dto.request.TicketPageQuery;
import com.xiaoyang.aiticketplatform.dto.request.UpdateTicketStatusRequest;
import com.xiaoyang.aiticketplatform.dto.response.PageResponse;
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

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

    @Test
    void shouldUseDefaultPaginationWhenQueryParametersAreAbsent() throws Exception {
        PageResponse<TicketResponse> serviceResponse = new PageResponse<>(List.of(), 0, 0, 1, 20);
        when(ticketService.pageTickets(any(TicketPageQuery.class))).thenReturn(serviceResponse);

        mockMvc.perform(get("/api/tickets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.records").isEmpty())
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.pages").value(0))
                .andExpect(jsonPath("$.data.current").value(1))
                .andExpect(jsonPath("$.data.size").value(20));

        ArgumentCaptor<TicketPageQuery> queryCaptor = ArgumentCaptor.forClass(TicketPageQuery.class);
        verify(ticketService, times(1)).pageTickets(queryCaptor.capture());
        verifyNoMoreInteractions(ticketService);
        TicketPageQuery query = queryCaptor.getValue();
        assertAll(
                () -> assertEquals(1, query.page()),
                () -> assertEquals(20, query.size()),
                () -> assertNull(query.status()),
                () -> assertNull(query.priority()),
                () -> assertNull(query.creatorName()),
                () -> assertNull(query.keyword())
        );
    }

    @Test
    void shouldBindCompletePaginationQueryAndReturnResponse() throws Exception {
        TicketResponse ticket = new TicketResponse(
                200L,
                "登录异常",
                "无法登录系统",
                "张三",
                TicketPriority.HIGH,
                TicketStatus.OPEN
        );
        PageResponse<TicketResponse> serviceResponse = new PageResponse<>(List.of(ticket), 11, 2, 2, 10);
        when(ticketService.pageTickets(any(TicketPageQuery.class))).thenReturn(serviceResponse);

        mockMvc.perform(get("/api/tickets")
                        .param("page", "2")
                        .param("size", "10")
                        .param("status", "OPEN")
                        .param("priority", "HIGH")
                        .param("creatorName", " 张三 ")
                        .param("keyword", " 登录 "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.total").value(11))
                .andExpect(jsonPath("$.data.pages").value(2))
                .andExpect(jsonPath("$.data.current").value(2))
                .andExpect(jsonPath("$.data.size").value(10))
                .andExpect(jsonPath("$.data.records.length()").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(200))
                .andExpect(jsonPath("$.data.records[0].priority").value("HIGH"))
                .andExpect(jsonPath("$.data.records[0].status").value("OPEN"));

        ArgumentCaptor<TicketPageQuery> queryCaptor = ArgumentCaptor.forClass(TicketPageQuery.class);
        verify(ticketService, times(1)).pageTickets(queryCaptor.capture());
        verifyNoMoreInteractions(ticketService);
        TicketPageQuery query = queryCaptor.getValue();
        assertAll(
                () -> assertEquals(2, query.page()),
                () -> assertEquals(10, query.size()),
                () -> assertEquals(TicketStatus.OPEN, query.status()),
                () -> assertEquals(TicketPriority.HIGH, query.priority()),
                () -> assertEquals(" 张三 ", query.creatorName()),
                () -> assertEquals(" 登录 ", query.keyword())
        );
    }

    @Test
    void shouldRejectZeroPageWithoutCallingService() throws Exception {
        mockMvc.perform(get("/api/tickets").param("page", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000))
                .andExpect(jsonPath("$.message").value("请求参数校验失败"))
                .andExpect(jsonPath("$.data.page").value("页码必须大于等于1"));

        verifyNoInteractions(ticketService);
    }

    @Test
    void shouldRejectOversizedPageSizeWithoutCallingService() throws Exception {
        mockMvc.perform(get("/api/tickets").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000))
                .andExpect(jsonPath("$.message").value("请求参数校验失败"))
                .andExpect(jsonPath("$.data.size").value("每页数量不能超过100"));

        verifyNoInteractions(ticketService);
    }

    @Test
    void shouldRejectNonNumericPageWithoutCallingService() throws Exception {
        mockMvc.perform(get("/api/tickets").param("page", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40002))
                .andExpect(jsonPath("$.message").value("请求参数格式错误"))
                .andExpect(jsonPath("$.data").value(nullValue()));

        verifyNoInteractions(ticketService);
    }

    @Test
    void shouldRejectUnknownStatusWithoutCallingService() throws Exception {
        mockMvc.perform(get("/api/tickets").param("status", "UNKNOWN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40002))
                .andExpect(jsonPath("$.message").value("请求参数格式错误"))
                .andExpect(jsonPath("$.data").value(nullValue()));

        verifyNoInteractions(ticketService);
    }

    @Test
    void shouldUpdateTicketStatus() throws Exception {
        TicketResponse serviceResponse = new TicketResponse(
                100L,
                "状态更新测试工单",
                "状态更新测试描述",
                "状态更新测试用户",
                TicketPriority.HIGH,
                TicketStatus.IN_PROGRESS
        );
        when(ticketService.updateTicketStatus(
                100L,
                new UpdateTicketStatusRequest(TicketStatus.IN_PROGRESS)
        )).thenReturn(serviceResponse);

        mockMvc.perform(patch("/api/tickets/100/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusRequestJson("IN_PROGRESS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.id").value(100))
                .andExpect(jsonPath("$.data.title").value("状态更新测试工单"))
                .andExpect(jsonPath("$.data.description").value("状态更新测试描述"))
                .andExpect(jsonPath("$.data.creatorName").value("状态更新测试用户"))
                .andExpect(jsonPath("$.data.priority").value("HIGH"))
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));

        ArgumentCaptor<UpdateTicketStatusRequest> requestCaptor = ArgumentCaptor.forClass(
                UpdateTicketStatusRequest.class
        );
        verify(ticketService, times(1)).updateTicketStatus(eq(100L), requestCaptor.capture());
        verify(ticketService, never()).createTicket(any(CreateTicketRequest.class));
        verify(ticketService, never()).getTicketById(any());
        verify(ticketService, never()).pageTickets(any(TicketPageQuery.class));
        verifyNoMoreInteractions(ticketService);
        assertEquals(TicketStatus.IN_PROGRESS, requestCaptor.getValue().status());
    }

    @Test
    void shouldRejectNullUpdateStatusWithoutCallingService() throws Exception {
        mockMvc.perform(patch("/api/tickets/100/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": null
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000))
                .andExpect(jsonPath("$.message").value("请求参数校验失败"))
                .andExpect(jsonPath("$.data.status").value("目标状态不能为空"));

        verifyNoInteractions(ticketService);
    }

    @Test
    void shouldRejectMissingUpdateStatusWithoutCallingService() throws Exception {
        mockMvc.perform(patch("/api/tickets/100/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000))
                .andExpect(jsonPath("$.message").value("请求参数校验失败"))
                .andExpect(jsonPath("$.data.status").value("目标状态不能为空"));

        verifyNoInteractions(ticketService);
    }

    @Test
    void shouldRejectUnknownUpdateStatusWithoutCallingService() throws Exception {
        mockMvc.perform(patch("/api/tickets/100/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusRequestJson("UNKNOWN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value("请求体格式错误"))
                .andExpect(jsonPath("$.data").value(nullValue()));

        verifyNoInteractions(ticketService);
    }

    @Test
    void shouldRejectZeroUpdateTicketIdWithoutCallingService() throws Exception {
        mockMvc.perform(patch("/api/tickets/0/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusRequestJson("IN_PROGRESS")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000))
                .andExpect(jsonPath("$.message").value("请求参数校验失败"))
                .andExpect(jsonPath("$.data").value(nullValue()));

        verifyNoInteractions(ticketService);
    }

    @Test
    void shouldRejectNonNumericUpdateTicketIdWithoutCallingService() throws Exception {
        mockMvc.perform(patch("/api/tickets/abc/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusRequestJson("IN_PROGRESS")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40002))
                .andExpect(jsonPath("$.message").value("请求参数格式错误"))
                .andExpect(jsonPath("$.data").value(nullValue()));

        verifyNoInteractions(ticketService);
    }

    @Test
    void shouldReturnNotFoundWhenUpdatingMissingTicket() throws Exception {
        assertStatusUpdateBusinessError(ErrorCode.TICKET_NOT_FOUND, 404);
    }

    @Test
    void shouldReturnConflictForInvalidStatusTransition() throws Exception {
        assertStatusUpdateBusinessError(ErrorCode.INVALID_TICKET_STATUS_TRANSITION, 409);
    }

    @Test
    void shouldReturnConflictForConcurrentStatusChange() throws Exception {
        assertStatusUpdateBusinessError(ErrorCode.TICKET_STATUS_CONFLICT, 409);
    }

    private void assertStatusUpdateBusinessError(ErrorCode errorCode, int expectedHttpStatus)
            throws Exception {
        when(ticketService.updateTicketStatus(
                100L,
                new UpdateTicketStatusRequest(TicketStatus.IN_PROGRESS)
        )).thenThrow(new BusinessException(errorCode));

        mockMvc.perform(patch("/api/tickets/100/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusRequestJson("IN_PROGRESS")))
                .andExpect(status().is(expectedHttpStatus))
                .andExpect(jsonPath("$.code").value(errorCode.getCode()))
                .andExpect(jsonPath("$.message").value(errorCode.getMessage()))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(content().string(not(containsString("BusinessException"))))
                .andExpect(content().string(not(containsString("java.lang"))))
                .andExpect(content().string(not(containsString("stackTrace"))))
                .andExpect(content().string(not(containsString("TicketServiceImpl"))));

        verify(ticketService, times(1)).updateTicketStatus(
                100L,
                new UpdateTicketStatusRequest(TicketStatus.IN_PROGRESS)
        );
        verifyNoMoreInteractions(ticketService);
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

    private static String statusRequestJson(String status) {
        return """
                {
                  "status": "%s"
                }
                """.formatted(status);
    }
}
