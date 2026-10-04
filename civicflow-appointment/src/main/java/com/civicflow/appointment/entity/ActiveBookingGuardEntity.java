package com.civicflow.appointment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("active_booking_guard")
public class ActiveBookingGuardEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;
    private Long itemId;
    private LocalDate serviceDate;
    private String reservationId;
    private Long appointmentId;
    private Instant createdAt;
    private Instant updatedAt;
}
