package com.civicflow.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.civicflow.auth.entity.SysUserEntity;
import com.civicflow.auth.enums.UserStatus;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface SysUserMapper extends BaseMapper<SysUserEntity> {
    SysUserEntity selectByLoginIdentifier(
            @Param("username") String username, @Param("mobileHash") byte[] mobileHash);

    SysUserEntity selectActiveById(@Param("id") long id);

    SysUserEntity selectByIdForUpdate(@Param("id") long id);

    int updateLastLogin(@Param("id") long id, @Param("lastLoginAt") Instant lastLoginAt);

    int updateStatusAndInvalidate(
            @Param("id") long id,
            @Param("status") UserStatus status,
            @Param("expectedVersion") int expectedVersion);

    int incrementTokenVersion(@Param("id") long id);

    int incrementTokenVersionIfVersion(
            @Param("id") long id, @Param("expectedVersion") int expectedVersion);

    List<SysUserEntity> selectAdminPage(
            @Param("keyword") String keyword,
            @Param("mobileHash") byte[] mobileHash,
            @Param("status") UserStatus status,
            @Param("offset") long offset,
            @Param("size") int size);

    long countAdminPage(
            @Param("keyword") String keyword,
            @Param("mobileHash") byte[] mobileHash,
            @Param("status") UserStatus status);
}
