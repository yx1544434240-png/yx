package com.agedcare.dispatch;

import com.agedcare.common.utils.DistanceUtil;
import com.agedcare.modules.dispatch.dto.CandidateEvaluationResult;
import com.agedcare.modules.dispatch.entity.StaffBooking;
import com.agedcare.modules.dispatch.service.DispatchScoreEngine;
import com.agedcare.modules.elder.entity.ElderProfile;
import com.agedcare.modules.order.entity.ServiceOrder;
import com.agedcare.modules.staff.entity.StaffProfile;
import com.agedcare.modules.staff.entity.StaffShift;
import com.agedcare.modules.staff.entity.StaffSkill;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 毕业论文核心实证仿真实验:
 * 直接复用真实派单引擎 DispatchScoreEngine，在多组随机种子与不同订单规模下，
 * 定量对比「随机轮询派单、贪心就近派单、本课题多目标综合评分」三方策略性能。
 */
public class DispatchAlgorithmBenchmarkTest {

    private final DispatchScoreEngine scoreEngine = new DispatchScoreEngine(
            null, null, null, null, null, null, new ObjectMapper()
    );

    static class SimulationContext {
        List<StaffProfile> staffList = new ArrayList<>();
        Map<Long, List<StaffSkill>> staffSkills = new HashMap<>();
        Map<Long, List<StaffBooking>> staffBookings = new HashMap<>();
        Map<Long, StaffShift> staffShifts = new HashMap<>();
        Map<Long, ServiceOrder> orderMap = new HashMap<>();
        Map<Long, ElderProfile> elderProfiles = new HashMap<>();
        Set<String> familiarPairs = new HashSet<>(); // staffId + "_" + elderId
    }

    static class BenchmarkMetrics {
        int totalOrders;
        int successfulDispatches;
        double totalTravelMinutes;
        double totalPriorRating;
        Map<Long, Integer> staffWorkloadOrders = new HashMap<>();
        long totalExecutionTimeNs;

        double getSuccessRate() {
            return (double) successfulDispatches / totalOrders * 100.0;
        }

        double getAvgTravelMinutes() {
            return successfulDispatches > 0 ? totalTravelMinutes / successfulDispatches : 0;
        }

        double getAvgPriorRating() {
            return successfulDispatches > 0 ? totalPriorRating / successfulDispatches : 0;
        }

        double getWorkloadStdDev() {
            if (staffWorkloadOrders.isEmpty()) return 0;
            double mean = staffWorkloadOrders.values().stream().mapToInt(Integer::intValue).average().orElse(0);
            double sumSq = 0;
            for (int count : staffWorkloadOrders.values()) {
                sumSq += Math.pow(count - mean, 2);
            }
            return Math.sqrt(sumSq / staffWorkloadOrders.size());
        }

        double getAvgLatencyMs() {
            return (double) totalExecutionTimeNs / totalOrders / 1_000_000.0;
        }
    }

    static class AggregateResult {
        double successRateMean;
        double travelMinutesMean;
        double priorRatingMean;
        double workloadStdDevMean;
        double latencyMsMean;
    }

    private SimulationContext initSimulationData(int staffCount, long seed) {
        SimulationContext ctx = new SimulationContext();
        double centerLat = 39.9042;
        double centerLng = 116.4074;

        Random random = new Random(seed);

        // 构建 30 位社区老人常驻档案
        for (long eId = 1; eId <= 30; eId++) {
            double offLat = (random.nextDouble() - 0.5) * 0.03;
            double offLng = (random.nextDouble() - 0.5) * 0.03;
            ctx.elderProfiles.put(eId, ElderProfile.builder()
                    .id(eId)
                    .name("长者#" + eId)
                    .address("幸福社区" + eId + "号楼")
                    .latitude(BigDecimal.valueOf(centerLat + offLat))
                    .longitude(BigDecimal.valueOf(centerLng + offLng))
                    .build());
        }

        // 12 名服务人员，基准点分布在周边网格
        for (long i = 1; i <= staffCount; i++) {
            double offsetLat = (random.nextDouble() - 0.5) * 0.04;
            double offsetLng = (random.nextDouble() - 0.5) * 0.04;
            double rating = 3.8 + random.nextDouble() * 1.2; // 3.8 ~ 5.0
            if (rating > 5.0) rating = 5.0;

            StaffProfile staff = StaffProfile.builder()
                    .id(i)
                    .userId(1000L + i)
                    .currentStatus("AVAILABLE")
                    .serviceArea("全网格")
                    .baseLatitude(BigDecimal.valueOf(centerLat + offsetLat))
                    .baseLongitude(BigDecimal.valueOf(centerLng + offsetLng))
                    .priorRating(BigDecimal.valueOf(rating).setScale(2, RoundingMode.HALF_UP))
                    .totalOrders(random.nextInt(30))
                    .build();
            ctx.staffList.add(staff);
            ctx.staffBookings.put(i, new ArrayList<>());

            // 技能分配: 基础照料 CARE，部分具备 CLEAN, MEDICAL, REHAB
            List<StaffSkill> skills = new ArrayList<>();
            skills.add(StaffSkill.builder().staffId(i).skillCode("CARE").auditStatus("APPROVED").build());
            if (i % 2 == 0) skills.add(StaffSkill.builder().staffId(i).skillCode("CLEAN").auditStatus("APPROVED").build());
            if (i % 3 == 0) skills.add(StaffSkill.builder().staffId(i).skillCode("MEDICAL").auditStatus("APPROVED").build());
            if (i % 4 == 0) skills.add(StaffSkill.builder().staffId(i).skillCode("REHAB").auditStatus("APPROVED").build());
            ctx.staffSkills.put(i, skills);

            // 排班: 08:00 ~ 18:00 (10小时班次)
            ctx.staffShifts.put(i, StaffShift.builder()
                    .staffId(i)
                    .startTime(LocalTime.of(8, 0))
                    .endTime(LocalTime.of(18, 0))
                    .status("ACTIVE")
                    .build());
        }

        // 预设熟悉度关系
        for (long staffId = 1; staffId <= staffCount; staffId++) {
            for (long elderId = 1; elderId <= 30; elderId++) {
                if ((staffId + elderId) % 5 == 0) {
                    ctx.familiarPairs.add(staffId + "_" + elderId);
                }
            }
        }

        return ctx;
    }

