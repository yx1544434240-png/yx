package com.agedcare.modules.dispatch.service;

import com.agedcare.common.enums.BookingStatus;
import com.agedcare.common.utils.DistanceUtil;
import com.agedcare.modules.dispatch.dto.CandidateEvaluationResult;
import com.agedcare.modules.dispatch.entity.StaffBooking;
import com.agedcare.modules.dispatch.mapper.StaffBookingMapper;
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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class DispatchScoreEngine {

    private final StaffProfileMapper staffProfileMapper;
    private final StaffSkillMapper staffSkillMapper;
    private final StaffShiftMapper staffShiftMapper;
    private final StaffBookingMapper staffBookingMapper;
    private final ServiceOrderMapper serviceOrderMapper;
    private final ElderProfileMapper elderProfileMapper;
    private final ObjectMapper objectMapper;

    @Value("${dispatch.speed-km-per-hour:15.0}")
    private double speedKmPerHour = 15.0;

    @Value("${dispatch.buffer-minutes:10}")
    private int bufferMinutes = 10;

    @Value("${dispatch.weights.distance:0.35}")
    private double weightDistance = 0.35;

    @Value("${dispatch.weights.quality:0.30}")
    private double weightQuality = 0.30;

    @Value("${dispatch.weights.workload:0.25}")
    private double weightWorkload = 0.25;

    @Value("${dispatch.weights.familiarity:0.10}")
    private double weightFamiliarity = 0.10;

    @Value("${dispatch.prior.rating:4.0}")
    private double priorRating = 4.0;

    @Value("${dispatch.prior.samples:5}")
    private int priorSamples = 5;

    public List<CandidateEvaluationResult> evaluateAllCandidates(ServiceOrder order) {
        List<StaffProfile> allStaff = staffProfileMapper.selectList(new LambdaQueryWrapper<StaffProfile>()
                .eq(StaffProfile::getAuditStatus, "APPROVED"));

        List<CandidateEvaluationResult> results = new ArrayList<>();
        for (StaffProfile staff : allStaff) {
            results.add(evaluateSingleCandidate(staff, order));
        }

        // 排序规则:
        // 1. 合格优先 (qualified DESC)
        // 2. 总分降序 (totalScore DESC)
        // 3. 到达耗时升序 (travelMinutes ASC)
        // 4. 工作量升序 (workload ASC)
        // 5. 人员ID升序 (staffId ASC)
        results.sort((a, b) -> {
            if (a.isQualified() != b.isQualified()) {
                return a.isQualified() ? -1 : 1;
            }
            if (a.isQualified()) {
                int scoreComp = b.getTotalScore().compareTo(a.getTotalScore());
                if (scoreComp != 0) return scoreComp;

                int travelComp = Integer.compare(a.getTravelMinutes(), b.getTravelMinutes());
                if (travelComp != 0) return travelComp;
            }
            return Long.compare(a.getStaffId(), b.getStaffId());
        });

        return results;
    }

    public CandidateEvaluationResult evaluateSingleCandidate(StaffProfile staff, ServiceOrder order) {
        return evaluateSingleCandidate(staff, order, LocalDateTime.now());
    }

    public CandidateEvaluationResult evaluateSingleCandidate(StaffProfile staff, ServiceOrder order, LocalDateTime evaluationTime) {
        ElderProfile elder = (elderProfileMapper != null && order.getElderId() != null)
                ? elderProfileMapper.selectById(order.getElderId()) : null;

        List<StaffSkill> skills = (staffSkillMapper != null)
                ? staffSkillMapper.selectList(new LambdaQueryWrapper<StaffSkill>()
                        .eq(StaffSkill::getStaffId, staff.getId())
                        .eq(StaffSkill::getAuditStatus, "APPROVED"))
                : Collections.emptyList();

        LocalDate orderDate = order.getPlannedStartAt().toLocalDate();
        List<StaffShift> shifts = (staffShiftMapper != null)
                ? staffShiftMapper.selectList(new LambdaQueryWrapper<StaffShift>()
                        .eq(StaffShift::getStaffId, staff.getId())
                        .eq(StaffShift::getShiftDate, orderDate)
                        .eq(StaffShift::getStatus, "ACTIVE"))
                : Collections.emptyList();

        LocalDateTime dayStart = orderDate.atStartOfDay();
        LocalDateTime dayEnd = orderDate.atTime(23, 59, 59);
        List<StaffBooking> dayBookings = (staffBookingMapper != null)
                ? staffBookingMapper.selectList(new LambdaQueryWrapper<StaffBooking>()
                        .eq(StaffBooking::getStaffId, staff.getId())
                        .ge(StaffBooking::getStartTime, dayStart)
                        .le(StaffBooking::getEndTime, dayEnd)
                        .in(StaffBooking::getStatus, BookingStatus.HELD.getCode(), BookingStatus.CONFIRMED.getCode(), BookingStatus.FINISHED.getCode())
                        .orderByAsc(StaffBooking::getStartTime))
                : Collections.emptyList();

        java.util.function.Function<Long, ServiceOrder> orderLookup = (orderId) ->
                (serviceOrderMapper != null && orderId != null) ? serviceOrderMapper.selectById(orderId) : null;

        Long completedCount = (serviceOrderMapper != null && order.getElderId() != null)
                ? serviceOrderMapper.selectCount(new LambdaQueryWrapper<ServiceOrder>()
                        .eq(ServiceOrder::getElderId, order.getElderId())
                        .eq(ServiceOrder::getStatus, "COMPLETED")
                        .apply("id in (select order_id from service_record where staff_id = {0})", staff.getId()))
                : 0L;

        boolean hasFamiliarity = completedCount != null && completedCount > 0;
        return evaluateCandidateWithContext(staff, order, skills, shifts, dayBookings, orderLookup, elder, hasFamiliarity, evaluationTime);
    }

    /**
     * 核心候选人资格与多目标综合评分函数（完全解耦I/O，供生产调度与基准仿真实验完全共享）
     */
    public CandidateEvaluationResult evaluateCandidateWithContext(
            StaffProfile staff,
            ServiceOrder order,
            List<StaffSkill> skills,
            List<StaffShift> shifts,
            List<StaffBooking> dayBookings,
            java.util.function.Function<Long, ServiceOrder> orderLookup,
            ElderProfile elder,
            boolean hasFamiliarity,
            LocalDateTime evaluationTime) {

        CandidateEvaluationResult result = CandidateEvaluationResult.builder()
                .staffId(staff.getId())
                .staffName("工作人员#" + staff.getId())
                .qualified(false)
                .travelMinutes(0)
                .metricD(BigDecimal.ZERO)
                .metricQ(BigDecimal.ZERO)
                .metricL(BigDecimal.ZERO)
                .metricF(BigDecimal.ZERO)
                .totalScore(BigDecimal.ZERO)
                .build();

        Map<String, Object> debugContext = new HashMap<>();
        debugContext.put("staffId", staff.getId());
        debugContext.put("orderId", order.getId());

        // 1. 账号与服务状态检查
        if ("RESTING".equalsIgnoreCase(staff.getCurrentStatus())) {
            result.setExcludeReason("人员处于休息状态");
            recordContext(result, debugContext);
            return result;
        }

        // 2. 服务区域检查
        if (StringUtils.hasText(order.getAddressSnapshot()) && StringUtils.hasText(staff.getServiceArea())) {
            String area = staff.getServiceArea().trim();
            boolean areaMatch = "全网格".equals(area) || "全区".equals(area) || "通用".equals(area)
                    || order.getAddressSnapshot().contains(area)
                    || area.contains(order.getAddressSnapshot());
            if (!areaMatch && elder != null && StringUtils.hasText(elder.getCommunity())) {
                areaMatch = elder.getCommunity().contains(area) || area.contains(elder.getCommunity());
            }
            if (!areaMatch) {
                result.setExcludeReason("服务地址不在该人员负责网格区域(" + area + ")");
                recordContext(result, debugContext);
                return result;
            }
        }

        // 3. 技能硬约束检查
        String requiredSkillsStr = order.getRequiredSkillsSnapshot();
        if (StringUtils.hasText(requiredSkillsStr)) {
            List<String> required = new ArrayList<>();
            if (requiredSkillsStr.trim().startsWith("[")) {
                try {
                    required = objectMapper.readValue(requiredSkillsStr, new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
                } catch (Exception ignored) {}
            }
            if (required == null || required.isEmpty()) {
                required = Arrays.stream(requiredSkillsStr.split(","))
                        .map(String::trim)
                        .filter(StringUtils::hasText)
                        .collect(Collectors.toList());
            }

            if (!required.isEmpty()) {
                Set<String> staffSkillCodes = (skills != null) ? skills.stream()
                        .map(StaffSkill::getSkillCode)
                        .collect(Collectors.toSet()) : Collections.emptySet();

                for (String reqSkill : required) {
                    if (!staffSkillCodes.contains(reqSkill)) {
                        result.setExcludeReason("缺少该服务项目必需的专业资质: " + reqSkill);
                        recordContext(result, debugContext);
                        return result;
                    }
                }
            }
        }

        LocalDate orderDate = order.getPlannedStartAt().toLocalDate();

        // 4. 当日排班检查
        if (shifts == null || shifts.isEmpty()) {
            result.setExcludeReason("当日没有有效工作排班");
            recordContext(result, debugContext);
            return result;
        }

        StaffShift shift = shifts.get(0);
        LocalTime shiftStart = shift.getStartTime();
        LocalTime shiftEnd = shift.getEndTime();

        // 5. 出行时间与位置推算
        LocalDateTime dayStart = orderDate.atStartOfDay();
        List<StaffBooking> safeBookings = (dayBookings != null) ? dayBookings : Collections.emptyList();

        // 检查与已有任务的时段冲突
        for (StaffBooking b : safeBookings) {
            // [startA, endA) 与 [startB, endB) 重叠: startA < endB && endA > startB
            if (order.getPlannedStartAt().isBefore(b.getEndTime()) && order.getPlannedEndAt().isAfter(b.getStartTime())) {
                result.setExcludeReason("与已有服务排班预留时段存在冲突");
                recordContext(result, debugContext);
                return result;
            }
        }

        // 推算该单前一项任务或站点位置
        StaffBooking prevBooking = null;
        StaffBooking nextBooking = null;
        for (StaffBooking b : safeBookings) {
            if (b.getEndTime().isBefore(order.getPlannedStartAt()) || b.getEndTime().isEqual(order.getPlannedStartAt())) {
                prevBooking = b;
            } else if (b.getStartTime().isAfter(order.getPlannedEndAt()) || b.getStartTime().isEqual(order.getPlannedEndAt())) {
                if (nextBooking == null) {
                    nextBooking = b;
                }
            }
        }

        double originLat = staff.getBaseLatitude() != null ? staff.getBaseLatitude().doubleValue() : 39.9042;
        double originLng = staff.getBaseLongitude() != null ? staff.getBaseLongitude().doubleValue() : 116.4074;

        if (prevBooking != null && orderLookup != null) {
            ServiceOrder prevOrder = orderLookup.apply(prevBooking.getOrderId());
            if (prevOrder != null && prevOrder.getLatitude() != null && prevOrder.getLongitude() != null) {
                originLat = prevOrder.getLatitude().doubleValue();
                originLng = prevOrder.getLongitude().doubleValue();
            }
        }

        BigDecimal orderLat = order.getLatitude();
        BigDecimal orderLng = order.getLongitude();
        if (!validCoordinates(orderLat, orderLng)) {
            orderLat = null;
            orderLng = null;
        }

        // 仅当订单服务地址与老人档案常驻地址一致且档案具备有效坐标时，才继承档案坐标
        // 若老人临时变更至子女家等其他地址，不可误用原住址坐标，必须执行未知位置保守降级
        if (orderLat == null && order.getElderId() != null && elder != null) {
            if (validCoordinates(elder.getLatitude(), elder.getLongitude())) {
                String orderAddr = order.getAddressSnapshot() != null ? order.getAddressSnapshot().trim() : "";
                String elderAddr = elder.getAddress() != null ? elder.getAddress().trim() : "";
                if (StringUtils.hasText(orderAddr) && Objects.equals(orderAddr, elderAddr)) {
                    orderLat = elder.getLatitude();
                    orderLng = elder.getLongitude();
                }
            }
        }

        boolean locationUnknown = false;
        double destLat;
        double destLng;
        double distanceKm;
        int travelMinutes;

        if (orderLat != null && orderLng != null) {
            destLat = orderLat.doubleValue();
            destLng = orderLng.doubleValue();
            distanceKm = DistanceUtil.calculateDistanceKm(originLat, originLng, destLat, destLng);
            travelMinutes = DistanceUtil.estimateTravelMinutes(distanceKm, speedKmPerHour, bufferMinutes);
        } else {
            // 坐标缺失：明确标记位置未知，按社区中位半径保守预估 3.0km (约需22分钟出行时间)，防止零距离高分刷榜
            locationUnknown = true;
            destLat = originLat;
            destLng = originLng;
            distanceKm = 3.0;
            travelMinutes = DistanceUtil.estimateTravelMinutes(distanceKm, speedKmPerHour, bufferMinutes);
        }
        result.setLocationUnknown(locationUnknown);
        result.setTravelMinutes(travelMinutes);

        // 检查前序出行是否可行: plannedStartAt - travelMinutes 必须晚于或等于 prevBooking.endTime
        LocalDateTime earliestDepart = prevBooking != null ? prevBooking.getEndTime() : dayStart.with(shiftStart);
        LocalDateTime requiredDepart = order.getPlannedStartAt().minusMinutes(travelMinutes);
        if (requiredDepart.isBefore(earliestDepart)) {
            result.setExcludeReason(String.format("前序任务衔接路程时间不足(需要预留%d分钟%s)",
                    travelMinutes, locationUnknown ? "，位置未知按3.0km保守测算" : ""));
            recordContext(result, debugContext);
            return result;
        }

        // 检查该单结束后前往下一单是否可行
        if (nextBooking != null && orderLookup != null) {
            double nextDestLat = originLat;
            double nextDestLng = originLng;
            boolean hasNextCoord = false;
            ServiceOrder nextOrder = orderLookup.apply(nextBooking.getOrderId());
            if (nextOrder != null && nextOrder.getLatitude() != null && nextOrder.getLongitude() != null) {
                nextDestLat = nextOrder.getLatitude().doubleValue();
                nextDestLng = nextOrder.getLongitude().doubleValue();
                hasNextCoord = true;
            }
            double nextDist = hasNextCoord
                    ? DistanceUtil.calculateDistanceKm(destLat, destLng, nextDestLat, nextDestLng)
                    : 2.0; // 缺省邻近网格保底预估2km
            int nextTravel = DistanceUtil.estimateTravelMinutes(nextDist, speedKmPerHour, bufferMinutes);
            if (order.getPlannedEndAt().plusMinutes(nextTravel).isAfter(nextBooking.getStartTime())) {
                result.setExcludeReason(String.format("将导致后续任务无法按时到达(需路程%d分钟)", nextTravel));
                recordContext(result, debugContext);
                return result;
            }
        }

        // 检查是否在排班工作时间内
        LocalDateTime shiftStartDt = orderDate.atTime(shiftStart);
        LocalDateTime shiftEndDt = orderDate.atTime(shiftEnd);
        if (requiredDepart.isBefore(shiftStartDt) || order.getPlannedEndAt().isAfter(shiftEndDt)) {
            result.setExcludeReason(String.format("超出服务人员工作班次范围(%s~%s)", shiftStart, shiftEnd));
            recordContext(result, debugContext);
            return result;
        }

        // 检查当前时间是否仍能赶上
        if (evaluationTime != null && evaluationTime.isAfter(requiredDepart)) {
            result.setExcludeReason("当前时间已无法按预计路程准时到达");
            recordContext(result, debugContext);
            return result;
        }

        // ======================= 硬约束全部通过！开始多因素评分 =======================
        result.setQualified(true);
        result.setExcludeReason("");

        // 1. 指标 D: 到达便利度 D = 1 - min(travelMinutes / 30.0, 1.0)
        // 坐标未知时采取保守估值(0.30)，防止位置未知订单因零距离获得1.0满分
        double dVal = locationUnknown
                ? 0.30
                : Math.max(0.0, 1.0 - Math.min((double) travelMinutes / 30.0, 1.0));
        BigDecimal metricD = BigDecimal.valueOf(dVal).setScale(4, RoundingMode.HALF_UP);
        result.setMetricD(metricD);

        // 2. 指标 Q: 服务质量均分 (消除二次贝叶斯平滑)
        double qRating = staff.getPriorRating() != null ? staff.getPriorRating().doubleValue() : priorRating;
        // 归一化到 0~1: (qRating - 1) / 4
        double qVal = Math.max(0.0, Math.min(1.0, (qRating - 1.0) / 4.0));
        BigDecimal metricQ = BigDecimal.valueOf(qVal).setScale(4, RoundingMode.HALF_UP);
        result.setMetricQ(metricQ);

        // 3. 指标 L: 当日工作量余量 (计入任务时长与出行耗时，分母采用班次真实时长)
        long shiftTotalMinutes = Duration.between(shiftStart, shiftEnd).toMinutes();
        if (shiftTotalMinutes <= 0) shiftTotalMinutes = 480; // 默认8小时

        long occupiedMinutesToday = 0;
        for (StaffBooking b : safeBookings) {
            occupiedMinutesToday += Math.max(0, Duration.between(b.getStartTime(), b.getEndTime()).toMinutes());
        }
        int thisOrderMinutes = order.getDurationMinutesSnapshot() != null ? order.getDurationMinutesSnapshot() : 60;
        double lVal = Math.max(0.0, 1.0 - (double) (occupiedMinutesToday + thisOrderMinutes + travelMinutes) / (double) shiftTotalMinutes);
        BigDecimal metricL = BigDecimal.valueOf(lVal).setScale(4, RoundingMode.HALF_UP);
        result.setMetricL(metricL);

        // 4. 指标 F: 熟悉程度 (曾为该老人完成服务为1，否则为0)
        double fVal = hasFamiliarity ? 1.0 : 0.0;
        BigDecimal metricF = BigDecimal.valueOf(fVal).setScale(4, RoundingMode.HALF_UP);
        result.setMetricF(metricF);

        // 综合得分: Score = 0.35*D + 0.30*Q + 0.25*L + 0.10*F
        double totalScoreVal = weightDistance * dVal + weightQuality * qVal + weightWorkload * lVal + weightFamiliarity * fVal;
        BigDecimal totalScore = BigDecimal.valueOf(totalScoreVal).setScale(4, RoundingMode.HALF_UP);
        result.setTotalScore(totalScore);

        debugContext.put("distanceKm", distanceKm);
        debugContext.put("travelMinutes", travelMinutes);
        debugContext.put("metricD", metricD);
        debugContext.put("metricQ", metricQ);
        debugContext.put("metricL", metricL);
        debugContext.put("metricF", metricF);
        debugContext.put("totalScore", totalScore);
        recordContext(result, debugContext);

        return result;
    }

    private void recordContext(CandidateEvaluationResult result, Map<String, Object> debugContext) {
        try {
            result.setDetailsJson(objectMapper.writeValueAsString(debugContext));
        } catch (JsonProcessingException e) {
            result.setDetailsJson("{}");
        }
    }

    private static boolean validCoordinates(BigDecimal latitude, BigDecimal longitude) {
        return latitude != null && longitude != null
                && latitude.abs().compareTo(BigDecimal.valueOf(90)) <= 0
                && longitude.abs().compareTo(BigDecimal.valueOf(180)) <= 0;
    }
}
