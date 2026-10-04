package com.civicflow.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.civicflow.auth.entity.SysRoleEntity;
import com.civicflow.auth.enums.RoleCode;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface SysRoleMapper extends BaseMapper<SysRoleEntity> {
    List<RoleCode> selectEnabledRoleCodesByUserId(@Param("userId") long userId);

    List<SysRoleEntity> selectEnabledByCodes(@Param("roleCodes") Collection<RoleCode> roleCodes);
}
