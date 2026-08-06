package com.xiaoyang.aiticketplatform.controller;

import com.xiaoyang.aiticketplatform.common.ApiResponse;
import com.xiaoyang.aiticketplatform.dto.request.CreateTicketRequest;
import com.xiaoyang.aiticketplatform.dto.request.AssignTicketRequest;
import com.xiaoyang.aiticketplatform.dto.request.TicketPageQuery;
import com.xiaoyang.aiticketplatform.dto.request.UpdateTicketStatusRequest;
import com.xiaoyang.aiticketplatform.dto.response.PageResponse;
import com.xiaoyang.aiticketplatform.dto.response.TicketAssignmentResponse;
import com.xiaoyang.aiticketplatform.dto.response.TicketResponse;
import com.xiaoyang.aiticketplatform.enums.UserRole;
import com.xiaoyang.aiticketplatform.idempotency.CreateTicketIdempotencyCoordinator;
import com.xiaoyang.aiticketplatform.service.TicketService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    private final TicketService ticketService;
    private final CreateTicketIdempotencyCoordinator createTicketIdempotencyCoordinator;

    public TicketController(
            TicketService ticketService,
            CreateTicketIdempotencyCoordinator createTicketIdempotencyCoordinator
    ) {
        this.ticketService = ticketService;
        this.createTicketIdempotencyCoordinator = createTicketIdempotencyCoordinator;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TicketResponse>> createTicket(
            @Valid @RequestBody CreateTicketRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            JwtAuthenticationToken authentication
    ) {
        Long creatorUserId = Long.valueOf(authentication.getName());
        TicketResponse ticketResponse = createTicketIdempotencyCoordinator.createTicket(
                creatorUserId,
                idempotencyKey,
                request
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(ticketResponse));
    }

    @GetMapping("/mine")
    public ResponseEntity<ApiResponse<PageResponse<TicketResponse>>> pageMyTickets(
            @Valid @ModelAttribute TicketPageQuery query,
            JwtAuthenticationToken authentication
    ) {
        PageResponse<TicketResponse> pageResponse = ticketService.pageMyTickets(
                query,
                currentUserId(authentication)
        );
        return ResponseEntity.ok(ApiResponse.success(pageResponse));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TicketResponse>> getTicketById(
            @PathVariable @Positive(message = "工单ID必须为正数") Long id,
            JwtAuthenticationToken authentication
    ) {
        TicketResponse ticketResponse = ticketService.getTicketById(
                id,
                currentUserId(authentication),
                currentUserRole(authentication)
        );
        return ResponseEntity.ok(ApiResponse.success(ticketResponse));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<TicketResponse>>> pageTickets(
            @Valid @ModelAttribute TicketPageQuery query
    ) {
        PageResponse<TicketResponse> pageResponse = ticketService.pageTickets(query);
        return ResponseEntity.ok(ApiResponse.success(pageResponse));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<TicketResponse>> updateTicketStatus(
            @PathVariable @Positive(message = "工单ID必须为正数") Long id,
            @Valid @RequestBody UpdateTicketStatusRequest request,
            JwtAuthenticationToken authentication
    ) {
        TicketResponse ticketResponse = ticketService.updateTicketStatus(
                id,
                request,
                currentUserId(authentication)
        );
        return ResponseEntity.ok(ApiResponse.success(ticketResponse));
    }

    @PatchMapping("/{id}/assignee")
    public ResponseEntity<ApiResponse<TicketAssignmentResponse>> assignTicket(
            @PathVariable @Positive(message = "工单ID必须为正数") Long id,
            @Valid @RequestBody AssignTicketRequest request,
            JwtAuthenticationToken authentication
    ) {
        TicketAssignmentResponse response = ticketService.assignTicket(
                id,
                request,
                currentUserId(authentication)
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    private Long currentUserId(JwtAuthenticationToken authentication) {
        return Long.valueOf(authentication.getName());
    }

    private UserRole currentUserRole(JwtAuthenticationToken authentication) {
        return UserRole.valueOf(authentication.getToken().getClaimAsString("role"));
    }
}
