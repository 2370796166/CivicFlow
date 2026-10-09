package com.civicflow.resource.dto.response;

import java.util.List;

public record WindowStaffResponse(
        String windowId,
        int version,
        List<String> staffUserIds,
        List<String> inheritedStaffUserIds) {
    public WindowStaffResponse {
        staffUserIds = List.copyOf(staffUserIds);
        inheritedStaffUserIds = List.copyOf(inheritedStaffUserIds);
    }
}
