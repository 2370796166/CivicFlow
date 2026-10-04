package com.civicflow.resource.dto.response;

import com.civicflow.resource.enums.ResourceStatus;

public record WindowResponse(
        String id, String outletId, String code, String name, ResourceStatus status, int version) {}
