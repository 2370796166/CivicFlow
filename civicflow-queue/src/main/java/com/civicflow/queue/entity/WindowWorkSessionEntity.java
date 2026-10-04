package com.civicflow.queue.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.civicflow.queue.enums.WindowSessionStatus;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.apache.ibatis.type.EnumTypeHandler;

@Getter
@Setter
@TableName(value = "window_work_session", autoResultMap = true)
public class WindowWorkSessionEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long windowId;
    private Long outletId;
    private Long staffUserId;

    @TableField(typeHandler = EnumTypeHandler.class)
    private WindowSessionStatus status;

    private Instant startedAt;
    private Instant endedAt;

    @Version private Integer version;

    private Instant createdAt;
    private Instant updatedAt;
}
