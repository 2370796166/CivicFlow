package com.civicflow.auth.service;

import com.civicflow.auth.dto.request.ChangeUserStatusRequest;
import com.civicflow.auth.dto.request.CreateUserRequest;
import com.civicflow.auth.dto.request.ReplaceUserRolesRequest;
import com.civicflow.auth.dto.response.UserResponse;
import com.civicflow.auth.enums.UserStatus;
import com.civicflow.common.api.PageResponse;

public interface UserAdminService {
    PageResponse<UserResponse> listUsers(
            long actorUserId,
            int actorTokenVersion,
            String keyword,
            UserStatus status,
            int page,
            int size);

    UserResponse createUser(
            long actorUserId,
            int actorTokenVersion,
            String idempotencyKey,
            String requestId,
            CreateUserRequest request);

    UserResponse changeStatus(
            long actorUserId,
            int actorTokenVersion,
            String idempotencyKey,
            String requestId,
            long userId,
            ChangeUserStatusRequest request);

    UserResponse replaceRoles(
            long actorUserId,
            int actorTokenVersion,
            String idempotencyKey,
            String requestId,
            long userId,
            ReplaceUserRolesRequest request);
}
