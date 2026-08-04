package com.xiaoyang.aiticketplatform.enums;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TicketStatusTransitionTest {

    @Test
    void shouldAllowOnlyTheThreeForwardTransitions() {
        assertAll(
                () -> assertTrue(TicketStatus.OPEN.canTransitionTo(TicketStatus.IN_PROGRESS)),
                () -> assertTrue(TicketStatus.IN_PROGRESS.canTransitionTo(TicketStatus.RESOLVED)),
                () -> assertTrue(TicketStatus.RESOLVED.canTransitionTo(TicketStatus.CLOSED))
        );
    }

    @Test
    void shouldMatchTheCompleteFourByFourTransitionMatrix() {
        Map<TicketStatus, TicketStatus> allowedTargets = Map.of(
                TicketStatus.OPEN, TicketStatus.IN_PROGRESS,
                TicketStatus.IN_PROGRESS, TicketStatus.RESOLVED,
                TicketStatus.RESOLVED, TicketStatus.CLOSED
        );

        for (TicketStatus currentStatus : TicketStatus.values()) {
            for (TicketStatus targetStatus : TicketStatus.values()) {
                boolean expected = targetStatus == allowedTargets.get(currentStatus);
                assertEquals(
                        expected,
                        currentStatus.canTransitionTo(targetStatus),
                        () -> currentStatus + " -> " + targetStatus
                );
            }
        }
    }

    @Test
    void shouldRejectNullTargetFromEveryStatus() {
        for (TicketStatus currentStatus : TicketStatus.values()) {
            assertFalse(
                    currentStatus.canTransitionTo(null),
                    () -> currentStatus + " -> null"
            );
        }
    }
}
