package com.civicflow.resource.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.civicflow.resource.dto.response.StaffScopeRow;
import com.civicflow.resource.entity.StaffWindowScopeEntity;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface StaffWindowScopeMapper extends BaseMapper<StaffWindowScopeEntity> {
    List<StaffScopeRow> selectAuthorizedRows(@Param("staffUserId") long staffUserId);

    long countByOutlet(@Param("outletId") long outletId);

    long countByWindow(@Param("windowId") long windowId);
}
