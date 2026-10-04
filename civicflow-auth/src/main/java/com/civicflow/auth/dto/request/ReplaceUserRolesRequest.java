package com.civicflow.auth.dto.request;

import com.civicflow.auth.enums.RoleCode;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.Set;

public record ReplaceUserRolesRequest(
        @NotEmpty Set<@NotNull RoleCode> roles, @NotNull @PositiveOrZero Integer version) {}
