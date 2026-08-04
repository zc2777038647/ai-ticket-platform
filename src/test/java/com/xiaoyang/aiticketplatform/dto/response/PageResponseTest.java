package com.xiaoyang.aiticketplatform.dto.response;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PageResponseTest {

    @Test
    void shouldCreateLegalPageResponse() {
        PageResponse<String> response = new PageResponse<>(List.of("工单1", "工单2"), 3, 2, 1, 2);

        assertEquals(List.of("工单1", "工单2"), response.records());
        assertEquals(3, response.total());
        assertEquals(2, response.pages());
        assertEquals(1, response.current());
        assertEquals(2, response.size());
    }

    @Test
    void shouldAllowEmptyPage() {
        PageResponse<String> response = new PageResponse<>(List.of(), 0, 0, 1, 20);

        assertTrue(response.records().isEmpty());
        assertEquals(0, response.total());
        assertEquals(0, response.pages());
        assertEquals(1, response.current());
        assertEquals(20, response.size());
    }

    @Test
    void shouldRejectNullRecords() {
        assertThrows(
                NullPointerException.class,
                () -> new PageResponse<String>(null, 0, 0, 1, 20)
        );
    }

    @Test
    void shouldDefensivelyCopyRecords() {
        List<String> source = new ArrayList<>(List.of("工单1", "工单2"));
        PageResponse<String> response = new PageResponse<>(source, 2, 1, 1, 20);

        source.add("工单3");

        assertEquals(List.of("工单1", "工单2"), response.records());
        assertThrows(UnsupportedOperationException.class, () -> response.records().add("工单4"));
    }

    @Test
    void shouldRejectNegativeTotal() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PageResponse<>(List.of(), -1, 0, 1, 20)
        );
    }

    @Test
    void shouldRejectNegativePages() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PageResponse<>(List.of(), 0, -1, 1, 20)
        );
    }

    @Test
    void shouldRejectCurrentLessThanOne() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PageResponse<>(List.of(), 0, 0, 0, 20)
        );
    }

    @Test
    void shouldRejectSizeLessThanOne() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PageResponse<>(List.of(), 0, 0, 1, 0)
        );
    }
}
