package com.civicflow.resource.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.civicflow.resource.entity.ServiceItemEntity;
import com.civicflow.resource.enums.ResourceStatus;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface ServiceItemMapper extends BaseMapper<ServiceItemEntity> {
    ServiceItemEntity selectActiveById(@Param("id") long id);

    ServiceItemEntity selectByIdForUpdate(@Param("id") long id);

    List<ServiceItemEntity> selectAdminPage(
            @Param("keyword") String keyword,
            @Param("status") ResourceStatus status,
            @Param("offset") long offset,
            @Param("size") int size);

    long countAdminPage(@Param("keyword") String keyword, @Param("status") ResourceStatus status);

    List<ServiceItemEntity> selectEnabledByOutletPage(
            @Param("outletId") long outletId,
            @Param("offset") long offset,
            @Param("size") int size);

    long countEnabledByOutlet(@Param("outletId") long outletId);

    List<ServiceItemEntity> selectEnabledByIds(@Param("ids") Collection<Long> ids);

    int updateDetails(
            @Param("id") long id,
            @Param("code") String code,
            @Param("name") String name,
            @Param("description") String description,
            @Param("defaultDurationMinutes") int defaultDurationMinutes,
            @Param("expectedVersion") int expectedVersion);

    int updateStatus(
            @Param("id") long id,
            @Param("status") ResourceStatus status,
            @Param("expectedVersion") int expectedVersion);

    int logicallyDelete(@Param("id") long id, @Param("expectedVersion") int expectedVersion);
}
