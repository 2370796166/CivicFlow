package com.civicflow.appointment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.civicflow.appointment.enums.CompensationStatus;
import com.civicflow.appointment.enums.StockReleaseReason;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.apache.ibatis.type.EnumTypeHandler;

@Getter
@Setter
@TableName(value = "stock_release_record", autoResultMap = true)
public class StockReleaseRecordEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String reservationId;
    private Long slotId;
    private Long userId;

    @TableField(typeHandler = EnumTypeHandler.class)
    private StockReleaseReason reason;

    @TableField(typeHandler = EnumTypeHandler.class)
    private CompensationStatus status;

    private Integer attempts;
    private Instant nextAttemptAt;
    private Instant releasedAt;
    private String lastErrorCode;
    private Instant createdAt;
    private Instant updatedAt;
}
