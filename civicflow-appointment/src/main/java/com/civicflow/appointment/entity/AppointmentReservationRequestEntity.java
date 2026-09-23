package com.civicflow.appointment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.civicflow.appointment.enums.ReservationRequestStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import lombok.Getter;
import lombok.Setter;
import org.apache.ibatis.type.EnumTypeHandler;

@Getter
@Setter
@TableName(value = "appointment_reservation_request", autoResultMap = true)
public class AppointmentReservationRequestEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String reservationId;
    private Long userId;
    private byte[] idempotencyKeyHash;
    private byte[] payloadHash;
    private Long slotId;
    private Long outletId;
    private Long itemId;
    private LocalDate serviceDate;
    private LocalTime slotStartTime;
    private LocalTime slotEndTime;
    private String outletNameSnapshot;
    private String itemNameSnapshot;
    private Integer totalQuota;
    private Instant releaseAt;
    private Instant closeAt;
    private String slotStatus;
    private Long slotConfigVersion;

    @TableField(typeHandler = EnumTypeHandler.class)
    private ReservationRequestStatus status;

    private String failureCode;
    private String eventJson;
    private String traceId;
    private Instant reservedAt;
    private Instant reservationExpiresAt;
    private Integer publishAttempts;
    private String lastErrorCode;
    private Instant nextRecoveryAt;
    private String recoveryOwner;
    private Instant recoveryLeaseUntil;
    private Instant createdAt;
    private Instant updatedAt;
}
