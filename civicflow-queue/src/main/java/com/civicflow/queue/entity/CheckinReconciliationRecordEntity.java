package com.civicflow.queue.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.civicflow.queue.enums.CheckinReconciliationStatus;
import com.civicflow.queue.enums.QueueAppointmentStatus;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.apache.ibatis.type.EnumTypeHandler;

@Getter
@Setter
@TableName(value = "checkin_reconciliation_record", autoResultMap = true)
public class CheckinReconciliationRecordEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long appointmentId;
    private Long ticketId;
    private String claimId;

    @TableField(typeHandler = EnumTypeHandler.class)
    private QueueAppointmentStatus appointmentStatusSnapshot;

    @TableField(typeHandler = EnumTypeHandler.class)
    private CheckinReconciliationStatus status;

    private Integer attempts;
    private Instant nextAttemptAt;
    private String lastErrorCode;
    private Instant createdAt;
    private Instant updatedAt;
}
