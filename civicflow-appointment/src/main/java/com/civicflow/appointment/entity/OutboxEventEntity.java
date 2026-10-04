package com.civicflow.appointment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.civicflow.appointment.enums.OutboxStatus;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.apache.ibatis.type.EnumTypeHandler;

@Getter
@Setter
@TableName(value = "outbox_event", autoResultMap = true)
public class OutboxEventEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String eventId;
    private String aggregateType;
    private String aggregateId;
    private String eventType;
    private Integer eventVersion;
    private String routingKey;
    private String payloadJson;

    @TableField(typeHandler = EnumTypeHandler.class)
    private OutboxStatus status;

    private Integer attempts;
    private Instant nextAttemptAt;
    private Instant publishedAt;
    private String lastErrorCode;
    private Instant createdAt;
    private Instant updatedAt;
}
