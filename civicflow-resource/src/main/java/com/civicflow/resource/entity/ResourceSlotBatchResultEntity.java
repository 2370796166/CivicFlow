package com.civicflow.resource.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("resource_slot_batch_result")
public class ResourceSlotBatchResultEntity {
    @TableId(type = IdType.INPUT)
    private Long id;

    private String resultJson;
    private Instant createdAt;
    private Instant updatedAt;
}
