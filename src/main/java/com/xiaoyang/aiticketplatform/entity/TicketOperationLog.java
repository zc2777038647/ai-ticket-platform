package com.xiaoyang.aiticketplatform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.xiaoyang.aiticketplatform.enums.TicketOperationType;

import java.time.LocalDateTime;

@TableName("ticket_operation_logs")
public class TicketOperationLog {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long ticketId;

    private Long operatorUserId;

    private TicketOperationType operationType;

    private String beforeValue;

    private String afterValue;

    private LocalDateTime createdAt;

    public TicketOperationLog() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTicketId() {
        return ticketId;
    }

    public void setTicketId(Long ticketId) {
        this.ticketId = ticketId;
    }

    public Long getOperatorUserId() {
        return operatorUserId;
    }

    public void setOperatorUserId(Long operatorUserId) {
        this.operatorUserId = operatorUserId;
    }

    public TicketOperationType getOperationType() {
        return operationType;
    }

    public void setOperationType(TicketOperationType operationType) {
        this.operationType = operationType;
    }

    public String getBeforeValue() {
        return beforeValue;
    }

    public void setBeforeValue(String beforeValue) {
        this.beforeValue = beforeValue;
    }

    public String getAfterValue() {
        return afterValue;
    }

    public void setAfterValue(String afterValue) {
        this.afterValue = afterValue;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
