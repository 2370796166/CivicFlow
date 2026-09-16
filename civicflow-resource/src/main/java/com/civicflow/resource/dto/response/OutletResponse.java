package com.civicflow.resource.dto.response;

import com.civicflow.resource.enums.ResourceStatus;
import java.math.BigDecimal;

public record OutletResponse(
        String id,
        String code,
        String name,
        String address,
        BigDecimal longitude,
        BigDecimal latitude,
        String maskedContactPhone,
        ResourceStatus status,
        int version) {}
