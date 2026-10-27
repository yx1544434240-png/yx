package com.agedcare.modules.order.controller;

import com.agedcare.common.api.Result;
import com.agedcare.common.enums.UserRole;
import com.agedcare.common.security.RequireRole;
import com.agedcare.modules.order.dto.OrderCreateDTO;
import com.agedcare.modules.order.dto.OrderQueryDTO;
import com.agedcare.modules.order.dto.OrderTrackingVO;
import com.agedcare.modules.order.dto.OrderVO;
import com.agedcare.modules.order.entity.ServiceOrder;
import com.agedcare.modules.order.service.OrderService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "养老服务订单管理")
@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @Operation(summary = "提交预约服务下单")
    @PostMapping("/create")
    @RequireRole(UserRole.RESIDENT)
    public Result<ServiceOrder> createOrder(@Valid @RequestBody OrderCreateDTO dto) {
        return Result.success(orderService.createOrder(dto));
    }

    @Operation(summary = "分页查询订单列表(按当前角色权限范围过滤)")
    @GetMapping("/page")
    public Result<Page<OrderVO>> pageOrders(@Valid OrderQueryDTO query) {
        return Result.success(orderService.pageOrders(query));
    }

    @Operation(summary = "获取订单详情(含状态时间线、履约记录与评价)")
    @GetMapping("/{id}")
    public Result<OrderVO> getOrderDetail(@PathVariable Long id) {
        return Result.success(orderService.getOrderDetail(id));
    }

    @Operation(summary = "取消订单(服务开始前)")
    @PostMapping("/{id}/cancel")
    @RequireRole({UserRole.RESIDENT, UserRole.ADMIN})
    public Result<Void> cancelOrder(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "用户主动取消") String reason
    ) {
        orderService.cancelOrder(id, reason);
        return Result.success();
    }

    @Operation(summary = "支付订单(老人本人或已审核绑定家属，每单仅一次)")
    @PostMapping("/{id}/pay")
    @RequireRole(UserRole.RESIDENT)
    public Result<OrderVO> payOrder(@PathVariable Long id) {
        return Result.success(orderService.payOrder(id));
    }

    @Operation(summary = "获取订单配送与服务人员实时轨迹")
    @GetMapping("/{id}/tracking")
    public Result<OrderTrackingVO> getOrderTracking(@PathVariable Long id) {
        return Result.success(orderService.getOrderTracking(id));
    }
}
