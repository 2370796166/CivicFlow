package com.civicflow.resource.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.civicflow.resource.entity.WindowItemRelEntity;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface WindowItemRelMapper extends BaseMapper<WindowItemRelEntity> {
    List<Long> selectActiveItemIds(@Param("windowId") long windowId);

    int logicallyDeleteByWindow(@Param("windowId") long windowId);

    long countByWindow(@Param("windowId") long windowId);

    long countByItem(@Param("itemId") long itemId);
}
