package com.civicflow.auth.service.impl;

import com.civicflow.auth.config.AuthSecurityProperties;
import com.civicflow.auth.config.MobileProtector;
import com.civicflow.auth.config.SecretHashing;
import com.civicflow.auth.convert.UserConverter;
import com.civicflow.auth.dto.request.LoginRequest;
import com.civicflow.auth.dto.response.CurrentUserResponse;
import com.civicflow.auth.dto.response.TokenPairResponse;
import com.civicflow.auth.entity.AuthSecurityAuditEntity;
import com.civicflow.auth.entity.RefreshTokenEntity;
import com.civicflow.auth.entity.SysUserEntity;
import com.civicflow.auth.enums.AuditOutcome;
import com.civicflow.auth.enums.RefreshTokenStatus;
import com.civicflow.auth.enums.RoleCode;
import com.civicflow.auth.enums.SecurityAuditAction;
import com.civicflow.auth.enums.UserStatus;
import com.civicflow.auth.error.AuthErrorCode;
import com.civicflow.auth.error.RefreshTokenReuseException;
import com.civicflow.auth.mapper.AuthSecurityAuditMapper;
import com.civicflow.auth.mapper.RefreshTokenMapper;
import com.civicflow.auth.mapper.SysRoleMapper;
import com.civicflow.auth.mapper.SysUserMapper;
import com.civicflow.auth.service.AuthenticationService;
import com.civicflow.auth.service.JwtTokenService;
import com.civicflow.common.exception.BusinessException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthenticationServiceImpl implements AuthenticationService {
    private static final String MOBILE_PATTERN = "(?:\\+?86)?1[3-9]\\d{9}";

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final RefreshTokenMapper refreshTokenMapper;
    private final AuthSecurityAuditMapper auditMapper;
    private final PasswordEncoder passwordEncoder;
    private final MobileProtector mobileProtector;
    private final SecretHashing secretHashing;
    private final JwtTokenService jwtTokenService;
    private final AuthSecurityProperties properties;
    private final Clock clock;
    private final SecureRandom secureRandom;
    private final String dummyPasswordHash;

    public AuthenticationServiceImpl(
            SysUserMapper userMapper,
            SysRoleMapper roleMapper,
            RefreshTokenMapper refreshTokenMapper,
            AuthSecurityAuditMapper auditMapper,
            PasswordEncoder passwordEncoder,
            MobileProtector mobileProtector,
            SecretHashing secretHashing,
            JwtTokenService jwtTokenService,
            AuthSecurityProperties properties,
            Clock clock,
            SecureRandom secureRandom) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.refreshTokenMapper = refreshTokenMapper;
        this.auditMapper = auditMapper;
        this.passwordEncoder = passwordEncoder;
        this.mobileProtector = mobileProtector;
        this.secretHashing = secretHashing;
        this.jwtTokenService = jwtTokenService;
        this.properties = properties;
        this.clock = clock;
        this.secureRandom = secureRandom;
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Override
    @Transactional
    public TokenPairResponse login(LoginRequest request) {
        verifyPasswordLength(request.password());
        String rawLoginName = request.loginName().trim();
        byte[] mobileHash = null;
        String username = rawLoginName.toLowerCase();
        if (rawLoginName.matches(MOBILE_PATTERN)) {
            String mobile = mobileProtector.normalize(rawLoginName);
            mobileHash = mobileProtector.hash(mobile);
            username = "";
        }
        SysUserEntity user = userMapper.selectByLoginIdentifier(username, mobileHash);
        String storedHash = user == null ? dummyPasswordHash : user.getPasswordHash();
        boolean passwordMatches = passwordEncoder.matches(request.password(), storedHash);
        if (!passwordMatches || user == null || user.getStatus() != UserStatus.ENABLED) {
            throw new BusinessException(AuthErrorCode.UNAUTHORIZED);
        }
        List<RoleCode> roles = roleMapper.selectEnabledRoleCodesByUserId(user.getId());
        if (roles.isEmpty()) {
            throw new BusinessException(AuthErrorCode.UNAUTHORIZED);
        }
        userMapper.updateLastLogin(user.getId(), clock.instant());
        return issuePair(user, roles, UUID.randomUUID().toString());
    }

    @Override
    @Transactional(noRollbackFor = BusinessException.class)
    public TokenPairResponse refresh(String rawRefreshToken, String requestId) {
        Instant now = clock.instant();
        byte[] tokenHash = secretHashing.sha256(rawRefreshToken);
        RefreshTokenEntity located = refreshTokenMapper.selectByHash(tokenHash);
        if (located == null) {
            throw new BusinessException(AuthErrorCode.UNAUTHORIZED);
        }
        SysUserEntity user = userMapper.selectByIdForUpdate(located.getUserId());
        RefreshTokenEntity current = refreshTokenMapper.selectByHashForUpdate(tokenHash);
        if (current == null || !current.getUserId().equals(located.getUserId())) {
            throw new BusinessException(AuthErrorCode.UNAUTHORIZED);
        }
        if (current.getStatus() == RefreshTokenStatus.ROTATED) {
            refreshTokenMapper.revokeFamily(current.getFamilyId(), now);
            userMapper.incrementTokenVersion(current.getUserId());
            AuthSecurityAuditEntity audit = new AuthSecurityAuditEntity();
            audit.setActorUserId(current.getUserId());
            audit.setTargetUserId(current.getUserId());
            audit.setAction(SecurityAuditAction.REFRESH_TOKEN_REUSE.name());
            audit.setOutcome(AuditOutcome.BLOCKED.name());
            audit.setRequestId(requestId);
            audit.setOccurredAt(now);
            auditMapper.insert(audit);
            throw new RefreshTokenReuseException();
        }
        if (current.getStatus() != RefreshTokenStatus.ACTIVE
                || !current.getExpiresAt().isAfter(now)) {
            throw new BusinessException(AuthErrorCode.UNAUTHORIZED);
        }
        if (user == null || user.getStatus() != UserStatus.ENABLED) {
            refreshTokenMapper.revokeFamily(current.getFamilyId(), now);
            throw new BusinessException(AuthErrorCode.UNAUTHORIZED);
        }
        List<RoleCode> roles = roleMapper.selectEnabledRoleCodesByUserId(user.getId());
        if (roles.isEmpty()) {
            refreshTokenMapper.revokeFamily(current.getFamilyId(), now);
            throw new BusinessException(AuthErrorCode.UNAUTHORIZED);
        }

        String replacement = newRefreshToken();
        byte[] replacementHash = secretHashing.sha256(replacement);
        if (refreshTokenMapper.markRotated(current.getId(), replacementHash, now) != 1) {
            throw new BusinessException(AuthErrorCode.UNAUTHORIZED);
        }
        insertRefreshToken(user.getId(), current.getFamilyId(), replacementHash, now);
        JwtTokenService.AccessToken accessToken = jwtTokenService.issue(user, roles);
        return new TokenPairResponse(
                accessToken.value(),
                accessToken.expiresIn(),
                replacement,
                UserConverter.toCurrent(user, roles));
    }

    @Override
    @Transactional
    public void logout(long authenticatedUserId, String rawRefreshToken) {
        RefreshTokenEntity token =
                refreshTokenMapper.selectByHashForUpdate(secretHashing.sha256(rawRefreshToken));
        if (token != null && token.getUserId() == authenticatedUserId) {
            refreshTokenMapper.revokeFamily(token.getFamilyId(), clock.instant());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public CurrentUserResponse currentUser(long authenticatedUserId, int tokenVersion) {
        SysUserEntity user = userMapper.selectActiveById(authenticatedUserId);
        if (user == null
                || user.getStatus() != UserStatus.ENABLED
                || user.getTokenVersion() != tokenVersion) {
            throw new BusinessException(AuthErrorCode.UNAUTHORIZED);
        }
        List<RoleCode> roles = roleMapper.selectEnabledRoleCodesByUserId(authenticatedUserId);
        return UserConverter.toCurrent(user, roles);
    }

    private TokenPairResponse issuePair(SysUserEntity user, List<RoleCode> roles, String familyId) {
        Instant now = clock.instant();
        String rawRefreshToken = newRefreshToken();
        insertRefreshToken(user.getId(), familyId, secretHashing.sha256(rawRefreshToken), now);
        JwtTokenService.AccessToken accessToken = jwtTokenService.issue(user, roles);
        return new TokenPairResponse(
                accessToken.value(),
                accessToken.expiresIn(),
                rawRefreshToken,
                UserConverter.toCurrent(user, roles));
    }

    private void insertRefreshToken(
            long userId, String familyId, byte[] tokenHash, Instant issuedAt) {
        RefreshTokenEntity entity = new RefreshTokenEntity();
        entity.setTokenHash(tokenHash);
        entity.setUserId(userId);
        entity.setFamilyId(familyId);
        entity.setStatus(RefreshTokenStatus.ACTIVE);
        entity.setIssuedAt(issuedAt);
        entity.setExpiresAt(issuedAt.plus(properties.getJwt().getRefreshTokenTtl()));
        refreshTokenMapper.insert(entity);
    }

    private String newRefreshToken() {
        byte[] value = new byte[32];
        secureRandom.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static void verifyPasswordLength(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BusinessException(com.civicflow.common.api.CommonErrorCode.VALIDATION);
        }
    }
}