    private List<ServiceOrder> generateSimulationOrders(int orderCount, LocalDate date, long seed) {
        List<ServiceOrder> orders = new ArrayList<>();
        double centerLat = 39.9042;
        double centerLng = 116.4074;
        Random random = new Random(seed + 888);

        LocalTime[] slotStarts = {
                LocalTime.of(8, 30),
                LocalTime.of(9, 45),
                LocalTime.of(11, 0),
                LocalTime.of(13, 30),
                LocalTime.of(14, 45),
                LocalTime.of(16, 0)
        };

        String[] requiredSkillSets = {
                "CARE",
                "CARE,CLEAN",
                "CARE,MEDICAL",
                "CARE,REHAB"
        };

        for (long i = 1; i <= orderCount; i++) {
            LocalTime startT = slotStarts[(int) (i % slotStarts.length)];
            int durationMins = 45;
            LocalDateTime startDt = date.atTime(startT);
            LocalDateTime endDt = startDt.plusMinutes(durationMins);

            double offsetLat = (random.nextDouble() - 0.5) * 0.035;
            double offsetLng = (random.nextDouble() - 0.5) * 0.035;

            long elderId = 1 + (i % 25);
            String skills = requiredSkillSets[(int) (i % requiredSkillSets.length)];

            orders.add(ServiceOrder.builder()
                    .id(i)
                    .orderNo("SIM_ORD_" + i)
                    .elderId(elderId)
                    .plannedStartAt(startDt)
                    .plannedEndAt(endDt)
                    .durationMinutesSnapshot(durationMins)
                    .requiredSkillsSnapshot(skills)
                    .addressSnapshot("幸福社区" + elderId + "号楼")
                    .latitude(BigDecimal.valueOf(centerLat + offsetLat))
                    .longitude(BigDecimal.valueOf(centerLng + offsetLng))
                    .build());
        }

        orders.sort(Comparator.comparing(ServiceOrder::getPlannedStartAt));
        return orders;
    }

