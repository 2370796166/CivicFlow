package com.civicflow.resource.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.civicflow.resource.entity.ResourceSlotDayLockEntity;
import java.time.LocalDate;
import org.apache.ibatis.annotations.Param;

public interface ResourceSlotDayLockMapper extends BaseMapper<ResourceSlotDayLockEntity> {
    int insertIgnore(ResourceSlotDayLockEntity entity);

    ResourceSlotDayLockEntity selectForUpdate(
            @Param("outletId") long outletId,
            @Param("itemId") long itemId,
            @Param("serviceDate") LocalDate serviceDate);
}
