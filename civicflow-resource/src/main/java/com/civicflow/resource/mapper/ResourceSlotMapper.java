package com.civicflow.resource.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.civicflow.resource.entity.ResourceSlotEntity;
import java.time.LocalDate;
import org.apache.ibatis.annotations.Param;

public interface ResourceSlotMapper extends BaseMapper<ResourceSlotEntity> {
    long countFutureByOutlet(@Param("outletId") long outletId, @Param("today") LocalDate today);

    long countFutureByItem(@Param("itemId") long itemId, @Param("today") LocalDate today);

    long countFutureByWindow(@Param("windowId") long windowId, @Param("today") LocalDate today);
}
