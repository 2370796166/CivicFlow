package com.civicflow.notification.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("notification_preference")
public class NotificationPreferenceEntity {
    private Long userId;
    private String channel;
    private Boolean optionalEnabled;
    private Instant updatedAt;
}
