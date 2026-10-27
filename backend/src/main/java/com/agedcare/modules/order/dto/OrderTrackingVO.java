package com.agedcare.modules.order.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderTrackingVO {
    private Long orderId;
    private String orderNo;
    private String status;
    private String serviceName;

    // 目的地(老人住址)
    private String destAddress;
    private BigDecimal destLongitude;
    private BigDecimal destLatitude;
    private String elderName;
    private String contactPhone;

    // 配送/服务人员实时位置
    private Long staffId;
    private String staffName;
    private String staffPhone;
    private BigDecimal staffLongitude;
    private BigDecimal staffLatitude;
    private LocalDateTime locationUpdatedAt;

    // 测算距离与预计到达时间
    private Integer distanceMeters;
    private Integer estimatedMinutes;

    // 位置更新状态与新鲜度标识 (防止误解)
    private Boolean isStale;
    private String locationStatusText;
}
