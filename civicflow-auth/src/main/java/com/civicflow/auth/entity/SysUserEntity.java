package com.civicflow.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.civicflow.auth.enums.UserStatus;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.apache.ibatis.type.EnumTypeHandler;

@Getter
@Setter
@TableName(value = "sys_user", autoResultMap = true)
public class SysUserEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String username;
    private byte[] mobileCipher;
    private byte[] mobileHash;
    private Integer mobileKeyVersion;
    private String passwordHash;
    private String displayName;

    @TableField(typeHandler = EnumTypeHandler.class)
    private UserStatus status;

    private Integer tokenVersion;
    private Instant lastLoginAt;

    @Version private Integer version;

    private Instant createdAt;
    private Instant updatedAt;

    @TableLogic(value = "0", delval = "id")
    private Long deleted;
}
