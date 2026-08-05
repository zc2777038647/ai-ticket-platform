package com.xiaoyang.aiticketplatform.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xiaoyang.aiticketplatform.common.ErrorCode;
import com.xiaoyang.aiticketplatform.dto.request.AssignTicketRequest;
import com.xiaoyang.aiticketplatform.dto.request.CreateTicketRequest;
import com.xiaoyang.aiticketplatform.dto.request.TicketPageQuery;
import com.xiaoyang.aiticketplatform.dto.request.UpdateTicketStatusRequest;
import com.xiaoyang.aiticketplatform.dto.response.PageResponse;
import com.xiaoyang.aiticketplatform.dto.response.TicketAssignmentResponse;
import com.xiaoyang.aiticketplatform.dto.response.TicketResponse;
import com.xiaoyang.aiticketplatform.entity.Ticket;
import com.xiaoyang.aiticketplatform.entity.UserAccount;
import com.xiaoyang.aiticketplatform.enums.TicketPriority;
import com.xiaoyang.aiticketplatform.enums.TicketStatus;
import com.xiaoyang.aiticketplatform.enums.UserRole;
import com.xiaoyang.aiticketplatform.exception.BusinessException;
import com.xiaoyang.aiticketplatform.mapper.TicketMapper;
import com.xiaoyang.aiticketplatform.mapper.UserAccountMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketServiceImplTest {

    @BeforeAll
    static void initializeMybatisPlusTableMetadata() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                Ticket.class
        );
    }

    @Mock
    private TicketMapper ticketMapper;

    @Mock
    private UserAccountMapper userAccountMapper;

    @InjectMocks
    private TicketServiceImpl ticketService;

    @Test
    void shouldCreateTicketAndReturnResponse() {
        CreateTicketRequest request = validRequest();
        when(ticketMapper.insert(any(Ticket.class))).thenAnswer(invocation -> {
            Ticket ticket = invocation.getArgument(0);
            assertNull(ticket.getId());
            ticket.setId(100L);
            return 1;
        });

        TicketResponse response = ticketService.createTicket(request, 100L);

        ArgumentCaptor<Ticket> ticketCaptor = ArgumentCaptor.forClass(Ticket.class);
        verify(ticketMapper, times(1)).insert(ticketCaptor.capture());
        verifyNoMoreInteractions(ticketMapper);

        Ticket insertedTicket = ticketCaptor.getValue();
        assertAll(
                () -> assertEquals("无法登录系统", insertedTicket.getTitle()),
                () -> assertEquals("用户输入正确密码后仍然无法登录", insertedTicket.getDescription()),
                () -> assertEquals("小杨", insertedTicket.getCreatorName()),
                () -> assertEquals(100L, insertedTicket.getCreatorUserId()),
                () -> assertEquals(TicketPriority.HIGH, insertedTicket.getPriority()),
                () -> assertEquals(TicketStatus.OPEN, insertedTicket.getStatus()),
                () -> assertNull(insertedTicket.getCreatedAt()),
                () -> assertNull(insertedTicket.getUpdatedAt()),
                () -> assertEquals(100L, response.id()),
                () -> assertEquals("无法登录系统", response.title()),
                () -> assertEquals("用户输入正确密码后仍然无法登录", response.description()),
                () -> assertEquals("小杨", response.creatorName()),
                () -> assertEquals(TicketPriority.HIGH, response.priority()),
                () -> assertEquals(TicketStatus.OPEN, response.status()),
                () -> assertFalse(Arrays.stream(TicketResponse.class.getRecordComponents())
                        .anyMatch(component -> component.getName().equals("creatorUserId")))
        );
    }

    @Test
    void shouldKeepCreatorNameAsDisplayTextWithoutChangingCreatorUserId() {
        CreateTicketRequest request = new CreateTicketRequest(
                "身份边界测试",
                "展示文本不能决定用户ID",
                "其他人的显示名称",
                TicketPriority.HIGH
        );
        when(ticketMapper.insert(any(Ticket.class))).thenAnswer(invocation -> {
            Ticket ticket = invocation.getArgument(0);
            ticket.setId(101L);
            return 1;
        });

        ticketService.createTicket(request, 100L);

        ArgumentCaptor<Ticket> ticketCaptor = ArgumentCaptor.forClass(Ticket.class);
        verify(ticketMapper).insert(ticketCaptor.capture());
        verifyNoMoreInteractions(ticketMapper);
        assertEquals("其他人的显示名称", ticketCaptor.getValue().getCreatorName());
        assertEquals(100L, ticketCaptor.getValue().getCreatorUserId());
    }

    @Test
    void shouldRejectNullCreatorUserIdWithoutCallingMapper() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ticketService.createTicket(validRequest(), null)
        );

        verifyNoMoreInteractions(ticketMapper);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L})
    void shouldRejectNonPositiveCreatorUserIdWithoutCallingMapper(long creatorUserId) {
        assertThrows(
                IllegalArgumentException.class,
                () -> ticketService.createTicket(validRequest(), creatorUserId)
        );

        verifyNoMoreInteractions(ticketMapper);
    }

    @Test
    void shouldThrowWhenInsertDoesNotAffectExactlyOneRow() {
        when(ticketMapper.insert(any(Ticket.class))).thenReturn(0);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> ticketService.createTicket(validRequest(), 100L)
        );

        assertEquals("创建工单失败：数据库插入影响行数不是 1", exception.getMessage());
        verify(ticketMapper, times(1)).insert(any(Ticket.class));
        verifyNoMoreInteractions(ticketMapper);
    }

    @Test
    void shouldThrowWhenGeneratedIdIsNotBackfilled() {
        when(ticketMapper.insert(any(Ticket.class))).thenReturn(1);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> ticketService.createTicket(validRequest(), 100L)
        );

        assertEquals("创建工单失败：数据库自增 ID 未回填", exception.getMessage());
        verify(ticketMapper, times(1)).insert(any(Ticket.class));
        verifyNoMoreInteractions(ticketMapper);
    }

    @Test
    void shouldReturnOwnedTicketForUserWithSingleConditionalQuery() {
        Ticket ticket = existingTicket();
        ticket.setCreatorUserId(101L);
        when(ticketMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(ticket);

        TicketResponse response = ticketService.getTicketById(100L, 101L, UserRole.USER);

        ArgumentCaptor<LambdaQueryWrapper<Ticket>> wrapperCaptor = ArgumentCaptor.forClass(
                LambdaQueryWrapper.class
        );
        verify(ticketMapper, times(1)).selectOne(wrapperCaptor.capture());
        verify(ticketMapper, never()).selectById(any());
        verifyNoMoreInteractions(ticketMapper);
        LambdaQueryWrapper<Ticket> wrapper = wrapperCaptor.getValue();
        assertNotNull(response);
        assertAll(
                () -> assertTrue(wrapper.getSqlSegment().contains("id")),
                () -> assertTrue(wrapper.getSqlSegment().contains("creator_user_id")),
                () -> assertTrue(wrapper.getParamNameValuePairs().containsValue(100L)),
                () -> assertTrue(wrapper.getParamNameValuePairs().containsValue(101L)),
                () -> assertEquals(100L, response.id()),
                () -> assertEquals("查询测试工单", response.title()),
                () -> assertEquals("查询测试描述", response.description()),
                () -> assertEquals("查询测试用户", response.creatorName()),
                () -> assertEquals(TicketPriority.HIGH, response.priority()),
                () -> assertEquals(TicketStatus.OPEN, response.status())
        );
    }

    @Test
    void shouldHideAnotherUsersTicketFromUser() {
        when(ticketMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> ticketService.getTicketById(999L, 101L, UserRole.USER)
        );

        assertEquals(ErrorCode.TICKET_NOT_FOUND, exception.getErrorCode());
        assertEquals("工单不存在", exception.getMessage());
        verify(ticketMapper, times(1)).selectOne(any(LambdaQueryWrapper.class));
        verify(ticketMapper, never()).selectById(any());
        verifyNoMoreInteractions(ticketMapper);
    }

    @Test
    void shouldAllowAgentToReadAnyExistingTicket() {
        Ticket ticket = existingTicket();
        ticket.setCreatorUserId(101L);
        when(ticketMapper.selectById(100L)).thenReturn(ticket);

        TicketResponse response = ticketService.getTicketById(100L, 200L, UserRole.AGENT);

        assertEquals(100L, response.id());
        verify(ticketMapper).selectById(100L);
        verify(ticketMapper, never()).selectOne(any(LambdaQueryWrapper.class));
        verifyNoMoreInteractions(ticketMapper);
    }

    @Test
    void shouldAllowAdminToReadAnyExistingTicket() {
        Ticket ticket = existingTicket();
        ticket.setCreatorUserId(null);
        when(ticketMapper.selectById(100L)).thenReturn(ticket);

        TicketResponse response = ticketService.getTicketById(100L, 300L, UserRole.ADMIN);

        assertEquals(100L, response.id());
        verify(ticketMapper).selectById(100L);
        verify(ticketMapper, never()).selectOne(any(LambdaQueryWrapper.class));
        verifyNoMoreInteractions(ticketMapper);
    }

    @Test
    void shouldReturnNotFoundForMissingTicketOnAgentPath() {
        when(ticketMapper.selectById(999L)).thenReturn(null);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> ticketService.getTicketById(999L, 200L, UserRole.AGENT)
        );

        assertEquals(ErrorCode.TICKET_NOT_FOUND, exception.getErrorCode());
        verify(ticketMapper).selectById(999L);
        verifyNoMoreInteractions(ticketMapper);
    }

    @Test
    void shouldReturnNotFoundForMissingTicketOnUserPath() {
        when(ticketMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> ticketService.getTicketById(999L, 101L, UserRole.USER)
        );

        assertEquals(ErrorCode.TICKET_NOT_FOUND, exception.getErrorCode());
        verify(ticketMapper).selectOne(any(LambdaQueryWrapper.class));
        verifyNoMoreInteractions(ticketMapper);
    }

    @Test
    void shouldRejectNullRequesterUserIdWithoutCallingMapper() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ticketService.getTicketById(100L, null, UserRole.USER)
        );

        verifyNoMoreInteractions(ticketMapper);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L})
    void shouldRejectNonPositiveRequesterUserIdWithoutCallingMapper(long requesterUserId) {
        assertThrows(
                IllegalArgumentException.class,
                () -> ticketService.getTicketById(100L, requesterUserId, UserRole.USER)
        );

        verifyNoMoreInteractions(ticketMapper);
    }

    @Test
    void shouldRejectNullRequesterRoleWithoutCallingMapper() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ticketService.getTicketById(100L, 101L, null)
        );

        verifyNoMoreInteractions(ticketMapper);
    }

    @Test
    void shouldConvertPagedTicketsAndPaginationMetadata() {
        TicketPageQuery query = new TicketPageQuery(
                2,
                2,
                TicketStatus.OPEN,
                TicketPriority.HIGH,
                " 测试用户 ",
                " 登录 "
        );
        Page<Ticket> mapperPage = new Page<>(2, 2);
        mapperPage.setTotal(5);
        mapperPage.setRecords(List.of(
                ticket(201L, "登录故障一", "描述一", "测试用户", TicketPriority.HIGH, TicketStatus.OPEN),
                ticket(200L, "登录故障二", "描述二", "测试用户", TicketPriority.HIGH, TicketStatus.OPEN)
        ));
        when(ticketMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(mapperPage);

        PageResponse<TicketResponse> response = ticketService.pageTickets(query);

        ArgumentCaptor<Page<Ticket>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        verify(ticketMapper, times(1)).selectPage(pageCaptor.capture(), any(LambdaQueryWrapper.class));
        verify(ticketMapper, never()).insert(any(Ticket.class));
        verify(ticketMapper, never()).selectById(any());
        verifyNoMoreInteractions(ticketMapper);

        Page<Ticket> requestedPage = pageCaptor.getValue();
        assertAll(
                () -> assertEquals(2L, requestedPage.getCurrent()),
                () -> assertEquals(2L, requestedPage.getSize()),
                () -> assertEquals(5L, response.total()),
                () -> assertEquals(3L, response.pages()),
                () -> assertEquals(2L, response.current()),
                () -> assertEquals(2L, response.size()),
                () -> assertEquals(2, response.records().size()),
                () -> assertTicketResponse(
                        response.records().get(0),
                        201L,
                        "登录故障一",
                        "描述一",
                        "测试用户",
                        TicketPriority.HIGH,
                        TicketStatus.OPEN
                ),
                () -> assertTicketResponse(
                        response.records().get(1),
                        200L,
                        "登录故障二",
                        "描述二",
                        "测试用户",
                        TicketPriority.HIGH,
                        TicketStatus.OPEN
                )
        );
    }

    @Test
    void shouldConvertEmptyPage() {
        TicketPageQuery query = new TicketPageQuery(3, 20, null, null, null, null);
        Page<Ticket> mapperPage = new Page<>(3, 20);
        mapperPage.setTotal(0);
        mapperPage.setRecords(List.of());
        when(ticketMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(mapperPage);

        PageResponse<TicketResponse> response = ticketService.pageTickets(query);

        ArgumentCaptor<Page<Ticket>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        verify(ticketMapper, times(1)).selectPage(pageCaptor.capture(), any(LambdaQueryWrapper.class));
        verifyNoMoreInteractions(ticketMapper);
        assertAll(
                () -> assertEquals(3L, pageCaptor.getValue().getCurrent()),
                () -> assertEquals(20L, pageCaptor.getValue().getSize()),
                () -> assertTrue(response.records().isEmpty()),
                () -> assertEquals(0L, response.total()),
                () -> assertEquals(0L, response.pages()),
                () -> assertEquals(3L, response.current()),
                () -> assertEquals(20L, response.size())
        );
    }

    @Test
    void shouldPageOnlyCurrentUsersTickets() {
        TicketPageQuery query = new TicketPageQuery(
                1,
                20,
                TicketStatus.OPEN,
                TicketPriority.HIGH,
                " 测试用户 ",
                " 登录 "
        );
        Page<Ticket> mapperPage = new Page<>(1, 20);
        mapperPage.setTotal(1);
        Ticket ownedTicket = existingTicket();
        ownedTicket.setCreatorUserId(101L);
        mapperPage.setRecords(List.of(ownedTicket));
        when(ticketMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(mapperPage);

        PageResponse<TicketResponse> response = ticketService.pageMyTickets(query, 101L);

        ArgumentCaptor<Page<Ticket>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        ArgumentCaptor<LambdaQueryWrapper<Ticket>> wrapperCaptor = ArgumentCaptor.forClass(
                LambdaQueryWrapper.class
        );
        verify(ticketMapper).selectPage(pageCaptor.capture(), wrapperCaptor.capture());
        verifyNoMoreInteractions(ticketMapper);
        assertAll(
                () -> assertEquals(1L, pageCaptor.getValue().getCurrent()),
                () -> assertEquals(20L, pageCaptor.getValue().getSize()),
                () -> assertEquals(1L, response.total()),
                () -> assertEquals(1, response.records().size()),
                () -> assertEquals(100L, response.records().getFirst().id()),
                () -> assertFalse(Arrays.stream(TicketResponse.class.getRecordComponents())
                        .anyMatch(component -> component.getName().equals("creatorUserId"))),
                () -> assertTrue(wrapperCaptor.getValue().getSqlSegment().contains("creator_user_id")),
                () -> assertTrue(wrapperCaptor.getValue().getSqlSegment().contains("title")),
                () -> assertTrue(wrapperCaptor.getValue().getSqlSegment().contains("description")),
                () -> assertTrue(wrapperCaptor.getValue().getParamNameValuePairs().containsValue(101L))
        );
    }

    @Test
    void shouldRejectNullCreatorUserIdForMyTicketsWithoutCallingMapper() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ticketService.pageMyTickets(
                        new TicketPageQuery(null, null, null, null, null, null),
                        null
                )
        );

        verifyNoMoreInteractions(ticketMapper);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L})
    void shouldRejectNonPositiveCreatorUserIdForMyTicketsWithoutCallingMapper(long creatorUserId) {
        assertThrows(
                IllegalArgumentException.class,
                () -> ticketService.pageMyTickets(
                        new TicketPageQuery(null, null, null, null, null, null),
                        creatorUserId
                )
        );

        verifyNoMoreInteractions(ticketMapper);
    }

    @Test
    void shouldUpdateTicketStatusAndReturnResponse() {
        Ticket ticket = existingTicket();
        UpdateTicketStatusRequest request = new UpdateTicketStatusRequest(TicketStatus.IN_PROGRESS);
        when(ticketMapper.selectById(100L)).thenReturn(ticket);
        when(ticketMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);

        TicketResponse response = ticketService.updateTicketStatus(100L, request);

        ArgumentCaptor<LambdaUpdateWrapper<Ticket>> wrapperCaptor = ArgumentCaptor.forClass(
                LambdaUpdateWrapper.class
        );
        verify(ticketMapper, times(1)).selectById(100L);
        verify(ticketMapper, times(1)).update(isNull(), wrapperCaptor.capture());
        verify(ticketMapper, never()).insert(any(Ticket.class));
        verify(ticketMapper, never()).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
        verifyNoMoreInteractions(ticketMapper);

        assertNotNull(wrapperCaptor.getValue());
        assertAll(
                () -> assertEquals(TicketStatus.IN_PROGRESS, ticket.getStatus()),
                () -> assertTicketResponse(
                        response,
                        100L,
                        "查询测试工单",
                        "查询测试描述",
                        "查询测试用户",
                        TicketPriority.HIGH,
                        TicketStatus.IN_PROGRESS
                )
        );
    }

    @Test
    void shouldThrowWhenUpdatingStatusOfMissingTicket() {
        when(ticketMapper.selectById(999L)).thenReturn(null);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> ticketService.updateTicketStatus(
                        999L,
                        new UpdateTicketStatusRequest(TicketStatus.IN_PROGRESS)
                )
        );

        assertEquals(ErrorCode.TICKET_NOT_FOUND, exception.getErrorCode());
        verify(ticketMapper, times(1)).selectById(999L);
        verifyNoMoreInteractions(ticketMapper);
    }

    @Test
    void shouldRejectInvalidStatusTransitionWithoutUpdating() {
        Ticket ticket = existingTicket();
        when(ticketMapper.selectById(100L)).thenReturn(ticket);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> ticketService.updateTicketStatus(
                        100L,
                        new UpdateTicketStatusRequest(TicketStatus.CLOSED)
                )
        );

        assertEquals(ErrorCode.INVALID_TICKET_STATUS_TRANSITION, exception.getErrorCode());
        assertEquals(TicketStatus.OPEN, ticket.getStatus());
        verify(ticketMapper, times(1)).selectById(100L);
        verifyNoMoreInteractions(ticketMapper);
    }

    @Test
    void shouldThrowConflictWhenConditionalUpdateAffectsNoRows() {
        Ticket ticket = existingTicket();
        when(ticketMapper.selectById(100L)).thenReturn(ticket);
        when(ticketMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(0);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> ticketService.updateTicketStatus(
                        100L,
                        new UpdateTicketStatusRequest(TicketStatus.IN_PROGRESS)
                )
        );

        assertEquals(ErrorCode.TICKET_STATUS_CONFLICT, exception.getErrorCode());
        assertEquals(TicketStatus.OPEN, ticket.getStatus());
        verify(ticketMapper, times(1)).selectById(100L);
        verify(ticketMapper, times(1)).update(isNull(), any(LambdaUpdateWrapper.class));
        verifyNoMoreInteractions(ticketMapper);
    }

    @Test
    void shouldThrowIllegalStateWhenConditionalUpdateAffectsMultipleRows() {
        Ticket ticket = existingTicket();
        when(ticketMapper.selectById(100L)).thenReturn(ticket);
        when(ticketMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(2);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> ticketService.updateTicketStatus(
                        100L,
                        new UpdateTicketStatusRequest(TicketStatus.IN_PROGRESS)
                )
        );

        assertEquals("更新工单状态失败：数据库更新影响行数不是 1", exception.getMessage());
        assertEquals(TicketStatus.OPEN, ticket.getStatus());
        verify(ticketMapper, times(1)).selectById(100L);
        verify(ticketMapper, times(1)).update(isNull(), any(LambdaUpdateWrapper.class));
        verifyNoMoreInteractions(ticketMapper);
    }

    @Test
    void shouldAssignUnassignedTicketToAgent() {
        Ticket ticket = existingTicket();
        UserAccount target = userAccount(200L, "agent_a", "处理人甲", UserRole.AGENT);
        when(ticketMapper.selectById(100L)).thenReturn(ticket);
        when(userAccountMapper.selectById(200L)).thenReturn(target);
        when(ticketMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);

        TicketAssignmentResponse response = ticketService.assignTicket(
                100L,
                new AssignTicketRequest(200L)
        );

        ArgumentCaptor<LambdaUpdateWrapper<Ticket>> wrapperCaptor = ArgumentCaptor.forClass(
                LambdaUpdateWrapper.class
        );
        verify(ticketMapper).selectById(100L);
        verify(userAccountMapper).selectById(200L);
        verify(ticketMapper).update(isNull(), wrapperCaptor.capture());
        verify(ticketMapper, never()).updateById(any(Ticket.class));
        verifyNoMoreInteractions(ticketMapper, userAccountMapper);

        LambdaUpdateWrapper<Ticket> wrapper = wrapperCaptor.getValue();
        assertAll(
                () -> assertTrue(wrapper.getSqlSegment().contains("id")),
                () -> assertTrue(wrapper.getSqlSegment().contains("assignee_user_id IS NULL")),
                () -> assertTrue(wrapper.getSqlSet().contains("assignee_user_id")),
                () -> assertTrue(wrapper.getParamNameValuePairs().containsValue(100L)),
                () -> assertTrue(wrapper.getParamNameValuePairs().containsValue(200L)),
                () -> assertEquals(200L, ticket.getAssigneeUserId()),
                () -> assertEquals(100L, response.ticketId()),
                () -> assertEquals(200L, response.assigneeUserId()),
                () -> assertEquals("agent_a", response.assigneeUsername()),
                () -> assertEquals("处理人甲", response.assigneeDisplayName())
        );
    }

    @Test
    void shouldReassignTicketFromOneAgentToAnother() {
        Ticket ticket = existingTicket();
        ticket.setAssigneeUserId(201L);
        UserAccount target = userAccount(202L, "agent_b", "处理人乙", UserRole.AGENT);
        when(ticketMapper.selectById(100L)).thenReturn(ticket);
        when(userAccountMapper.selectById(202L)).thenReturn(target);
        when(ticketMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);

        TicketAssignmentResponse response = ticketService.assignTicket(
                100L,
                new AssignTicketRequest(202L)
        );

        ArgumentCaptor<LambdaUpdateWrapper<Ticket>> wrapperCaptor = ArgumentCaptor.forClass(
                LambdaUpdateWrapper.class
        );
        verify(ticketMapper).selectById(100L);
        verify(userAccountMapper).selectById(202L);
        verify(ticketMapper).update(isNull(), wrapperCaptor.capture());
        verify(ticketMapper, never()).updateById(any(Ticket.class));
        verifyNoMoreInteractions(ticketMapper, userAccountMapper);
        assertAll(
                () -> assertTrue(wrapperCaptor.getValue().getSqlSegment().contains("assignee_user_id")),
                () -> assertTrue(wrapperCaptor.getValue().getParamNameValuePairs().containsValue(201L)),
                () -> assertTrue(wrapperCaptor.getValue().getParamNameValuePairs().containsValue(202L)),
                () -> assertEquals(202L, ticket.getAssigneeUserId()),
                () -> assertEquals(202L, response.assigneeUserId()),
                () -> assertEquals("agent_b", response.assigneeUsername()),
                () -> assertEquals("处理人乙", response.assigneeDisplayName())
        );
    }

    @Test
    void shouldRejectAssignmentWhenTicketDoesNotExist() {
        when(ticketMapper.selectById(999L)).thenReturn(null);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> ticketService.assignTicket(999L, new AssignTicketRequest(200L))
        );

        assertEquals(ErrorCode.TICKET_NOT_FOUND, exception.getErrorCode());
        verify(ticketMapper).selectById(999L);
        verifyNoMoreInteractions(ticketMapper);
        verifyNoInteractions(userAccountMapper);
    }

    @Test
    void shouldRejectAssignmentWhenTargetUserDoesNotExist() {
        Ticket ticket = existingTicket();
        when(ticketMapper.selectById(100L)).thenReturn(ticket);
        when(userAccountMapper.selectById(999L)).thenReturn(null);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> ticketService.assignTicket(100L, new AssignTicketRequest(999L))
        );

        assertEquals(ErrorCode.ASSIGNEE_NOT_FOUND, exception.getErrorCode());
        assertNull(ticket.getAssigneeUserId());
        verify(ticketMapper).selectById(100L);
        verify(userAccountMapper).selectById(999L);
        verifyNoMoreInteractions(ticketMapper, userAccountMapper);
    }

    @ParameterizedTest
    @ValueSource(strings = {"USER", "ADMIN"})
    void shouldRejectNonAgentTargetRole(String roleName) {
        Ticket ticket = existingTicket();
        UserAccount target = userAccount(200L, "target", "目标用户", UserRole.valueOf(roleName));
        when(ticketMapper.selectById(100L)).thenReturn(ticket);
        when(userAccountMapper.selectById(200L)).thenReturn(target);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> ticketService.assignTicket(100L, new AssignTicketRequest(200L))
        );

        assertEquals(ErrorCode.INVALID_ASSIGNEE_ROLE, exception.getErrorCode());
        assertNull(ticket.getAssigneeUserId());
        verify(ticketMapper).selectById(100L);
        verify(userAccountMapper).selectById(200L);
        verifyNoMoreInteractions(ticketMapper, userAccountMapper);
    }

    @Test
    void shouldRejectDuplicateAssignmentToSameAgent() {
        Ticket ticket = existingTicket();
        ticket.setAssigneeUserId(200L);
        UserAccount target = userAccount(200L, "agent_a", "处理人甲", UserRole.AGENT);
        when(ticketMapper.selectById(100L)).thenReturn(ticket);
        when(userAccountMapper.selectById(200L)).thenReturn(target);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> ticketService.assignTicket(100L, new AssignTicketRequest(200L))
        );

        assertEquals(ErrorCode.TICKET_ALREADY_ASSIGNED, exception.getErrorCode());
        assertEquals(200L, ticket.getAssigneeUserId());
        verify(ticketMapper).selectById(100L);
        verify(userAccountMapper).selectById(200L);
        verifyNoMoreInteractions(ticketMapper, userAccountMapper);
    }

    @Test
    void shouldKeepInMemoryAssigneeWhenAssignmentConditionIsStale() {
        Ticket ticket = existingTicket();
        ticket.setAssigneeUserId(201L);
        UserAccount target = userAccount(202L, "agent_b", "处理人乙", UserRole.AGENT);
        when(ticketMapper.selectById(100L)).thenReturn(ticket);
        when(userAccountMapper.selectById(202L)).thenReturn(target);
        when(ticketMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(0);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> ticketService.assignTicket(100L, new AssignTicketRequest(202L))
        );

        assertEquals(ErrorCode.TICKET_ASSIGNMENT_CONFLICT, exception.getErrorCode());
        assertEquals(201L, ticket.getAssigneeUserId());
        verify(ticketMapper).selectById(100L);
        verify(userAccountMapper).selectById(202L);
        verify(ticketMapper).update(isNull(), any(LambdaUpdateWrapper.class));
        verifyNoMoreInteractions(ticketMapper, userAccountMapper);
    }

    @Test
    void shouldKeepInMemoryAssigneeWhenAssignmentAffectsMultipleRows() {
        Ticket ticket = existingTicket();
        ticket.setAssigneeUserId(201L);
        UserAccount target = userAccount(202L, "agent_b", "处理人乙", UserRole.AGENT);
        when(ticketMapper.selectById(100L)).thenReturn(ticket);
        when(userAccountMapper.selectById(202L)).thenReturn(target);
        when(ticketMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(2);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> ticketService.assignTicket(100L, new AssignTicketRequest(202L))
        );

        assertEquals("指派工单失败：数据库更新影响行数不是 1", exception.getMessage());
        assertEquals(201L, ticket.getAssigneeUserId());
        verify(ticketMapper).selectById(100L);
        verify(userAccountMapper).selectById(202L);
        verify(ticketMapper).update(isNull(), any(LambdaUpdateWrapper.class));
        verifyNoMoreInteractions(ticketMapper, userAccountMapper);
    }

    @Test
    void shouldRejectNullAssignmentTicketIdWithoutCallingMappers() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ticketService.assignTicket(null, new AssignTicketRequest(200L))
        );

        verifyNoInteractions(ticketMapper, userAccountMapper);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L})
    void shouldRejectNonPositiveAssignmentTicketIdWithoutCallingMappers(long ticketId) {
        assertThrows(
                IllegalArgumentException.class,
                () -> ticketService.assignTicket(ticketId, new AssignTicketRequest(200L))
        );

        verifyNoInteractions(ticketMapper, userAccountMapper);
    }

    private static CreateTicketRequest validRequest() {
        return new CreateTicketRequest(
                "无法登录系统",
                "用户输入正确密码后仍然无法登录",
                "小杨",
                TicketPriority.HIGH
        );
    }

    private static Ticket existingTicket() {
        return ticket(
                100L,
                "查询测试工单",
                "查询测试描述",
                "查询测试用户",
                TicketPriority.HIGH,
                TicketStatus.OPEN
        );
    }

    private static Ticket ticket(
            Long id,
            String title,
            String description,
            String creatorName,
            TicketPriority priority,
            TicketStatus status
    ) {
        Ticket ticket = new Ticket();
        ticket.setId(id);
        ticket.setTitle(title);
        ticket.setDescription(description);
        ticket.setCreatorName(creatorName);
        ticket.setPriority(priority);
        ticket.setStatus(status);
        return ticket;
    }

    private static UserAccount userAccount(
            Long id,
            String username,
            String displayName,
            UserRole role
    ) {
        UserAccount user = new UserAccount();
        user.setId(id);
        user.setUsername(username);
        user.setDisplayName(displayName);
        user.setRole(role);
        return user;
    }

    private static void assertTicketResponse(
            TicketResponse response,
            Long id,
            String title,
            String description,
            String creatorName,
            TicketPriority priority,
            TicketStatus status
    ) {
        assertAll(
                () -> assertEquals(id, response.id()),
                () -> assertEquals(title, response.title()),
                () -> assertEquals(description, response.description()),
                () -> assertEquals(creatorName, response.creatorName()),
                () -> assertEquals(priority, response.priority()),
                () -> assertEquals(status, response.status())
        );
    }
}
