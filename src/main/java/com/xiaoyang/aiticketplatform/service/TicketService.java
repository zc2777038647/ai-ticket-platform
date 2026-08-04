package com.xiaoyang.aiticketplatform.service;

import com.xiaoyang.aiticketplatform.dto.request.CreateTicketRequest;
import com.xiaoyang.aiticketplatform.dto.response.TicketResponse;

public interface TicketService {

    TicketResponse createTicket(CreateTicketRequest request);

    TicketResponse getTicketById(Long id);
}
