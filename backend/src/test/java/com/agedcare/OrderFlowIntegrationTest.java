package com.agedcare;

import com.agedcare.common.enums.OrderStatus;
import com.agedcare.common.enums.UserRole;
import com.agedcare.common.security.JwtTokenProvider;
import com.agedcare.common.security.LoginUser;
import com.agedcare.common.security.UserContext;
import com.agedcare.modules.dispatch.entity.DispatchAttempt;
import com.agedcare.modules.dispatch.mapper.DispatchAttemptMapper;
import com.agedcare.modules.fulfillment.entity.ServiceRecord;
import com.agedcare.modules.fulfillment.mapper.ServiceRecordMapper;
import com.agedcare.modules.fulfillment.service.FulfillmentService;
import com.agedcare.modules.order.entity.ServiceOrder;
import com.agedcare.modules.order.mapper.ServiceOrderMapper;
import com.agedcare.modules.staff.service.StaffService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class OrderFlowIntegrationTest extends IsolatedIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private ServiceOrderMapper serviceOrderMapper;

    @Autowired
    private DispatchAttemptMapper dispatchAttemptMapper;

    @Autowired
    private ServiceRecordMapper serviceRecordMapper;

    @Autowired
    private FulfillmentService fulfillmentService;

    @Autowired
    private StaffService staffService;

    private String generateToken(Long userId, String username, String role) {
        LoginUser loginUser = LoginUser.builder()
                .id(userId)
                .username(username)
                .realName("测试" + username)
                .role(role)
                .build();
        return jwtTokenProvider.generateToken(loginUser);
    }

    @Test
    @DisplayName("R1安全拦截验证: 匿名调用保存项目接口必须返回401，普通居民必须返回403")
    public void testServiceItemSecurity() throws Exception {
        // 1. 匿名用户尝试调用 /service-items/save 应被拦截为 401
        mockMvc.perform(post("/service-items/save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"黑客新增项目\",\"price\":10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));

        // 2. 普通居民身份尝试调用 /service-items/save 应被拦截为 403 (使用数据库中存在的 resident01 用户 ID 5)
        String residentToken = generateToken(5L, "resident01", UserRole.RESIDENT.getCode());
        mockMvc.perform(post("/service-items/save")
                        .header("Authorization", "Bearer " + residentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"越权项目\",\"price\":10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));

        // 3. 匿名用户调用公开 /service-items/list-available 应放行 200
        mockMvc.perform(get("/service-items/list-available"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("R2与履约闭环验证: startService成功插入待完结记录(不报非空错误)并由submitServiceRecord完结")
    public void testStartAndSubmitService() {
        // 创建测试订单与接单记录
        ServiceOrder order = ServiceOrder.builder()
                .orderNo("TEST_ORD_" + System.currentTimeMillis())
                .requesterUserId(5L)
                .elderId(201L)
                .serviceItemId(301L)
                .serviceNameSnapshot("测试助餐")
                .priceSnapshot(new BigDecimal("15.00"))
                .durationMinutesSnapshot(30)
                .plannedStartAt(LocalDateTime.now().plusHours(1))
                .plannedEndAt(LocalDateTime.now().plusHours(2))
                .addressSnapshot("阳光社区1号楼")
                .contactNameSnapshot("赵先生")
                .contactPhoneSnapshot("13800000004")
                .status(OrderStatus.WAIT_VISIT.getCode())
                .dispatchRound(1)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        serviceOrderMapper.insert(order);

        DispatchAttempt attempt = DispatchAttempt.builder()
                .orderId(order.getId())
                .dispatchRound(1)
                .attemptNo(1)
                .staffId(401L)
                .dispatchType("AUTO")
                .deadline(LocalDateTime.now().plusMinutes(2))
                .status("ACCEPTED")
                .acceptedAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        dispatchAttemptMapper.insert(attempt);

        order.setCurrentDispatchId(attempt.getId());
        serviceOrderMapper.updateById(order);

        // 模拟 401 师傅登录
        LoginUser staffUser = LoginUser.builder()
                .id(2L) // sys_user 2 对应 staff_profile 401
                .username("staff01")
                .role(UserRole.STAFF.getCode())
                .build();
        UserContext.setUser(staffUser);

        try {
            // 1. 开始服务
            fulfillmentService.startService(order.getId());

            // 验证订单状态流转为 IN_SERVICE
            ServiceOrder inServiceOrder = serviceOrderMapper.selectById(order.getId());
            Assertions.assertEquals(OrderStatus.IN_SERVICE.getCode(), inServiceOrder.getStatus());

            // 验证 service_record 记录存在，且 actualStartAt 已经记录
            ServiceRecord record = serviceRecordMapper.selectOne(new LambdaQueryWrapper<ServiceRecord>()
                    .eq(ServiceRecord::getOrderId, order.getId()));
            Assertions.assertNotNull(record, "开始服务后应成功插入履约记录");
            Assertions.assertNotNull(record.getActualStartAt());
            Assertions.assertNull(record.getActualEndAt(), "开始服务时结束时间应为null");

            // 2. 提交完成服务记录 (服务历时30分钟)
            record.setActualEndAt(record.getActualStartAt().plusMinutes(30));
            record.setServiceSummary("服务按标准规范顺利完成，老人反馈良好");
            fulfillmentService.submitServiceRecord(record);

            // 验证订单状态流转为 WAIT_CONFIRM
            ServiceOrder waitConfirmOrder = serviceOrderMapper.selectById(order.getId());
            Assertions.assertEquals(OrderStatus.WAIT_CONFIRM.getCode(), waitConfirmOrder.getStatus());

            // 3. 老人/居民确认服务完成
            LoginUser residentUser = LoginUser.builder()
                    .id(5L)
                    .username("resident01")
                    .role(UserRole.RESIDENT.getCode())
                    .build();
            UserContext.setUser(residentUser);

            fulfillmentService.confirmOrder(order.getId(), "老人非常满意");

            // 验证订单状态流转为 COMPLETED
            ServiceOrder completedOrder = serviceOrderMapper.selectById(order.getId());
            Assertions.assertEquals(OrderStatus.COMPLETED.getCode(), completedOrder.getStatus());
            Assertions.assertNotNull(completedOrder.getConfirmedAt());

        } finally {
            UserContext.clear();
        }
    }

    @Test
    @DisplayName("R5人员状态权限验证: 普通居民不得擅自修改服务师傅工作状态")
    public void testStaffStatusSecurity() {
        LoginUser residentUser = LoginUser.builder()
                .id(5L)
                .username("resident01")
                .role(UserRole.RESIDENT.getCode())
                .build();
        UserContext.setUser(residentUser);

        try {
            // 普通居民修改 401 师傅状态应抛出无权限异常
            Assertions.assertThrows(Exception.class, () -> {
                staffService.updateStaffStatus(401L, "RESTING");
            });
        } finally {
            UserContext.clear();
        }
    }
}
