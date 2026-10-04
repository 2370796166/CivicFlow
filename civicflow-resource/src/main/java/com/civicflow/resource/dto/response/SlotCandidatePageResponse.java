package com.civicflow.resource.dto.response;

import java.util.List;

public record SlotCandidatePageResponse(
        List<SlotSnapshotResponse> items, String nextCursor, boolean hasMore) {}
