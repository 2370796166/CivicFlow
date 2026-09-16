package com.civicflow.resource.dto.response;

import java.util.List;

public record WindowItemsResponse(String windowId, int version, List<String> itemIds) {
    public WindowItemsResponse {
        itemIds = List.copyOf(itemIds);
    }
}
