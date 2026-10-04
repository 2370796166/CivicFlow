package com.civicflow.resource.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.civicflow.resource.entity.ResourceAdminIdempotencyEntity;
import org.apache.ibatis.annotations.Param;

public interface ResourceAdminIdempotencyMapper extends BaseMapper<ResourceAdminIdempotencyEntity> {
    int insertIgnore(ResourceAdminIdempotencyEntity entity);

    ResourceAdminIdempotencyEntity selectForUpdate(
            @Param("actorUserId") long actorUserId,
            @Param("operation") String operation,
            @Param("idempotencyKey") String idempotencyKey);

    int bindResource(@Param("id") long id, @Param("resourceId") long resourceId);
}
