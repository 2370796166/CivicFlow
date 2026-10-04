package com.civicflow.resource.dto.response;

import java.util.List;

public record StaffScopeResponse(
        String outletId,
        String outletCode,
        String outletName,
        String windowId,
        String windowCode,
        String windowName,
        List<ItemSummaryResponse> items) {
    public StaffScopeResponse {
        items = List.copyOf(items);
    }
}
