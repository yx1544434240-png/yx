package com.agedcare.dispatch;

import com.agedcare.modules.dispatch.dto.CandidateEvaluationResult;
import com.agedcare.modules.dispatch.entity.StaffBooking;
import com.agedcare.modules.dispatch.mapper.StaffBookingMapper;
import com.agedcare.modules.dispatch.service.DispatchScoreEngine;
import com.agedcare.modules.elder.entity.ElderProfile;
import com.agedcare.modules.elder.mapper.ElderProfileMapper;
import com.agedcare.modules.order.entity.ServiceOrder;
import com.agedcare.modules.order.mapper.ServiceOrderMapper;
import com.agedcare.modules.staff.entity.StaffProfile;
import com.agedcare.modules.staff.entity.StaffShift;
import com.agedcare.modules.staff.entity.StaffSkill;
import com.agedcare.modules.staff.mapper.StaffProfileMapper;
import com.agedcare.modules.staff.mapper.StaffShiftMapper;
import com.agedcare.modules.staff.mapper.StaffSkillMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class DispatchCoreDeepTest {

    @Mock
    private StaffProfileMapper staffProfileMapper;
    @Mock
    private StaffSkillMapper staffSkillMapper;
    @Mock
    private StaffShiftMapper staffShiftMapper;
    @Mock
    private StaffBookingMapper staffBookingMapper;
    @Mock
    private ServiceOrderMapper serviceOrderMapper;
    @Mock
    private ElderProfileMapper elderProfileMapper;

    private DispatchScoreEngine scoreEngine;

    @BeforeEach
    void setUp() {
        scoreEngine = new DispatchScoreEngine(
                staffProfileMapper,
                staffSkillMapper,
                staffShiftMapper,
                staffBookingMapper,
                serviceOrderMapper,
                elderProfileMapper,
                new ObjectMapper()
        );
    }

    private StaffProfile createStaff(Long id, String status, double lat, double lng, double rating) {
        return StaffProfile.builder()
                .id(id)
                .userId(100L + id)
                .auditStatus("APPROVED")
                .currentStatus(status)
                .baseLatitude(BigDecimal.valueOf(lat))
                .baseLongitude(BigDecimal.valueOf(lng))
                .priorRating(BigDecimal.valueOf(rating))
                .totalOrders(10)
                .build();
    }

    private ServiceOrder createOrder(Long orderId, String requiredSkills, LocalDateTime start, LocalDateTime end, Double lat, Double lng) {
        return ServiceOrder.builder()
                .id(orderId)
                .orderNo("ORD_" + orderId)
                .elderId(2001L)
                .requiredSkillsSnapshot(requiredSkills)
                .plannedStartAt(start)
                .plannedEndAt(end)
                .durationMinutesSnapshot((int) java.time.Duration.between(start, end).toMinutes())
                .latitude(lat != null ? BigDecimal.valueOf(lat) : null)
                .longitude(lng != null ? BigDecimal.valueOf(lng) : null)
                .addressSnapshot("阳光花园社区3号楼")
                .build();
    }

    @Test
    @DisplayName("硬约束测试1: 技能不匹配时严格过滤并记录原因")
    void testSkillFiltering() {
        StaffProfile staff = createStaff(1L, "AVAILABLE", 39.9042, 116.4074, 4.5);
        LocalDateTime tomorrow = LocalDate.now().plusDays(1).atTime(9, 0);
        ServiceOrder order = createOrder(101L, "NURSING,REHAB", tomorrow, tomorrow.plusHours(1), 39.9050, 116.4080);

        // 模拟该员工仅具备 NURSING 技能，缺少 REHAB
        when(staffSkillMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                StaffSkill.builder().staffId(1L).skillCode("NURSING").skillName("基础照料").auditStatus("APPROVED").build()
        ));

        CandidateEvaluationResult result = scoreEngine.evaluateSingleCandidate(staff, order);
        assertFalse(result.isQualified(), "技能不齐备应被过滤");
        assertTrue(result.getExcludeReason().contains("专业资质"), "拒绝原因应提示专业资质不足");
    }

    @Test
    @DisplayName("硬约束测试2: 工作排班范围过滤(非工作时间/超出班次)")
    void testShiftConstraint() {
        StaffProfile staff = createStaff(2L, "AVAILABLE", 39.9042, 116.4074, 4.8);
        LocalDate tomorrowDate = LocalDate.now().plusDays(1);
        LocalDateTime orderStart = tomorrowDate.atTime(14, 0);
        LocalDateTime orderEnd = tomorrowDate.atTime(15, 0);
        ServiceOrder order = createOrder(102L, "", orderStart, orderEnd, 39.9050, 116.4080);

        when(staffSkillMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
        when(staffShiftMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                StaffShift.builder().staffId(2L).shiftDate(tomorrowDate)
                        .startTime(LocalTime.of(8, 30))
                        .endTime(LocalTime.of(12, 0))
                        .status("ACTIVE").build()
        ));

        CandidateEvaluationResult result = scoreEngine.evaluateSingleCandidate(staff, order);
        assertFalse(result.isQualified(), "超出排班时段应被过滤");
        assertTrue(result.getExcludeReason().contains("超出服务人员工作班次范围"), "拒绝原因应包含排班范围");
    }

    @Test
    @DisplayName("硬约束测试3: 前序任务衔接路程与缓冲时间不足时硬性阻断")
    void testTravelBufferConstraint() {
        StaffProfile staff = createStaff(3L, "AVAILABLE", 39.9042, 116.4074, 4.8);
        LocalDate tomorrowDate = LocalDate.now().plusDays(1);
        LocalDateTime orderStart = tomorrowDate.atTime(10, 15);
        LocalDateTime orderEnd = tomorrowDate.atTime(11, 15);
        // 新订单位置在 (39.9942, 116.5074)，相距约 13 公里
        ServiceOrder order = createOrder(103L, "", orderStart, orderEnd, 39.9942, 116.5074);

        when(staffSkillMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
        when(staffShiftMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                StaffShift.builder().staffId(3L).shiftDate(tomorrowDate)
                        .startTime(LocalTime.of(8, 0))
                        .endTime(LocalTime.of(18, 0))
                        .status("ACTIVE").build()
        ));

        // 前一项任务 10:00 结束于原点 (39.9042, 116.4074)
        StaffBooking prevBooking = StaffBooking.builder()
                .orderId(999L)
                .staffId(3L)
                .startTime(tomorrowDate.atTime(9, 0))
                .endTime(tomorrowDate.atTime(10, 0))
                .status("CONFIRMED")
                .build();
        when(staffBookingMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(prevBooking));

        ServiceOrder prevOrder = createOrder(999L, "", tomorrowDate.atTime(9, 0), tomorrowDate.atTime(10, 0), 39.9042, 116.4074);
        when(serviceOrderMapper.selectById(999L)).thenReturn(prevOrder);

        CandidateEvaluationResult result = scoreEngine.evaluateSingleCandidate(staff, order);
        assertFalse(result.isQualified(), "前序任务与本任务之间仅15分钟间隔，无法跨越13公里路程，应被阻断");
        assertTrue(result.getExcludeReason().contains("前序任务衔接路程时间不足"), "拒绝原因应提示前序路程时间不足");
    }

    @Test
    @DisplayName("缺失坐标处理测试: 明确标记位置未知，按3km保守估算，禁止零距离满分刷榜")
    void testMissingCoordinatesConservativePenalty() {
        StaffProfile staff = createStaff(4L, "AVAILABLE", 39.9042, 116.4074, 4.5);
        LocalDate tomorrowDate = LocalDate.now().plusDays(1);
        LocalDateTime orderStart = tomorrowDate.atTime(9, 0);
        LocalDateTime orderEnd = tomorrowDate.atTime(10, 0);

        // 订单经纬度为 null
        ServiceOrder order = createOrder(104L, "", orderStart, orderEnd, null, null);

        when(staffSkillMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
        when(staffShiftMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                StaffShift.builder().staffId(4L).shiftDate(tomorrowDate)
                        .startTime(LocalTime.of(8, 0))
                        .endTime(LocalTime.of(18, 0))
                        .status("ACTIVE").build()
        ));
        when(staffBookingMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
        when(serviceOrderMapper.selectCount(any())).thenReturn(0L);

        CandidateEvaluationResult result = scoreEngine.evaluateSingleCandidate(staff, order);
        assertTrue(result.isQualified(), "无前序冲突且在排班内应合格");
        assertTrue(result.isLocationUnknown(), "应明确标记位置未知 (locationUnknown=true)");
        // 验证便利度指标 D 是否被保守限制为 0.30 (而不是 1.00 满分)
        assertEquals(0.30, result.getMetricD().doubleValue(), 0.001, "未知坐标到达便利度必须采取保守估值0.30，防止零距离刷分");
        assertTrue(result.getTravelMinutes() >= 20, "未知坐标至少预留20分钟以上出行时间");
    }

    @Test
    @DisplayName("多目标综合评分测试: 0.35*D + 0.30*Q + 0.25*L + 0.10*F 精度验证")
    void testScoreEngineMultiFactorFormula() {
        StaffProfile staff = createStaff(5L, "AVAILABLE", 39.9042, 116.4074, 5.0);
        LocalDate tomorrowDate = LocalDate.now().plusDays(1);
        LocalDateTime orderStart = tomorrowDate.atTime(9, 0);
        LocalDateTime orderEnd = tomorrowDate.atTime(10, 0);

        // 订单在附近 500 米: 39.9080, 116.4074
        ServiceOrder order = createOrder(105L, "", orderStart, orderEnd, 39.9080, 116.4074);

        when(staffSkillMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
        when(staffShiftMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                StaffShift.builder().staffId(5L).shiftDate(tomorrowDate)
                        .startTime(LocalTime.of(8, 0))
                        .endTime(LocalTime.of(18, 0))
                        .status("ACTIVE").build()
        ));
        when(staffBookingMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
        // 曾为该老人服务过 (F=1.0)
        when(serviceOrderMapper.selectCount(any())).thenReturn(2L);

        CandidateEvaluationResult result = scoreEngine.evaluateSingleCandidate(staff, order);
        assertTrue(result.isQualified());
        assertFalse(result.isLocationUnknown());

        // Q=(5.0-1.0)/4 = 1.0
        assertEquals(1.0, result.getMetricQ().doubleValue(), 0.001);
        // F=1.0
        assertEquals(1.0, result.getMetricF().doubleValue(), 0.001);
        // 综合得分高于 0.70
        assertTrue(result.getTotalScore().doubleValue() >= 0.70);
    }

    @Test
    @DisplayName("地址一致性安全检查: 订单地址与老人档案地址不一致时，严禁误用档案坐标，必须执行未知位置保守降级")
    void testElderAddressMismatchTriggersConservativeDegradation() {
        StaffProfile staff = createStaff(6L, "AVAILABLE", 39.9042, 116.4074, 4.6);
        LocalDate tomorrowDate = LocalDate.now().plusDays(1);
        LocalDateTime orderStart = tomorrowDate.atTime(10, 0);
        LocalDateTime orderEnd = tomorrowDate.atTime(11, 0);

        // 订单没有经纬度，且服务地址为子女家
        ServiceOrder order = createOrder(106L, "", orderStart, orderEnd, null, null);
        order.setAddressSnapshot("海淀区中关村南大街1号子女家");
        order.setElderId(501L);

        // 老人档案登记的常驻地址在朝阳区老人家，且档案有坐标 (39.9042, 116.4074)
        ElderProfile elder = ElderProfile.builder()
                .id(501L)
                .name("李大爷")
                .address("朝阳区建国门外大街10号老人家")
                .latitude(BigDecimal.valueOf(39.9042))
                .longitude(BigDecimal.valueOf(116.4074))
                .build();
        when(elderProfileMapper.selectById(501L)).thenReturn(elder);

        when(staffSkillMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
        when(staffShiftMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                StaffShift.builder().staffId(6L).shiftDate(tomorrowDate)
                        .startTime(LocalTime.of(8, 0))
                        .endTime(LocalTime.of(18, 0))
                        .status("ACTIVE").build()
        ));
        when(staffBookingMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
        when(serviceOrderMapper.selectCount(any())).thenReturn(0L);

        CandidateEvaluationResult result = scoreEngine.evaluateSingleCandidate(staff, order);
        assertTrue(result.isQualified());
        assertTrue(result.isLocationUnknown(), "地址不一致时不得继承档案坐标，必须标记 locationUnknown=true");
        assertEquals(0.30, result.getMetricD().doubleValue(), 0.001, "到场便利度必须执行0.30保守降级");
    }

    @Test
    @DisplayName("地址一致性正常继承: 订单地址与老人档案地址完全匹配时，允许安全继承档案坐标并作为已知位置计算")
    void testElderAddressMatchInheritsCoordinates() {
        StaffProfile staff = createStaff(7L, "AVAILABLE", 39.9042, 116.4074, 4.6);
        LocalDate tomorrowDate = LocalDate.now().plusDays(1);
        LocalDateTime orderStart = tomorrowDate.atTime(10, 0);
        LocalDateTime orderEnd = tomorrowDate.atTime(11, 0);

        // 订单没有经纬度，但服务地址就是老人档案地址
        ServiceOrder order = createOrder(107L, "", orderStart, orderEnd, null, null);
        order.setAddressSnapshot("朝阳区建国门外大街10号老人家");
        order.setElderId(502L);

        ElderProfile elder = ElderProfile.builder()
                .id(502L)
                .name("张奶奶")
                .address("朝阳区建国门外大街10号老人家")
                .latitude(BigDecimal.valueOf(39.9050))
                .longitude(BigDecimal.valueOf(116.4080))
                .build();
        when(elderProfileMapper.selectById(502L)).thenReturn(elder);

        when(staffSkillMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
        when(staffShiftMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                StaffShift.builder().staffId(7L).shiftDate(tomorrowDate)
                        .startTime(LocalTime.of(8, 0))
                        .endTime(LocalTime.of(18, 0))
                        .status("ACTIVE").build()
        ));
        when(staffBookingMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
        when(serviceOrderMapper.selectCount(any())).thenReturn(0L);

        CandidateEvaluationResult result = scoreEngine.evaluateSingleCandidate(staff, order);
        assertTrue(result.isQualified());
        assertFalse(result.isLocationUnknown(), "地址完全一致时应成功继承坐标，标记 locationUnknown=false");
        assertTrue(result.getMetricD().doubleValue() > 0.30, "继承坐标后近距离到场便利度应正常计算，大于保守值0.30");
    }
}
