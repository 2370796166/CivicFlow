package com.civicflow.common.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class ApiResponseTest {

    @Test
    void createsUniformSuccessResponse() {
        ApiResponse<String> response = ApiResponse.success("ready", "request-1");

        assertEquals("OK", response.code());
        assertEquals("ready", response.data());
        assertEquals("request-1", response.requestId());
    }

    @Test
    void calculatesPagesWithoutOverflowProneAddition() {
        PageResponse<String> response = PageResponse.of(List.of("one"), 1, 20, 21);

        assertEquals(2, response.pages());
        assertThrows(UnsupportedOperationException.class, () -> response.items().add("two"));
        assertThrows(IllegalArgumentException.class, () -> PageResponse.of(List.of(), 1, 0, 0));
    }
}
