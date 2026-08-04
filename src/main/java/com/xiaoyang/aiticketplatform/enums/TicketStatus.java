package com.xiaoyang.aiticketplatform.enums;

public enum TicketStatus {
    OPEN,
    IN_PROGRESS,
    RESOLVED,
    CLOSED;

    public boolean canTransitionTo(TicketStatus targetStatus) {
        if (targetStatus == null) {
            return false;
        }

        return switch (this) {
            case OPEN -> targetStatus == IN_PROGRESS;
            case IN_PROGRESS -> targetStatus == RESOLVED;
            case RESOLVED -> targetStatus == CLOSED;
            case CLOSED -> false;
        };
    }
}
