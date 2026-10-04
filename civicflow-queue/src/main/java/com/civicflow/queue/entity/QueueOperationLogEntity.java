package com.civicflow.queue.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.civicflow.queue.enums.QueueActorType;
import com.civicflow.queue.enums.QueueOperation;
import com.civicflow.queue.enums.QueueTicketStatus;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.apache.ibatis.type.EnumTypeHandler;

@Getter
@Setter
@TableName(value = "queue_operation_log", autoResultMap = true)
public class QueueOperationLogEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long ticketId;
    private Long appointmentId;

    @TableField(typeHandler = EnumTypeHandler.class)
    private QueueActorType actorType;

    private Long actorId;

    @TableField(typeHandler = EnumTypeHandler.class)
    private QueueOperation operation;

    @TableField(typeHandler = EnumTypeHandler.class)
    private QueueTicketStatus fromStatus;

    @TableField(typeHandler = EnumTypeHandler.class)
    private QueueTicketStatus toStatus;

    private String requestId;
    private Instant occurredAt;
    private String detailJson;
    private Instant createdAt;
    private Instant updatedAt;
}
