package com.civicflow.appointment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.civicflow.appointment.enums.AppointmentActorType;
import com.civicflow.appointment.enums.AppointmentOperation;
import com.civicflow.appointment.enums.AppointmentStatus;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.apache.ibatis.type.EnumTypeHandler;

@Getter
@Setter
@TableName(value = "appointment_operation_log", autoResultMap = true)
public class AppointmentOperationLogEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long appointmentId;
    private String reservationId;

    @TableField(typeHandler = EnumTypeHandler.class)
    private AppointmentActorType actorType;

    private Long actorId;

    @TableField(typeHandler = EnumTypeHandler.class)
    private AppointmentOperation operation;

    @TableField(typeHandler = EnumTypeHandler.class)
    private AppointmentStatus fromStatus;

    @TableField(typeHandler = EnumTypeHandler.class)
    private AppointmentStatus toStatus;

    private String requestId;
    private Instant occurredAt;
    private String detailJson;
    private Instant createdAt;
    private Instant updatedAt;
}
