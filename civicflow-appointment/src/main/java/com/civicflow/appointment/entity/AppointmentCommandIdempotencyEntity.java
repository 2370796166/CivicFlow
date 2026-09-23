package com.civicflow.appointment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("appointment_command_idempotency")
public class AppointmentCommandIdempotencyEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long actorUserId;
    private Long appointmentId;
    private String operation;
    private byte[] idempotencyKeyHash;
    private byte[] payloadHash;
    private Instant createdAt;
}
