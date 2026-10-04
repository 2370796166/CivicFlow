package com.civicflow.appointment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.civicflow.appointment.enums.ReconciliationRunStatus;
import com.civicflow.appointment.enums.ReconciliationTriggerType;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.apache.ibatis.type.EnumTypeHandler;

@Getter
@Setter
@TableName(value = "stock_reconciliation_run", autoResultMap = true)
public class StockReconciliationRunEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @TableField(typeHandler = EnumTypeHandler.class)
    private ReconciliationTriggerType triggerType;

    private String slotScopeJson;

    @TableField(typeHandler = EnumTypeHandler.class)
    private ReconciliationRunStatus status;

    private Instant startedAt;
    private Instant completedAt;
    private Long actorId;
    private byte[] idempotencyKeyHash;
    private byte[] requestHash;
    private String reportJson;
    private Instant createdAt;
    private Instant updatedAt;
}
