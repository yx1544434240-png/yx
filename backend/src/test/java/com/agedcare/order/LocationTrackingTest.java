package com.agedcare.order;

import com.agedcare.modules.order.dto.OrderTrackingVO;
import com.agedcare.modules.order.service.OrderService;
import com.agedcare.modules.staff.dto.StaffLocationReportDTO;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

public class LocationTrackingTest {

    private final ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    private final Validator validator = factory.getValidator();

    @Test
    @DisplayName("测试球面距离Haversine公式计算精度与预计时间估算")
    void testCalculateDistanceAndEta() {
        // 天安门坐标: 39.908722, 116.397499
        double lat1 = 39.908722;
        double lon1 = 116.397499;

        // 北京站坐标: 39.905023, 116.427211
        double lat2 = 39.905023;
        double lon2 = 116.427211;

        double distanceMeters = OrderService.calculateDistanceMeters(lat1, lon1, lat2, lon2);
        assertTrue(distanceMeters > 2400 && distanceMeters < 2700, "天安门至北京站两点测距应在2.4km~2.7km之间，实际=" + distanceMeters);

        // 同一点距离应为0
        double zeroDist = OrderService.calculateDistanceMeters(lat1, lon1, lat1, lon1);
        assertEquals(0.0, zeroDist, 0.001);

        // 测算预计到达时间
        int distInt = (int) Math.round(distanceMeters);
        int minutes = distInt <= 50 ? 1 : (int) Math.ceil(distInt / 250.0);
        assertTrue(minutes >= 9 && minutes <= 12, "2.5km骑行上门预计耗时约10~11分钟，实际=" + minutes);
    }

    @Test
    @DisplayName("测试位置上报DTO字段校验约束")
    void testStaffLocationReportDTOValidation() {
        StaffLocationReportDTO dto = new StaffLocationReportDTO();
        assertFalse(validator.validate(dto).isEmpty(), "空DTO应有校验错误");

        dto.setLongitude(new BigDecimal("116.4074"));
        dto.setLatitude(new BigDecimal("39.9042"));
        assertTrue(validator.validate(dto).isEmpty(), "经纬度齐全时应校验通过");
    }

    @Test
    @DisplayName("测试OrderTrackingVO数据结构封装")
    void testOrderTrackingVOBuilder() {
        OrderTrackingVO vo = OrderTrackingVO.builder()
                .orderId(1001L)
                .orderNo("ORD20261021001")
                .status("WAIT_VISIT")
                .destAddress("阳光花园社区1号楼")
                .destLongitude(new BigDecimal("116.407400"))
                .destLatitude(new BigDecimal("39.904200"))
                .staffName("张师傅")
                .staffLongitude(new BigDecimal("116.397400"))
                .staffLatitude(new BigDecimal("39.900000"))
                .distanceMeters(950)
                .estimatedMinutes(4)
                .build();

        assertNotNull(vo);
        assertEquals(1001L, vo.getOrderId());
        assertEquals(950, vo.getDistanceMeters());
        assertEquals(4, vo.getEstimatedMinutes());
    }
}
