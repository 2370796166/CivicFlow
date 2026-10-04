package com.civicflow.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.civicflow.auth.enums.RefreshTokenStatus;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.apache.ibatis.type.EnumTypeHandler;

@Getter
@Setter
@TableName(value = "refresh_token", autoResultMap = true)
public class RefreshTokenEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private byte[] tokenHash;
    private Long userId;
    private String familyId;

    @TableField(typeHandler = EnumTypeHandler.class)
    private RefreshTokenStatus status;

    private Instant issuedAt;
    private Instant expiresAt;
    private Instant revokedAt;
    private byte[] replacedByHash;
    private byte[] clientFingerprintHash;
    private Instant createdAt;
    private Instant updatedAt;
}
