package com.civicflow.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("sys_user_role")
public class SysUserRoleEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;
    private Long roleId;
    private Instant createdAt;
    private Instant updatedAt;

    @TableLogic(value = "0", delval = "id")
    private Long deleted;
}
