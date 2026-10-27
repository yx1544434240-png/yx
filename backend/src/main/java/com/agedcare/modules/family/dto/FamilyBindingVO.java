package com.agedcare.modules.family.dto;

import com.agedcare.modules.elder.entity.ElderProfile;
import com.agedcare.modules.user.dto.UserSummaryVO;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FamilyBindingVO {
    private Long id;
    private Long familyUserId;
    private Long elderId;
    private String relation;
    private String proofMaterials;
    private String status;
    private Long reviewerId;
    private LocalDateTime reviewTime;
    private String reviewReason;
    private LocalDateTime createdAt;

    // 关联安全脱敏显示
    private ElderProfile elder;
    private UserSummaryVO familyUser;
}
