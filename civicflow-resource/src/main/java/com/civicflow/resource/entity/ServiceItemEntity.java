package com.civicflow.resource.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.civicflow.resource.enums.ResourceStatus;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.apache.ibatis.type.EnumTypeHandler;

@Getter
@Setter
@TableName(value = "service_item", autoResultMap = true)
public class ServiceItemEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String code;
    private String name;
    private String description;
    private Integer defaultDurationMinutes;

    @TableField(typeHandler = EnumTypeHandler.class)
    private ResourceStatus status;

    @Version private Integer version;

    private Instant createdAt;
    private Instant updatedAt;

    @TableLogic(value = "0", delval = "id")
    private Long deleted;
}
