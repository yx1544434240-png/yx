package com.agedcare.modules.stat.service;

import com.agedcare.common.enums.OrderStatus;
import com.agedcare.modules.elder.mapper.ElderProfileMapper;
import com.agedcare.modules.order.entity.ServiceOrder;
import com.agedcare.modules.order.mapper.ServiceOrderMapper;
import com.agedcare.modules.serviceitem.entity.ServiceItem;
import com.agedcare.modules.serviceitem.mapper.ServiceItemMapper;
import com.agedcare.modules.staff.dto.StaffDetailVO;
import com.agedcare.modules.staff.entity.StaffProfile;
import com.agedcare.modules.staff.mapper.StaffProfileMapper;
import com.agedcare.modules.staff.service.StaffService;
import com.agedcare.modules.stat.dto.DashboardStatVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class StatService {

    private final ServiceOrderMapper serviceOrderMapper;
    private final ElderProfileMapper elderProfileMapper;
    private final StaffProfileMapper staffProfileMapper;
    private final ServiceItemMapper serviceItemMapper;
    private final StaffService staffService;

    public DashboardStatVO getDashboardStat() {
        long totalOrders = serviceOrderMapper.selectCount(null);
        long completedOrders = serviceOrderMapper.selectCount(new LambdaQueryWrapper<ServiceOrder>()
                .eq(ServiceOrder::getStatus, OrderStatus.COMPLETED.getCode()));
        long waitingDispatchOrders = serviceOrderMapper.selectCount(new LambdaQueryWrapper<ServiceOrder>()
                .in(ServiceOrder::getStatus, OrderStatus.WAIT_DISPATCH.getCode(), OrderStatus.WAIT_MANUAL.getCode()));
        long inServiceOrders = serviceOrderMapper.selectCount(new LambdaQueryWrapper<ServiceOrder>()
                .in(ServiceOrder::getStatus, OrderStatus.WAIT_VISIT.getCode(), OrderStatus.IN_SERVICE.getCode()));
        long totalElders = elderProfileMapper.selectCount(null);
        long totalStaff = staffProfileMapper.selectCount(null);

        // 1. 服务类别分布 (助餐送餐, 居家保洁, 代买代办, 陪同办事)
        List<ServiceOrder> allOrders = serviceOrderMapper.selectList(new LambdaQueryWrapper<ServiceOrder>()
                .select(ServiceOrder::getServiceItemId, ServiceOrder::getServiceNameSnapshot));
        Map<String, Integer> categoryCount = new HashMap<>();
        for (ServiceOrder o : allOrders) {
            String name = o.getServiceNameSnapshot() != null ? o.getServiceNameSnapshot() : "其他服务";
            categoryCount.put(name, categoryCount.getOrDefault(name, 0) + 1);
        }
        List<Map<String, Object>> categoryDistribution = new ArrayList<>();
        categoryCount.forEach((k, v) -> {
            Map<String, Object> map = new HashMap<>();
            map.put("name", k);
            map.put("value", v);
            categoryDistribution.add(map);
        });

        // 2. 近7天订单趋势
        List<Map<String, Object>> recentOrdersTrend = new ArrayList<>();
        LocalDate today = LocalDate.now();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM-dd");
        for (int i = 6; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            String label = d.format(fmt);
            Long count = serviceOrderMapper.selectCount(new LambdaQueryWrapper<ServiceOrder>()
                    .ge(ServiceOrder::getCreatedAt, d.atStartOfDay())
                    .le(ServiceOrder::getCreatedAt, d.atTime(23, 59, 59)));
            Map<String, Object> trendItem = new HashMap<>();
            trendItem.put("date", label);
            trendItem.put("count", count);
            recentOrdersTrend.add(trendItem);
        }

        // 3. 人员工作量分布
        List<StaffProfile> staffList = staffProfileMapper.selectList(null);
        List<Map<String, Object>> staffWorkloadList = new ArrayList<>();
        for (StaffProfile sp : staffList) {
            StaffDetailVO detail = staffService.getStaffDetail(sp.getId());
            String staffName = detail.getUser() != null ? detail.getUser().getRealName() : "工作人员#" + sp.getId();
            Map<String, Object> workloadMap = new HashMap<>();
            workloadMap.put("staffId", sp.getId());
            workloadMap.put("staffName", staffName);
            workloadMap.put("totalOrders", sp.getTotalOrders() != null ? sp.getTotalOrders() : 0);
            workloadMap.put("rating", sp.getPriorRating());
            workloadMap.put("status", sp.getCurrentStatus());
            staffWorkloadList.add(workloadMap);
        }

        return DashboardStatVO.builder()
                .totalOrders(totalOrders)
                .completedOrders(completedOrders)
                .waitingDispatchOrders(waitingDispatchOrders)
                .inServiceOrders(inServiceOrders)
                .totalElders(totalElders)
                .totalStaff(totalStaff)
                .categoryDistribution(categoryDistribution)
                .recentOrdersTrend(recentOrdersTrend)
                .staffWorkloadList(staffWorkloadList)
                .build();
    }
}
