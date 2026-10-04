package com.civicflow.resource.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.civicflow.resource.enums.SlotStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import lombok.Getter;
import lombok.Setter;
import org.apache.ibatis.type.EnumTypeHandler;

@Getter
@Setter
@TableName(value = "resource_slot", autoResultMap = true)
public class ResourceSlotEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long outletId;
    private Long itemId;
    private LocalDate serviceDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private Integer totalQuota;
    private Instant releaseAt;
    private Instant checkInStart;
    private Instant checkInEnd;

    @TableField(typeHandler = EnumTypeHandler.class)
    private SlotStatus status;

    private Long configVersion;
    private Integer consumedHint;

    @Version private Integer version;

    private Long createdBy;
    private Long updatedBy;
    private Instant createdAt;
    private Instant updatedAt;

    @TableLogic(value = "0", delval = "id")
    private Long deleted;
}
