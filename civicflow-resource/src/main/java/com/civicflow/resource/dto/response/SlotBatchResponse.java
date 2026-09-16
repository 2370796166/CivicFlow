package com.civicflow.resource.dto.response;

import java.util.List;

public record SlotBatchResponse(int created, int skipped, List<SlotBatchFailureResponse> failed) {}
