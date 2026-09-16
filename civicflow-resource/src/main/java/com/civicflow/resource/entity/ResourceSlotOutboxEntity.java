package com.civicflow.resource.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("resource_slot_outbox")
public class ResourceSlotOutboxEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String eventId;
    private Long slotId;
    private String eventType;
    private Integer eventVersion;
    private String routingKey;
    private String payloadJson;
    private String status;
    private Integer attempts;
    private Instant nextAttemptAt;
    private Instant publishedAt;
    private String lastErrorCode;
    private Instant createdAt;
    private Instant updatedAt;
}
