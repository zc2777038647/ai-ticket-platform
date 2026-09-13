package com.xiaoyang.aiticketplatform.controller;

import com.xiaoyang.aiticketplatform.config.AiServiceProperties;
import com.xiaoyang.aiticketplatform.dto.response.TicketOperationLogResponse;
import com.xiaoyang.aiticketplatform.dto.response.TicketResponse;
import com.xiaoyang.aiticketplatform.service.TicketService;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

@RestController
@RequestMapping("/internal/ai/tickets")
public class AiInternalToolController {

    private static final String TOKEN_HEADER = "X-Internal-AI-Token";

    private final TicketService ticketService;
    private final AiServiceProperties properties;

    public AiInternalToolController(TicketService ticketService, AiServiceProperties properties) {
        this.ticketService = ticketService;
        this.properties = properties;
    }

    @GetMapping("/{id}")
    public TicketResponse getTicket(
            @PathVariable @Positive Long id,
            @RequestHeader(value = TOKEN_HEADER, required = false) String token
    ) {
        requireToken(token);
        return ticketService.getTicketForAiTool(id);
    }

    @GetMapping("/{id}/history")
    public List<TicketOperationLogResponse> getHistory(
            @PathVariable @Positive Long id,
            @RequestHeader(value = TOKEN_HEADER, required = false) String token
    ) {
        requireToken(token);
        return ticketService.getTicketHistoryForAiTool(id);
    }

    private void requireToken(String providedToken) {
        byte[] expected = properties.getInternalToken().getBytes(StandardCharsets.UTF_8);
        byte[] actual = providedToken == null
                ? new byte[0]
                : providedToken.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, actual)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid internal token");
        }
    }
}
