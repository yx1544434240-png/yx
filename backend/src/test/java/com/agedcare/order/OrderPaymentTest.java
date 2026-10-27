package com.agedcare.order;

import com.agedcare.common.exception.BusinessException;
import com.agedcare.common.security.LoginUser;
import com.agedcare.common.security.UserContext;
import com.agedcare.modules.dispatch.mapper.*;
import com.agedcare.modules.dispatch.service.DispatchService;
import com.agedcare.modules.elder.entity.ElderProfile;
import com.agedcare.modules.elder.mapper.ElderProfileMapper;
import com.agedcare.modules.elder.service.ElderProfileService;
import com.agedcare.modules.family.mapper.FamilyBindingMapper;
import com.agedcare.modules.fulfillment.mapper.*;
import com.agedcare.modules.order.dto.OrderVO;
import com.agedcare.modules.order.entity.ServiceOrder;
import com.agedcare.modules.order.mapper.*;
import com.agedcare.modules.order.service.OrderService;
import com.agedcare.modules.serviceitem.mapper.ServiceItemMapper;
import com.agedcare.modules.staff.mapper.StaffProfileMapper;
import com.agedcare.modules.staff.service.StaffService;
import com.agedcare.modules.user.mapper.SysUserMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 支付：老人本人与已审核绑定家属可付，其他人 403，每单只能付一次。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderPaymentTest {
    static final long ELDER_USER = 10L, FAMILY_USER = 20L, STRANGER = 30L, ELDER_ID = 2L, ORDER_ID = 99L;

    @Mock ServiceOrderMapper orders;
    @Mock OrderStatusLogMapper logs;
    @Mock ServiceItemMapper items;
    @Mock ElderProfileMapper elders;
    @Mock FamilyBindingMapper bindings;
    @Mock StaffProfileMapper staffProfiles;
    @Mock StaffService staffService;
    @Mock DispatchAttemptMapper attempts;
    @Mock StaffBookingMapper bookings;
    @Mock ServiceRecordMapper records;
    @Mock ServiceReviewMapper reviews;
    @Mock com.agedcare.modules.fulfillment.mapper.ServiceComplaintMapper complaints;
    @Mock DispatchService dispatch;
    @Mock SysUserMapper users;
    OrderService service;
    ServiceOrder order;

    @BeforeEach void init() {
        // 用真实的 ElderProfileService，绑定关系只由 FamilyBindingMapper 决定
        ElderProfileService profiles = new ElderProfileService(elders, bindings, staffProfiles, orders);
        service = new OrderService(orders, logs, items, elders, profiles, staffProfiles, staffService, attempts,
                bookings, records, reviews, complaints, dispatch, users);
        order = ServiceOrder.builder().id(ORDER_ID).elderId(ELDER_ID).requesterUserId(FAMILY_USER).status("WAIT_VISIT").build();
        when(orders.selectByIdForUpdate(ORDER_ID)).thenAnswer(i -> order);
        when(elders.selectById(ELDER_ID)).thenReturn(ElderProfile.builder().id(ELDER_ID).userId(ELDER_USER).name("钱桂芳").idCard("110101194611152345").build());
        when(orders.markPaid(eq(ORDER_ID), anyLong(), any())).thenAnswer(i -> {
            if (order.getPaidAt() != null) return 0;
            order.setPaidByUserId(i.getArgument(1)); order.setPaidAt(i.getArgument(2)); return 1;
        });
    }
    @AfterEach void cleanup() { UserContext.clear(); }

    private void as(long id, String role) { UserContext.setUser(LoginUser.builder().id(id).role(role).build()); }
    private void bound(long userId, boolean approved) {
        when(bindings.selectCount(any())).thenReturn(approved ? 1L : 0L);
    }

    @Test void elderThemselvesCanPayAndIsRecordedAsPayer() {
        as(ELDER_USER, "RESIDENT");
        OrderVO vo = service.payOrder(ORDER_ID);
        assertTrue(vo.isPaid());
        assertEquals(ELDER_USER, vo.getPayerUserId());
        verify(orders).markPaid(eq(ORDER_ID), eq(ELDER_USER), any());
    }

    @Test void approvedFamilyMemberCanPay() {
        as(FAMILY_USER, "RESIDENT"); bound(FAMILY_USER, true);
        OrderVO vo = service.payOrder(ORDER_ID);
        assertEquals(FAMILY_USER, vo.getPayerUserId());
        assertEquals(FAMILY_USER, order.getPaidByUserId());
    }

    @Test void unboundUserIsRejectedWith403() {
        as(STRANGER, "RESIDENT"); bound(STRANGER, false);
        BusinessException e = assertThrows(BusinessException.class, () -> service.payOrder(ORDER_ID));
        assertEquals(403, e.getCode());
        verify(orders, never()).markPaid(anyLong(), anyLong(), any());
    }

    @Test void requesterWhoseBindingIsNoLongerApprovedIsRejected() {
        as(FAMILY_USER, "RESIDENT"); bound(FAMILY_USER, false); // 下单人本身，但绑定已撤销
        assertEquals(403, assertThrows(BusinessException.class, () -> service.payOrder(ORDER_ID)).getCode());
    }

    @Test void staffAndAdminAreRejectedWith403() {
        for (String role : new String[]{"STAFF", "ADMIN"}) {
            as(ELDER_USER, role);
            assertEquals(403, assertThrows(BusinessException.class, () -> service.payOrder(ORDER_ID)).getCode(), role);
        }
    }

    @Test void repeatPaymentIsRejected() {
        as(ELDER_USER, "RESIDENT");
        service.payOrder(ORDER_ID);
        as(FAMILY_USER, "RESIDENT"); bound(FAMILY_USER, true);
        BusinessException e = assertThrows(BusinessException.class, () -> service.payOrder(ORDER_ID));
        assertEquals(409, e.getCode());
        assertEquals(ELDER_USER, order.getPaidByUserId(), "first payer is kept");
    }

    @Test void concurrentSecondWriterLosesTheConditionalUpdate() {
        as(ELDER_USER, "RESIDENT");
        when(orders.markPaid(eq(ORDER_ID), anyLong(), any())).thenReturn(0); // 另一请求已先写入
        assertEquals(409, assertThrows(BusinessException.class, () -> service.payOrder(ORDER_ID)).getCode());
    }

    @Test void cancelledOrderCannotBePaid() {
        as(ELDER_USER, "RESIDENT");
        order.setStatus("CANCELLED");
        assertEquals(409, assertThrows(BusinessException.class, () -> service.payOrder(ORDER_ID)).getCode());
    }

    @Test void detailExposesCanPayOnlyForEligibleUnpaidOrders() {
        when(orders.selectById(ORDER_ID)).thenAnswer(i -> order);
        as(ELDER_USER, "RESIDENT");
        assertTrue(service.getOrderDetail(ORDER_ID).isCanPay());
        order.setPaidAt(LocalDateTime.now()); order.setPaidByUserId(ELDER_USER);
        OrderVO paid = service.getOrderDetail(ORDER_ID);
        assertFalse(paid.isCanPay());
        assertTrue(paid.isPaid());
    }

    @Test void orderDetailNeverExposesTheElderIdCard() throws Exception {
        when(orders.selectById(ORDER_ID)).thenAnswer(i -> order);
        as(ELDER_USER, "RESIDENT");
        OrderVO vo = service.getOrderDetail(ORDER_ID);
        assertEquals("钱桂芳", vo.getElder().getName());
        String json = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules().writeValueAsString(vo);
        assertFalse(json.contains("idCard"), json);
        assertFalse(json.contains("110101194611152345"));
    }
}
