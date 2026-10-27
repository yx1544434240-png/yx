package com.agedcare.modules.dispatch.entity;

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
@TableName("dispatch_score")
public class DispatchScore implements Serializable {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long orderId;
    private Integer dispatchRound;
    private Long staffId;
    private Integer isQualified; // 1-合格 0-不合格
    private String excludeReason;
    private Integer travelMinutes;
    private BigDecimal metricD;
    private BigDecimal metricQ;
    private BigDecimal metricL;
    private BigDecimal metricF;
    private BigDecimal totalScore;
    private String algorithmVersion;
    private String detailsJson;
    private LocalDateTime createdAt;
}
