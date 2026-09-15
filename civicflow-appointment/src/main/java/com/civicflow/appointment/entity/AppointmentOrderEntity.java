package com.civicflow.appointment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.civicflow.appointment.enums.AppointmentStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import lombok.Getter;
import lombok.Setter;
import org.apache.ibatis.type.EnumTypeHandler;

@Getter
@Setter
@TableName(value = "appointment_order", autoResultMap = true)
public class AppointmentOrderEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String reservationId;
    private Long userId;
    private Long slotId;
    private Long outletId;
    private Long itemId;
    private LocalDate serviceDate;
    private LocalTime slotStartTime;
    private LocalTime slotEndTime;
    private String outletNameSnapshot;
    private String itemNameSnapshot;

    @TableField(typeHandler = EnumTypeHandler.class)
    private AppointmentStatus status;

    private Instant confirmDeadline;
    private Instant confirmedAt;
    private Instant cancelledAt;
    private Instant expiredAt;
    private Instant checkedInAt;
    private Instant servingAt;
    private Instant completedAt;
    private Instant noShowAt;
    private String cancelReason;
    private byte[] qrNonceHash;
    private Instant qrExpiresAt;
    private String qrKeyId;
    private String checkinClaimId;
    private Long queueTicketId;

    @Version private Integer version;

    private Instant createdAt;
    private Instant updatedAt;
}
