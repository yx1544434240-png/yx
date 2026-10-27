package com.agedcare.modules.staff.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.*;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@TableName("staff_application")
public class StaffApplication {
    @TableId(type=IdType.ASSIGN_ID) private Long id;
    private Long userId;
    private String realName;
    private String phone;
    private String serviceArea;
    private String introduction;
    private String status;
    private Integer revision;
    private String auditReason;
    private Long auditUserId;
    private LocalDateTime auditTime;
    private LocalDateTime submittedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
