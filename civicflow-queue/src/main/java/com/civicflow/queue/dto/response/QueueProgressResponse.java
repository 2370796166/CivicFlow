package com.civicflow.queue.dto.response;

public record QueueProgressResponse(
        QueueTicketResponse ticket, long aheadCount, String currentCall, String estimate) {}
