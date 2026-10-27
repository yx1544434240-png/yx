package com.agedcare.modules.staff.entity;

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
@TableName("staff_skill")
public class StaffSkill implements Serializable {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long staffId;
    private String skillCode;
    private String skillName;
    private String certificateNo;
    private String auditStatus; // PENDING, APPROVED, REJECTED
    private String proofUrlsJson;
    private String auditReason;
    private Long auditUserId;
    private LocalDateTime auditTime;
    private LocalDateTime submittedAt;
    private Integer revision;
    @com.baomidou.mybatisplus.annotation.TableField(exist=false)
    private String staffName;
    private LocalDateTime createdAt;
}