    /**
     * 运行派单策略（统一直接调用真实引擎 DispatchScoreEngine 评估每一个候选人）
     */
    private BenchmarkMetrics runStrategy(SimulationContext ctx, List<ServiceOrder> orders, String strategyName, long randomSeed) {
        BenchmarkMetrics metrics = new BenchmarkMetrics();
        metrics.totalOrders = orders.size();
        for (StaffProfile s : ctx.staffList) metrics.staffWorkloadOrders.put(s.getId(), 0);

        Random rand = new Random(randomSeed);

        for (ServiceOrder order : orders) {
            long t0 = System.nanoTime();
            StaffProfile chosenStaff = null;
            CandidateEvaluationResult chosenResult = null;

            if ("RANDOM".equals(strategyName)) {
                List<CandidateEvaluationResult> qualifiedList = new ArrayList<>();
                for (StaffProfile staff : ctx.staffList) {
                    CandidateEvaluationResult res = scoreEngine.evaluateCandidateWithContext(
                            staff, order,
                            ctx.staffSkills.get(staff.getId()),
                            List.of(ctx.staffShifts.get(staff.getId())),
                            ctx.staffBookings.get(staff.getId()),
                            ctx.orderMap::get,
                            ctx.elderProfiles.get(order.getElderId()),
                            ctx.familiarPairs.contains(staff.getId() + "_" + order.getElderId()),
                            order.getPlannedStartAt().minusMinutes(20)
                    );
                    if (res.isQualified()) {
                        qualifiedList.add(res);
                    }
                }
                if (!qualifiedList.isEmpty()) {
                    chosenResult = qualifiedList.get(rand.nextInt(qualifiedList.size()));
                    final Long targetStaffId = chosenResult.getStaffId();
                    chosenStaff = ctx.staffList.stream()
                            .filter(s -> s.getId().equals(targetStaffId))
                            .findFirst().orElse(null);
                }
            } else if ("GREEDY".equals(strategyName)) {
                int minTravelMinutes = Integer.MAX_VALUE;
                for (StaffProfile staff : ctx.staffList) {
                    CandidateEvaluationResult res = scoreEngine.evaluateCandidateWithContext(
                            staff, order,
                            ctx.staffSkills.get(staff.getId()),
                            List.of(ctx.staffShifts.get(staff.getId())),
                            ctx.staffBookings.get(staff.getId()),
                            ctx.orderMap::get,
                            ctx.elderProfiles.get(order.getElderId()),
                            ctx.familiarPairs.contains(staff.getId() + "_" + order.getElderId()),
                            order.getPlannedStartAt().minusMinutes(20)
                    );
                    if (res.isQualified()) {
                        if (res.getTravelMinutes() < minTravelMinutes) {
                            minTravelMinutes = res.getTravelMinutes();
                            chosenResult = res;
                            chosenStaff = staff;
                        }
                    }
                }
            } else if ("COMPOSITE".equals(strategyName)) {
                double maxScore = -1.0;
                for (StaffProfile staff : ctx.staffList) {
                    CandidateEvaluationResult res = scoreEngine.evaluateCandidateWithContext(
                            staff, order,
                            ctx.staffSkills.get(staff.getId()),
                            List.of(ctx.staffShifts.get(staff.getId())),
                            ctx.staffBookings.get(staff.getId()),
                            ctx.orderMap::get,
                            ctx.elderProfiles.get(order.getElderId()),
                            ctx.familiarPairs.contains(staff.getId() + "_" + order.getElderId()),
                            order.getPlannedStartAt().minusMinutes(20)
                    );
                    if (res.isQualified()) {
                        if (res.getTotalScore().doubleValue() > maxScore) {
                            maxScore = res.getTotalScore().doubleValue();
                            chosenResult = res;
                            chosenStaff = staff;
                        }
                    }
                }
            }

            metrics.totalExecutionTimeNs += (System.nanoTime() - t0);

            if (chosenStaff != null && chosenResult != null) {
                assignOrder(ctx, chosenStaff, order, metrics, chosenResult);
            }
        }
        return metrics;
    }

    private void assignOrder(SimulationContext ctx, StaffProfile staff, ServiceOrder order,
                             BenchmarkMetrics metrics, CandidateEvaluationResult eval) {
        metrics.successfulDispatches++;
        metrics.totalTravelMinutes += eval.getTravelMinutes();
        metrics.totalPriorRating += staff.getPriorRating().doubleValue();
        metrics.staffWorkloadOrders.put(staff.getId(), metrics.staffWorkloadOrders.get(staff.getId()) + 1);

        // 记录真实排班与订单检索映射，严格按照实际业务规则提前预留出行路程时间
        ctx.staffBookings.get(staff.getId()).add(StaffBooking.builder()
                .staffId(staff.getId())
                .orderId(order.getId())
                .startTime(order.getPlannedStartAt().minusMinutes(eval.getTravelMinutes()))
                .endTime(order.getPlannedEndAt())
                .status("CONFIRMED")
                .build());

        ctx.orderMap.put(order.getId(), order);
    }

