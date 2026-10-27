package com.agedcare.modules.elder.entity;

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
@TableName("elder_profile")
public class ElderProfile implements Serializable {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long userId; // 老人本人登录账号ID(可为空)
    private String name;
    private String gender; // MALE, FEMALE, UNKNOWN
    private String idCard;
    private Integer age;
    private String phone;
    private String address;
    private java.math.BigDecimal longitude;
    private java.math.BigDecimal latitude;
    private String community;
    private String emergencyContact;
    private String emergencyPhone;
    private String healthNote;
    private String careLevel; // NORMAL, MEDIUM, HIGH
    private Integer status; // 1-正常 0-注销
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private Boolean isSelf;

    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private String relationDesc; // "本人", "家属代约"
}
