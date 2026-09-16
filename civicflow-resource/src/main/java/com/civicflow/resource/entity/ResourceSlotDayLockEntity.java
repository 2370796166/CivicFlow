package com.civicflow.resource.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("resource_slot_day_lock")
public class ResourceSlotDayLockEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long outletId;
    private Long itemId;
    private LocalDate serviceDate;
    private Instant createdAt;
    private Instant updatedAt;
}
