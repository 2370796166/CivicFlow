package com.civicflow.appointment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("stock_admin_audit")
public class StockAdminAuditEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long actorUserId;
    private Long slotId;
    private String action;
    private String outcome;
    private String requestId;
    private String detailJson;
    private Instant occurredAt;
    private Instant createdAt;
    private Instant updatedAt;
}
