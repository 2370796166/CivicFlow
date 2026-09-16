package com.civicflow.resource.dto.response;

import com.civicflow.resource.enums.ResourceStatus;

public record ItemResponse(
        String id,
        String code,
        String name,
        String description,
        int defaultDurationMinutes,
        ResourceStatus status,
        int version) {}
