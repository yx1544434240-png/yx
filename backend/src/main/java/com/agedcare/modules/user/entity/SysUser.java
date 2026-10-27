package com.agedcare.modules.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_user")
public class SysUser implements Serializable {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String username;

    @com.fasterxml.jackson.annotation.JsonIgnore
    private String password;

    private String realName;
    private String phone;
    private String avatar;
    private String role; // ADMIN, STAFF, RESIDENT

    @com.fasterxml.jackson.annotation.JsonIgnore
    private String openid;
    private Integer status; // 1-正常 0-禁用
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
