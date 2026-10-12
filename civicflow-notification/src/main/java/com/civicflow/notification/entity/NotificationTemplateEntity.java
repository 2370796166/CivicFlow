package com.civicflow.notification.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("notification_template")
public class NotificationTemplateEntity {
    @TableId private Long id;
    private String templateCode;
    private Integer templateVersion;
    private String titlePattern;
    private String bodyPattern;
    private String allowedVariables;
    private Instant createdAt;
}
