package com.civicflow.resource.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("resource_admin_idempotency")
public class ResourceAdminIdempotencyEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long actorUserId;
    private String operation;
    private String idempotencyKey;
    private byte[] payloadHash;
    private Long resourceId;
    private Instant createdAt;
    private Instant updatedAt;
}
