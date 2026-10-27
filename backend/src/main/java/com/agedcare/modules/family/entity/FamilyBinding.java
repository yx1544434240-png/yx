package com.agedcare.modules.family.entity;

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
@TableName("family_binding")
public class FamilyBinding implements Serializable {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long familyUserId;
    private Long elderId;
    private String relation;
    private String proofMaterials;
    private String status; // PENDING, APPROVED, REJECTED, REVOKED
    private Long reviewerId;
    private LocalDateTime reviewTime;
    private String reviewReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
