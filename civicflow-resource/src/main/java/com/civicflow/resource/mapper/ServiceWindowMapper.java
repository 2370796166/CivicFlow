package com.civicflow.resource.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.civicflow.resource.entity.ServiceWindowEntity;
import com.civicflow.resource.enums.ResourceStatus;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface ServiceWindowMapper extends BaseMapper<ServiceWindowEntity> {
    ServiceWindowEntity selectActiveById(@Param("id") long id);

    ServiceWindowEntity selectByIdForUpdate(@Param("id") long id);

    List<ServiceWindowEntity> selectAdminPage(
            @Param("outletId") Long outletId,
            @Param("keyword") String keyword,
            @Param("status") ResourceStatus status,
            @Param("offset") long offset,
            @Param("size") int size);

    long countAdminPage(
            @Param("outletId") Long outletId,
            @Param("keyword") String keyword,
            @Param("status") ResourceStatus status);

    long countActiveByOutlet(@Param("outletId") long outletId);

    int updateDetails(
            @Param("id") long id,
            @Param("outletId") long outletId,
            @Param("code") String code,
            @Param("name") String name,
            @Param("expectedVersion") int expectedVersion);

    int updateStatus(
            @Param("id") long id,
            @Param("status") ResourceStatus status,
            @Param("expectedVersion") int expectedVersion);

    int incrementVersion(@Param("id") long id, @Param("expectedVersion") int expectedVersion);

    int logicallyDelete(@Param("id") long id, @Param("expectedVersion") int expectedVersion);
}
