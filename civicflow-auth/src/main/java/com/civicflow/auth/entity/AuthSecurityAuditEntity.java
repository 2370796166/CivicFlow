package com.civicflow.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("auth_security_audit")
public class AuthSecurityAuditEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long actorUserId;
    private Long targetUserId;
    private String action;
    private String outcome;
    private String requestId;
    private String beforeJson;
    private String afterJson;
    private Instant occurredAt;
    private Instant createdAt;
    private Instant updatedAt;
}
