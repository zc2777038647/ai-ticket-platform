package com.xiaoyang.aiticketplatform.idempotency;

import com.xiaoyang.aiticketplatform.dto.request.CreateTicketRequest;
import com.xiaoyang.aiticketplatform.enums.TicketPriority;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreateTicketRequestFingerprintTest {

    private final CreateTicketRequestFingerprint fingerprint = new CreateTicketRequestFingerprint();

    @Test
    void shouldGenerateSameFingerprintForIdenticalRequest() {
        assertEquals(fingerprint.fingerprint(request()), fingerprint.fingerprint(request()));
    }

    @Test
    void shouldChangeWhenTitleChanges() {
        assertNotEquals(fingerprint.fingerprint(request()), fingerprint.fingerprint(
                new CreateTicketRequest("不同标题", "描述", "小杨", TicketPriority.HIGH)));
    }

    @Test
    void shouldChangeWhenDescriptionChanges() {
        assertNotEquals(fingerprint.fingerprint(request()), fingerprint.fingerprint(
                new CreateTicketRequest("标题", "不同描述", "小杨", TicketPriority.HIGH)));
    }

    @Test
    void shouldChangeWhenCreatorNameChanges() {
        assertNotEquals(fingerprint.fingerprint(request()), fingerprint.fingerprint(
                new CreateTicketRequest("标题", "描述", "另一用户", TicketPriority.HIGH)));
    }

    @Test
    void shouldChangeWhenPriorityChanges() {
        assertNotEquals(fingerprint.fingerprint(request()), fingerprint.fingerprint(
                new CreateTicketRequest("标题", "描述", "小杨", TicketPriority.URGENT)));
    }

    @Test
    void shouldAvoidAmbiguityForDelimitersAndNewlines() {
        CreateTicketRequest first = new CreateTicketRequest(
                "a:|\nb", "c", "name:|\n", TicketPriority.HIGH);
        CreateTicketRequest second = new CreateTicketRequest(
                "a", "|\nb:c", "name:|\n", TicketPriority.HIGH);

        assertNotEquals(fingerprint.fingerprint(first), fingerprint.fingerprint(second));
    }

    @Test
    void shouldReturnLowercaseSha256Hex() {
        assertTrue(fingerprint.fingerprint(request()).matches("[0-9a-f]{64}"));
    }

    @Test
    void shouldRejectNullRequest() {
        assertThrows(NullPointerException.class, () -> fingerprint.fingerprint(null));
    }

    private static CreateTicketRequest request() {
        return new CreateTicketRequest("标题", "描述", "小杨", TicketPriority.HIGH);
    }
}