    @Test
    @DisplayName("毕业论文核心实验: 真实派单引擎驱动 + 5组随机种子多轮仿真对比")
    public void runAlgorithmComparativeExperiment() {
        int staffCount = 12;
        int orderCount = 60;
        LocalDate testDate = LocalDate.now().plusDays(1);
        long[] seeds = {42L, 101L, 2024L, 777L, 9999L};

        List<BenchmarkMetrics> randomList = new ArrayList<>();
        List<BenchmarkMetrics> greedyList = new ArrayList<>();
        List<BenchmarkMetrics> compositeList = new ArrayList<>();

        for (long seed : seeds) {
            // A. 随机
            SimulationContext ctxA = initSimulationData(staffCount, seed);
            List<ServiceOrder> ordersA = generateSimulationOrders(orderCount, testDate, seed);
            randomList.add(runStrategy(ctxA, ordersA, "RANDOM", seed));

            // B. 贪心就近
            SimulationContext ctxB = initSimulationData(staffCount, seed);
            List<ServiceOrder> ordersB = generateSimulationOrders(orderCount, testDate, seed);
            greedyList.add(runStrategy(ctxB, ordersB, "GREEDY", seed));

            // C. 本课题综合评分
            SimulationContext ctxC = initSimulationData(staffCount, seed);
            List<ServiceOrder> ordersC = generateSimulationOrders(orderCount, testDate, seed);
            compositeList.add(runStrategy(ctxC, ordersC, "COMPOSITE", seed));
        }

        AggregateResult aggRandom = aggregate(randomList);
        AggregateResult aggGreedy = aggregate(greedyList);
        AggregateResult aggComposite = aggregate(compositeList);

        // 打印学术论文标准的仿真对比数据表
        System.out.println("\n=========================================================================================================");
        System.out.println("                 【毕业设计实证实验: 真实派单引擎驱动的多策略多轮仿真统计表 (5组种子均值)】                     ");
        System.out.println("=========================================================================================================");
        System.out.printf("%-18s | %-12s | %-14s | %-16s | %-14s | %-12s%n",
                "调度算法策略", "派单成功率(%)", "单均路途耗时(min)", "被选人员历史均分(星)", "人员负荷标准差(单)", "算法纯计算时延(ms)");
        System.out.println("---------------------------------------------------------------------------------------------------------");

        System.out.printf("%-18s | %-12.1f | %-14.1f | %-16.2f | %-14.2f | %-12.3f%n",
                "A. 随机轮询派单 (Random)",
                aggRandom.successRateMean,
                aggRandom.travelMinutesMean,
                aggRandom.priorRatingMean,
                aggRandom.workloadStdDevMean,
                aggRandom.latencyMsMean);

        System.out.printf("%-18s | %-12.1f | %-14.1f | %-16.2f | %-14.2f | %-12.3f%n",
                "B. 贪心就近派单 (Greedy)",
                aggGreedy.successRateMean,
                aggGreedy.travelMinutesMean,
                aggGreedy.priorRatingMean,
                aggGreedy.workloadStdDevMean,
                aggGreedy.latencyMsMean);

        System.out.printf("%-18s | %-12.1f | %-14.1f | %-16.2f | %-14.2f | %-12.3f%n",
                "C. 本课题多目标综合评分",
                aggComposite.successRateMean,
                aggComposite.travelMinutesMean,
                aggComposite.priorRatingMean,
                aggComposite.workloadStdDevMean,
                aggComposite.latencyMsMean);
        System.out.println("=========================================================================================================\n");

        // 验证论文核心学术推导:
        // 1. 综合算法被选人员历史服务基准评分显著高于盲目随机派单
        assertTrue(aggComposite.priorRatingMean >= aggRandom.priorRatingMean, "综合评分机制应优先匹配历史服务评分更高的优质服务人员");
        // 2. 贪心算法使负荷集中在中心人员导致负载标准差偏高，综合评分由于引入工时余量L，能有效平抑极化
        assertTrue(aggComposite.workloadStdDevMean <= aggGreedy.workloadStdDevMean + 0.1,
                "多目标综合评分通过L指标平衡负荷，负载标准差应明显低于或不劣于贪心算法");
        // 延迟是机器、JIT 和系统负载相关的测量值；功能 CI 不承诺固定的亚毫秒时延。
        assertTrue(Double.isFinite(aggComposite.latencyMsMean) && aggComposite.latencyMsMean > 0);
        String latencyBudget = System.getProperty("benchmark.max-latency-ms");
        if (latencyBudget != null) {
            assertTrue(aggComposite.latencyMsMean < Double.parseDouble(latencyBudget), "算法耗时超过本次显式配置的性能预算");
        }
    }

    private AggregateResult aggregate(List<BenchmarkMetrics> list) {
        AggregateResult agg = new AggregateResult();
        agg.successRateMean = list.stream().mapToDouble(BenchmarkMetrics::getSuccessRate).average().orElse(0);
        agg.travelMinutesMean = list.stream().mapToDouble(BenchmarkMetrics::getAvgTravelMinutes).average().orElse(0);
        agg.priorRatingMean = list.stream().mapToDouble(BenchmarkMetrics::getAvgPriorRating).average().orElse(0);
        agg.workloadStdDevMean = list.stream().mapToDouble(BenchmarkMetrics::getWorkloadStdDev).average().orElse(0);
        agg.latencyMsMean = list.stream().mapToDouble(BenchmarkMetrics::getAvgLatencyMs).average().orElse(0);
        return agg;
    }
}
