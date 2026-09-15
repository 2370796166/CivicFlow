package com.civicflow.queue.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.civicflow.queue.enums.MessageConsumeStatus;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.apache.ibatis.type.EnumTypeHandler;

@Getter
@Setter
@TableName(value = "message_consume_record", autoResultMap = true)
public class MessageConsumeRecordEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String consumerName;
    private String idempotencyKey;
    private String eventId;
    private byte[] payloadHash;

    @TableField(typeHandler = EnumTypeHandler.class)
    private MessageConsumeStatus status;

    private Instant firstSeenAt;
    private Instant processedAt;
    private Instant createdAt;
    private Instant updatedAt;
}
