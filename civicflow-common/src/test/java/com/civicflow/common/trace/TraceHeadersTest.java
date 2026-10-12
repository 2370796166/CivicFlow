package com.civicflow.common.trace;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TraceHeadersTest {
    @Test
    void preservesUuidTraceAcrossHopsAndGeneratesNewSpan() {
        String first = TraceHeaders.traceparent("d67d57b7-183e-40b7-92b3-c43c40556389");
        String second = TraceHeaders.traceparent("d67d57b7-183e-40b7-92b3-c43c40556389");
        assertTrue(first.startsWith("00-d67d57b7183e40b792b3c43c40556389-"));
        assertTrue(second.startsWith("00-d67d57b7183e40b792b3c43c40556389-"));
        assertTrue(!first.equals(second));
    }
}
