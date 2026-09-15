package com.civicflow.queue.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.civicflow.queue.enums.QueueTicketStatus;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import org.apache.ibatis.type.EnumTypeHandler;

@Getter
@Setter
@TableName(value = "queue_ticket", autoResultMap = true)
public class QueueTicketEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long appointmentId;
    private Long userId;
    private Long outletId;
    private Long itemId;
    private LocalDate serviceDate;
    private String ticketNo;
    private Integer priority;

    @TableField(typeHandler = EnumTypeHandler.class)
    private QueueTicketStatus status;

    private Instant checkedInAt;
    private Instant calledAt;
    private Instant servingAt;
    private Instant missedAt;
    private Instant completedAt;
    private Long calledWindowId;
    private Long workSessionId;
    private Integer callCount;
    private String resultCode;

    @Version private Integer version;

    private Instant createdAt;
    private Instant updatedAt;
}
