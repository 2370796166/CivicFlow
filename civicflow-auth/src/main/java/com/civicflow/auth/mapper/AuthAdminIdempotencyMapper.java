package com.civicflow.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.civicflow.auth.entity.AuthAdminIdempotencyEntity;
import org.apache.ibatis.annotations.Param;

public interface AuthAdminIdempotencyMapper extends BaseMapper<AuthAdminIdempotencyEntity> {
    int insertIgnore(AuthAdminIdempotencyEntity entity);

    AuthAdminIdempotencyEntity selectForUpdate(
            @Param("actorUserId") long actorUserId,
            @Param("operation") String operation,
            @Param("idempotencyKey") String idempotencyKey);

    int bindResource(@Param("id") long id, @Param("resourceId") long resourceId);
}
