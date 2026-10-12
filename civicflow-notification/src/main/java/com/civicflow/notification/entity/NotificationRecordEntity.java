package com.civicflow.notification.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("notification_record")
public class NotificationRecordEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String eventId;
    private String channel;
    private String eventType;
    private String templateCode;
    private Integer templateVersion;
    private Long userId;
    private String aggregateId;
    private Boolean requiredNotice;
    private String payloadHash;
    private String eventJson;
    private String title;
    private String body;
    private String status;
    private Integer attempts;
    private String lastErrorCode;
    private Instant readAt;
    private Instant sentAt;
    private Instant createdAt;
    private Instant updatedAt;
}
