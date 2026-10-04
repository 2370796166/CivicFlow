package com.civicflow.appointment.client;

import java.util.List;

public record ResourceSlotCandidatePage(
        List<ResourceSlotSnapshot> items, String nextCursor, boolean hasMore) {}
