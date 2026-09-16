package com.civicflow.auth.dto.response;

import com.civicflow.auth.enums.RoleCode;
import java.util.List;

public record CurrentUserResponse(String id, String displayName, List<RoleCode> roles) {
    public CurrentUserResponse {
        roles = List.copyOf(roles);
    }
}
