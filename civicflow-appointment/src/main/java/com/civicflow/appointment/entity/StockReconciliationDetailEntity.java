package com.civicflow.appointment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.civicflow.appointment.enums.ReconciliationClassification;
import com.civicflow.appointment.enums.ReconciliationRepairStatus;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.apache.ibatis.type.EnumTypeHandler;

@Getter
@Setter
@TableName(value = "stock_reconciliation_detail", autoResultMap = true)
public class StockReconciliationDetailEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long runId;
    private Long slotId;
    private Long configVersion;
    private Integer configuredTotal;
    private Integer persistedConsumed;
    private Integer pendingReserved;
    private Integer expectedRemaining;
    private Integer actualRemaining;
    private Integer diff;

    @TableField(typeHandler = EnumTypeHandler.class)
    private ReconciliationClassification classification;

    @TableField(typeHandler = EnumTypeHandler.class)
    private ReconciliationRepairStatus repairStatus;

    private String beforeJson;
    private String afterJson;
    private Instant createdAt;
    private Instant updatedAt;
}
