package com.civicflow.resource.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("resource_admin_audit")
public class ResourceAdminAuditEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long actorUserId;
    private String resourceType;
    private Long resourceId;
    private String action;
    private String requestId;
    private String beforeJson;
    private String afterJson;
    private Instant occurredAt;
    private Instant createdAt;
    private Instant updatedAt;
}
