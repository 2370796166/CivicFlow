package com.civicflow.auth.convert;

import com.civicflow.auth.dto.response.CurrentUserResponse;
import com.civicflow.auth.dto.response.UserResponse;
import com.civicflow.auth.entity.SysUserEntity;
import com.civicflow.auth.enums.RoleCode;
import java.util.List;

public final class UserConverter {
    private UserConverter() {}

    public static CurrentUserResponse toCurrent(SysUserEntity user, List<RoleCode> roles) {
        return new CurrentUserResponse(String.valueOf(user.getId()), user.getDisplayName(), roles);
    }

    public static UserResponse toAdmin(
            SysUserEntity user, String maskedMobile, List<RoleCode> roles) {
        return new UserResponse(
                String.valueOf(user.getId()),
                user.getUsername(),
                maskedMobile,
                user.getDisplayName(),
                user.getStatus(),
                roles,
                user.getVersion());
    }
}
