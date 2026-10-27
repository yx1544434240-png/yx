package com.agedcare.modules.staff.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("staff_profile")
public class StaffProfile implements Serializable {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long userId;
    private String serviceArea;
    private String baseStationName;
    private BigDecimal baseLongitude;
    private BigDecimal baseLatitude;
    private BigDecimal currentLongitude;
    private BigDecimal currentLatitude;
    private LocalDateTime locationUpdatedAt;
    private String currentStatus; // RESTING, AVAILABLE, BUSY
    private String auditStatus; // PENDING, APPROVED, REJECTED
    private LocalDateTime auditTime;
    private String auditReason;
    private BigDecimal priorRating; // 平滑均分
    private Integer totalOrders;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
