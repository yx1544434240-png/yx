package com.agedcare.order;

import com.agedcare.common.exception.BusinessException;
import com.agedcare.common.security.LoginUser;
import com.agedcare.common.security.UserContext;
import com.agedcare.modules.dispatch.mapper.*;
import com.agedcare.modules.dispatch.service.DispatchService;
import com.agedcare.modules.elder.entity.ElderProfile;
import com.agedcare.modules.elder.mapper.ElderProfileMapper;
import com.agedcare.modules.elder.service.ElderProfileService;
import com.agedcare.modules.fulfillment.mapper.*;
import com.agedcare.modules.order.dto.OrderCreateDTO;
import com.agedcare.modules.order.entity.ServiceOrder;
import com.agedcare.modules.order.mapper.*;
import com.agedcare.modules.order.service.OrderService;
import com.agedcare.modules.serviceitem.entity.ServiceItem;
import com.agedcare.modules.serviceitem.mapper.ServiceItemMapper;
import com.agedcare.modules.staff.mapper.StaffProfileMapper;
import com.agedcare.modules.staff.service.StaffService;
import com.agedcare.modules.user.mapper.SysUserMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderAddressSnapshotTest {
    @Mock ServiceOrderMapper orders;
    @Mock OrderStatusLogMapper logs;
    @Mock ServiceItemMapper items;
    @Mock ElderProfileMapper elders;
    @Mock ElderProfileService elderService;
    @Mock StaffProfileMapper staffProfiles;
    @Mock StaffService staffService;
    @Mock DispatchAttemptMapper attempts;
    @Mock StaffBookingMapper bookings;
    @Mock ServiceRecordMapper records;
    @Mock ServiceReviewMapper reviews;
    @Mock DispatchService dispatch;
    @Mock SysUserMapper users;
    @InjectMocks OrderService service;

    @BeforeEach void init() {
        UserContext.setUser(LoginUser.builder().id(1L).role("RESIDENT").build());
        when(elders.selectById(2L)).thenReturn(ElderProfile.builder().id(2L).address("原住址")
                .latitude(new BigDecimal("30")).longitude(new BigDecimal("120")).build());
        when(items.selectById(3L)).thenReturn(ServiceItem.builder().id(3L).name("保洁")
                .status("ON_SHELF").durationMinutes(60).build());
    }
    @AfterEach void cleanup() { UserContext.clear(); }

    private OrderCreateDTO dto(String address) {
        OrderCreateDTO dto = new OrderCreateDTO();
        dto.setElderId(2L); dto.setServiceItemId(3L); dto.setAddress(address);
        dto.setPlannedStartAt(LocalDateTime.now().plusDays(1).withHour(10));
        return dto;
    }
    private ServiceOrder create(OrderCreateDTO dto) {
        when(orders.insert(any(ServiceOrder.class))).thenAnswer(call -> {
            call.getArgument(0, ServiceOrder.class).setId(10L); return 1;
        });
        service.createOrder(dto);
        ArgumentCaptor<ServiceOrder> order = ArgumentCaptor.forClass(ServiceOrder.class);
        verify(orders).insert(order.capture());
        return order.getValue();
    }

    @Test void changedAddressMustNotInheritOldCoordinates() {
        ServiceOrder saved = create(dto("新住址"));
        assertNull(saved.getLatitude()); assertNull(saved.getLongitude());
    }
    @Test void trackingForChangedAddressCannotRestoreOldElderCoordinates() {
        ServiceOrder saved = create(dto("新住址"));
        var tracking = service.buildTrackingVO(saved);
        assertNull(tracking.getDestLatitude()); assertNull(tracking.getDestLongitude());
    }
    @Test void unchangedAddressCanInheritElderCoordinates() {
        ServiceOrder saved = create(dto("原住址"));
        assertEquals(new BigDecimal("30"), saved.getLatitude());
        assertEquals(new BigDecimal("120"), saved.getLongitude());
    }
    @Test void explicitCoordinatesArePreservedIncludingZero() {
        OrderCreateDTO dto = dto("新住址");
        dto.setLatitude(BigDecimal.ZERO); dto.setLongitude(new BigDecimal("110"));
        ServiceOrder saved = create(dto);
        assertEquals(BigDecimal.ZERO, saved.getLatitude());
        assertEquals(new BigDecimal("110"), saved.getLongitude());
    }
    @Test void partialCoordinatesCannotBeMixedWithOldAddressCoordinates() {
        OrderCreateDTO dto = dto("新住址"); dto.setLatitude(BigDecimal.ONE);
        assertThrows(BusinessException.class, () -> service.createOrder(dto));
        verify(orders, never()).insert(any(ServiceOrder.class));
    }
    @Test void outOfRangeCoordinatesAreRejectedBeforeSaving() {
        OrderCreateDTO dto = dto("新住址");
        dto.setLatitude(new BigDecimal("91")); dto.setLongitude(BigDecimal.ONE);
        assertThrows(BusinessException.class, () -> service.createOrder(dto));
        verify(orders, never()).insert(any(ServiceOrder.class));
    }
}
