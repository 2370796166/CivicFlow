package com.civicflow.auth.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.civicflow.auth.config.MobileProtector;
import com.civicflow.auth.config.SecretHashing;
import com.civicflow.auth.convert.UserConverter;
import com.civicflow.auth.dto.request.ChangeUserStatusRequest;
import com.civicflow.auth.dto.request.CreateUserRequest;
import com.civicflow.auth.dto.request.ReplaceUserRolesRequest;
import com.civicflow.auth.dto.response.UserResponse;
import com.civicflow.auth.entity.AuthAdminIdempotencyEntity;
import com.civicflow.auth.entity.AuthSecurityAuditEntity;
import com.civicflow.auth.entity.SysRoleEntity;
import com.civicflow.auth.entity.SysUserEntity;
import com.civicflow.auth.entity.SysUserRoleEntity;
import com.civicflow.auth.enums.AdminOperation;
import com.civicflow.auth.enums.AuditOutcome;
import com.civicflow.auth.enums.RoleCode;
import com.civicflow.auth.enums.UserStatus;
import com.civicflow.auth.error.AuthErrorCode;
import com.civicflow.auth.mapper.AuthAdminIdempotencyMapper;
import com.civicflow.auth.mapper.AuthSecurityAuditMapper;
import com.civicflow.auth.mapper.RefreshTokenMapper;
import com.civicflow.auth.mapper.SysRoleMapper;
import com.civicflow.auth.mapper.SysUserMapper;
import com.civicflow.auth.mapper.SysUserRoleMapper;
import com.civicflow.auth.service.UserAdminService;
import com.civicflow.common.api.CommonErrorCode;
import com.civicflow.common.api.PageResponse;
import com.civicflow.common.exception.BusinessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class UserAdminServiceImpl implements UserAdminService {
    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final RefreshTokenMapper refreshTokenMapper;
    private final AuthAdminIdempotencyMapper idempotencyMapper;
    private final AuthSecurityAuditMapper auditMapper;
    private final PasswordEncoder passwordEncoder;
    private final MobileProtector mobileProtector;
    private final SecretHashing secretHashing;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    public UserAdminServiceImpl(
            SysUserMapper userMapper,
            SysRoleMapper roleMapper,
            SysUserRoleMapper userRoleMapper,
            RefreshTokenMapper refreshTokenMapper,
            AuthAdminIdempotencyMapper idempotencyMapper,
            AuthSecurityAuditMapper auditMapper,
            PasswordEncoder passwordEncoder,
            MobileProtector mobileProtector,
            SecretHashing secretHashing,
            Clock clock,
            ObjectMapper objectMapper) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.refreshTokenMapper = refreshTokenMapper;
        this.idempotencyMapper = idempotencyMapper;
        this.auditMapper = auditMapper;
        this.passwordEncoder = passwordEncoder;
        this.mobileProtector = mobileProtector;
        this.secretHashing = secretHashing;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<UserResponse> listUsers(
            long actorUserId,
            int actorTokenVersion,
            String keyword,
            UserStatus status,
            int page,
            int size) {
        assertActiveAdmin(actorUserId, actorTokenVersion);
        String normalizedKeyword = StringUtils.hasText(keyword) ? keyword.trim() : null;
        byte[] mobileHash = null;
        if (normalizedKeyword != null && normalizedKeyword.matches("(?:\\+?86)?1[3-9]\\d{9}")) {
            mobileHash = mobileProtector.hash(mobileProtector.normalize(normalizedKeyword));
        }
        long total = userMapper.countAdminPage(normalizedKeyword, mobileHash, status);
        List<UserResponse> records =
                userMapper
                        .selectAdminPage(
                                normalizedKeyword,
                                mobileHash,
                                status,
                                (long) (page - 1) * size,
                                size)
                        .stream()
                        .map(this::view)
                        .toList();
        return PageResponse.of(records, page, size, total);
    }

    @Override
    @Transactional
    public UserResponse createUser(
            long actorUserId,
            int actorTokenVersion,
            String idempotencyKey,
            String requestId,
            CreateUserRequest request) {
        assertActiveAdmin(actorUserId, actorTokenVersion);
        verifyPasswordLength(request.password());
        String username = request.username().trim().toLowerCase();
        String mobile =
                StringUtils.hasText(request.mobile())
                        ? mobileProtector.normalize(request.mobile())
                        : null;
        List<SysRoleEntity> roles = resolveRoles(request.roles());
        String canonical =
                username
                        + "|"
                        + (mobile == null ? "" : mobile)
                        + "|"
                        + request.displayName().trim()
                        + "|"
                        + sortedRoles(request.roles())
                        + "|"
                        + toHex(
                                mobileProtector.contextualHmac(
                                        "IDEMPOTENCY_PASSWORD", request.password()));
        AuthAdminIdempotencyEntity operation =
                reserve(actorUserId, AdminOperation.CREATE_USER, idempotencyKey, canonical);
        if (operation.getResourceId() != null) {
            return view(operation.getResourceId());
        }

        SysUserEntity user = new SysUserEntity();
        user.setUsername(username);
        if (mobile != null) {
            user.setMobileCipher(mobileProtector.encrypt(mobile));
            user.setMobileHash(mobileProtector.hash(mobile));
            user.setMobileKeyVersion(mobileProtector.keyVersion());
        }
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setDisplayName(request.displayName().trim());
        user.setStatus(UserStatus.ENABLED);
        user.setTokenVersion(0);
        user.setVersion(0);
        try {
            userMapper.insert(user);
            replaceRoleRows(user.getId(), roles);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(AuthErrorCode.USER_EXISTS, exception);
        }
        idempotencyMapper.bindResource(operation.getId(), user.getId());
        audit(
                actorUserId,
                user.getId(),
                AdminOperation.CREATE_USER,
                requestId,
                null,
                Map.of("status", UserStatus.ENABLED.name(), "roles", sortedRoles(request.roles())));
        return view(user.getId());
    }

    @Override
    @Transactional
    public UserResponse changeStatus(
            long actorUserId,
            int actorTokenVersion,
            String idempotencyKey,
            String requestId,
            long userId,
            ChangeUserStatusRequest request) {
        assertActiveAdmin(actorUserId, actorTokenVersion);
        if (request.status() == UserStatus.LOCKED) {
            throw new BusinessException(CommonErrorCode.VALIDATION);
        }
        if (actorUserId == userId && request.status() != UserStatus.ENABLED) {
            throw new BusinessException(AuthErrorCode.FORBIDDEN);
        }
        String canonical = userId + "|" + request.status() + "|" + request.version();
        AuthAdminIdempotencyEntity operation =
                reserve(actorUserId, AdminOperation.CHANGE_USER_STATUS, idempotencyKey, canonical);
        if (operation.getResourceId() != null) {
            return view(operation.getResourceId());
        }
        SysUserEntity user = requireUserForUpdate(userId);
        if (userMapper.updateStatusAndInvalidate(userId, request.status(), request.version())
                != 1) {
            throw new BusinessException(AuthErrorCode.VERSION_CONFLICT);
        }
        refreshTokenMapper.revokeActiveByUser(userId, clock.instant());
        idempotencyMapper.bindResource(operation.getId(), userId);
        audit(
                actorUserId,
                userId,
                AdminOperation.CHANGE_USER_STATUS,
                requestId,
                Map.of("status", user.getStatus().name(), "version", user.getVersion()),
                Map.of("status", request.status().name(), "version", request.version() + 1));
        return view(userId);
    }

    @Override
    @Transactional
    public UserResponse replaceRoles(
            long actorUserId,
            int actorTokenVersion,
            String idempotencyKey,
            String requestId,
            long userId,
            ReplaceUserRolesRequest request) {
        assertActiveAdmin(actorUserId, actorTokenVersion);
        if (actorUserId == userId && !request.roles().contains(RoleCode.ADMIN)) {
            throw new BusinessException(AuthErrorCode.FORBIDDEN);
        }
        List<SysRoleEntity> roles = resolveRoles(request.roles());
        String canonical = userId + "|" + sortedRoles(request.roles()) + "|" + request.version();
        AuthAdminIdempotencyEntity operation =
                reserve(actorUserId, AdminOperation.REPLACE_USER_ROLES, idempotencyKey, canonical);
        if (operation.getResourceId() != null) {
            return view(operation.getResourceId());
        }
        SysUserEntity user = requireUserForUpdate(userId);
        List<RoleCode> beforeRoles = roleMapper.selectEnabledRoleCodesByUserId(userId);
        if (userMapper.incrementTokenVersionIfVersion(userId, request.version()) != 1) {
            throw new BusinessException(AuthErrorCode.VERSION_CONFLICT);
        }
        userRoleMapper.logicallyDeleteActiveByUserId(userId);
        replaceRoleRows(userId, roles);
        refreshTokenMapper.revokeActiveByUser(userId, clock.instant());
        idempotencyMapper.bindResource(operation.getId(), userId);
        audit(
                actorUserId,
                userId,
                AdminOperation.REPLACE_USER_ROLES,
                requestId,
                Map.of("roles", sortedRoles(Set.copyOf(beforeRoles)), "version", user.getVersion()),
                Map.of("roles", sortedRoles(request.roles()), "version", request.version() + 1));
        return view(userId);
    }

    private void assertActiveAdmin(long actorUserId, int tokenVersion) {
        SysUserEntity actor = userMapper.selectActiveById(actorUserId);
        if (actor == null
                || actor.getStatus() != UserStatus.ENABLED
                || actor.getTokenVersion() != tokenVersion
                || !roleMapper
                        .selectEnabledRoleCodesByUserId(actorUserId)
                        .contains(RoleCode.ADMIN)) {
            throw new BusinessException(AuthErrorCode.FORBIDDEN);
        }
    }

    private AuthAdminIdempotencyEntity reserve(
            long actorUserId,
            AdminOperation operation,
            String idempotencyKey,
            String canonicalPayload) {
        if (!StringUtils.hasText(idempotencyKey)) {
            throw new BusinessException(CommonErrorCode.IDEMPOTENCY_REQUIRED);
        }
        byte[] payloadHash = secretHashing.sha256(canonicalPayload);
        Instant now = clock.instant();
        AuthAdminIdempotencyEntity candidate = new AuthAdminIdempotencyEntity();
        candidate.setId(IdWorker.getId());
        candidate.setActorUserId(actorUserId);
        candidate.setOperation(operation.name());
        candidate.setIdempotencyKey(idempotencyKey);
        candidate.setPayloadHash(payloadHash);
        candidate.setCreatedAt(now);
        candidate.setUpdatedAt(now);
        idempotencyMapper.insertIgnore(candidate);
        AuthAdminIdempotencyEntity stored =
                idempotencyMapper.selectForUpdate(actorUserId, operation.name(), idempotencyKey);
        if (stored == null || !MessageDigest.isEqual(stored.getPayloadHash(), payloadHash)) {
            throw new BusinessException(CommonErrorCode.IDEMPOTENCY_CONFLICT);
        }
        return stored;
    }

    private List<SysRoleEntity> resolveRoles(Set<RoleCode> requested) {
        List<SysRoleEntity> roles = roleMapper.selectEnabledByCodes(requested);
        if (roles.size() != requested.size()) {
            throw new BusinessException(CommonErrorCode.VALIDATION);
        }
        return roles;
    }

    private void replaceRoleRows(long userId, List<SysRoleEntity> roles) {
        for (SysRoleEntity role : roles) {
            SysUserRoleEntity relation = new SysUserRoleEntity();
            relation.setUserId(userId);
            relation.setRoleId(role.getId());
            userRoleMapper.insert(relation);
        }
    }

    private SysUserEntity requireUserForUpdate(long userId) {
        SysUserEntity user = userMapper.selectByIdForUpdate(userId);
        if (user == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        }
        return user;
    }

    private UserResponse view(long userId) {
        SysUserEntity user = userMapper.selectActiveById(userId);
        if (user == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        }
        return view(user);
    }

    private UserResponse view(SysUserEntity user) {
        String maskedMobile = null;
        if (user.getMobileCipher() != null) {
            maskedMobile =
                    mobileProtector.mask(
                            mobileProtector.decrypt(
                                    user.getMobileCipher(), user.getMobileKeyVersion()));
        }
        return UserConverter.toAdmin(
                user, maskedMobile, roleMapper.selectEnabledRoleCodesByUserId(user.getId()));
    }

    private static String sortedRoles(Set<RoleCode> roles) {
        return roles.stream()
                .sorted(Comparator.comparing(Enum::name))
                .map(Enum::name)
                .collect(Collectors.joining(","));
    }

    private static void verifyPasswordLength(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BusinessException(CommonErrorCode.VALIDATION);
        }
    }

    private static String toHex(byte[] value) {
        return java.util.HexFormat.of().formatHex(value);
    }

    private void audit(
            long actorUserId,
            long targetUserId,
            AdminOperation action,
            String requestId,
            Map<String, Object> before,
            Map<String, Object> after) {
        AuthSecurityAuditEntity audit = new AuthSecurityAuditEntity();
        audit.setActorUserId(actorUserId);
        audit.setTargetUserId(targetUserId);
        audit.setAction(action.name());
        audit.setOutcome(AuditOutcome.SUCCEEDED.name());
        audit.setRequestId(requestId);
        audit.setBeforeJson(toJson(before));
        audit.setAfterJson(toJson(after));
        audit.setOccurredAt(clock.instant());
        auditMapper.insert(audit);
    }

    private String toJson(Map<String, Object> value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize security audit", exception);
        }
    }
}
