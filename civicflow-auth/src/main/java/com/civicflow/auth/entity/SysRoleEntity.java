package com.civicflow.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.civicflow.auth.enums.RoleCode;
import com.civicflow.auth.enums.RoleStatus;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.apache.ibatis.type.EnumTypeHandler;

@Getter
@Setter
@TableName(value = "sys_role", autoResultMap = true)
public class SysRoleEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @TableField(typeHandler = EnumTypeHandler.class)
    private RoleCode roleCode;

    private String roleName;

    @TableField(typeHandler = EnumTypeHandler.class)
    private RoleStatus status;

    @Version private Integer version;

    private Instant createdAt;
    private Instant updatedAt;
}
