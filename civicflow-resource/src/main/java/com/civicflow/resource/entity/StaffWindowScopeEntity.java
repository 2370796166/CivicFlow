package com.civicflow.resource.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("staff_window_scope")
public class StaffWindowScopeEntity {
    public static final long ALL_WINDOWS = 0L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long staffUserId;
    private Long outletId;
    private Long windowId;
    private Instant createdAt;
    private Instant updatedAt;

    @TableLogic(value = "0", delval = "id")
    private Long deleted;
}
