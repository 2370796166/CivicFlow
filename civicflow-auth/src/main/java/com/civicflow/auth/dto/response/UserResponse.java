package com.civicflow.auth.dto.response;

import com.civicflow.auth.enums.RoleCode;
import com.civicflow.auth.enums.UserStatus;
import java.util.List;

public record UserResponse(
        String id,
        String username,
        String maskedMobile,
        String displayName,
        UserStatus status,
        List<RoleCode> roles,
        int version) {
    public UserResponse {
        roles = List.copyOf(roles);
    }
}
