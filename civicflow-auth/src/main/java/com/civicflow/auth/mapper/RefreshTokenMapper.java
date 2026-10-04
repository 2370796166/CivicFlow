package com.civicflow.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.civicflow.auth.entity.RefreshTokenEntity;
import java.time.Instant;
import org.apache.ibatis.annotations.Param;

public interface RefreshTokenMapper extends BaseMapper<RefreshTokenEntity> {
    RefreshTokenEntity selectByHash(@Param("tokenHash") byte[] tokenHash);

    RefreshTokenEntity selectByHashForUpdate(@Param("tokenHash") byte[] tokenHash);

    int markRotated(
            @Param("id") long id,
            @Param("replacedByHash") byte[] replacedByHash,
            @Param("now") Instant now);

    int revokeFamily(@Param("familyId") String familyId, @Param("now") Instant now);

    int revokeActiveByUser(@Param("userId") long userId, @Param("now") Instant now);
}
