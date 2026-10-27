package com.agedcare.modules.serviceitem.entity;

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
@TableName("service_item")
public class ServiceItem implements Serializable {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String name;
    private String category; // MEAL, CLEANING, ERRAND, ESCORT
    private String description;
    private BigDecimal price;
    private Integer durationMinutes;
    private String requiredSkills; // 逗号分隔技能码
    private String serviceArea;
    private String status; // ON_SHELF, OFF_SHELF
    private String icon;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
