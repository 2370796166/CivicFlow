package com.civicflow.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.civicflow.auth.entity.SysUserRoleEntity;
import org.apache.ibatis.annotations.Param;

public interface SysUserRoleMapper extends BaseMapper<SysUserRoleEntity> {
    int logicallyDeleteActiveByUserId(@Param("userId") long userId);
}
