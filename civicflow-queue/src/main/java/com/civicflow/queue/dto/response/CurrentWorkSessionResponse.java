package com.civicflow.queue.dto.response;

public record CurrentWorkSessionResponse(
        WorkSessionResponse session, QueueTicketResponse currentTicket) {}
