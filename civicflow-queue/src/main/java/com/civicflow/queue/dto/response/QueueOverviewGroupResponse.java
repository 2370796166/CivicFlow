package com.civicflow.queue.dto.response;

public record QueueOverviewGroupResponse(
        String itemId, String windowId, String status, long count) {}
