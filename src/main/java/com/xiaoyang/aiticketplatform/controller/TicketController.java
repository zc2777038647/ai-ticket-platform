package com.xiaoyang.aiticketplatform.controller;

import com.xiaoyang.aiticketplatform.common.ApiResponse;
import com.xiaoyang.aiticketplatform.dto.request.CreateTicketRequest;
import com.xiaoyang.aiticketplatform.dto.request.TicketPageQuery;
import com.xiaoyang.aiticketplatform.dto.response.PageResponse;
import com.xiaoyang.aiticketplatform.dto.response.TicketResponse;
import com.xiaoyang.aiticketplatform.service.TicketService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TicketResponse>> createTicket(
            @Valid @RequestBody CreateTicketRequest request
    ) {
        TicketResponse ticketResponse = ticketService.createTicket(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(ticketResponse));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TicketResponse>> getTicketById(
            @PathVariable @Positive(message = "工单ID必须为正数") Long id
    ) {
        TicketResponse ticketResponse = ticketService.getTicketById(id);
        return ResponseEntity.ok(ApiResponse.success(ticketResponse));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<TicketResponse>>> pageTickets(
            @Valid @ModelAttribute TicketPageQuery query
    ) {
        PageResponse<TicketResponse> pageResponse = ticketService.pageTickets(query);
        return ResponseEntity.ok(ApiResponse.success(pageResponse));
    }
}
