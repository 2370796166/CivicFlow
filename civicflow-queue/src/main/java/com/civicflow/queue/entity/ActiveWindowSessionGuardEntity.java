package com.civicflow.queue.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("active_window_session_guard")
public class ActiveWindowSessionGuardEntity {
    @TableId(value = "window_id", type = IdType.INPUT)
    private Long windowId;

    private Long sessionId;
    private Long staffUserId;
    private Instant createdAt;
    private Instant updatedAt;
}
