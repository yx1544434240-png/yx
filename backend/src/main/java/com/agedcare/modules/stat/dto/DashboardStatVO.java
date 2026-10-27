package com.agedcare.modules.stat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStatVO implements Serializable {
    private long totalOrders;
    private long completedOrders;
    private long waitingDispatchOrders;
    private long inServiceOrders;
    private long totalElders;
    private long totalStaff;

    // 统计图表数据
    private List<Map<String, Object>> categoryDistribution;
    private List<Map<String, Object>> recentOrdersTrend;
    private List<Map<String, Object>> staffWorkloadList;
}
