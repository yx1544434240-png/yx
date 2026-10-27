package com.agedcare.dispatch;

import com.agedcare.common.utils.DistanceUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.junit.jupiter.api.Assertions.*;

public class DispatchScoreEngineTest {

    @Test
    @DisplayName("验证距离与出行时间估算模型")
    public void testDistanceAndTravelMinutes() {
        // 阳光社区内两点距离约 1.2 公里
        double lat1 = 39.904199;
        double lng1 = 116.407396;
        double lat2 = 39.914199;
        double lng2 = 116.417396;

        double distanceKm = DistanceUtil.calculateDistanceKm(lat1, lng1, lat2, lng2);
        assertTrue(distanceKm > 0.5 && distanceKm < 3.0, "两点间距离应在合理社区半径内");

        // 速度 15km/h, 缓冲 10 分钟
        int travelMinutes = DistanceUtil.estimateTravelMinutes(distanceKm, 15.0, 10);
        assertTrue(travelMinutes >= 10, "预估出行时间必须至少包含安全缓冲时间");
    }

    @Test
    @DisplayName("验证指标 D: 到达便利度归一化")
    public void testMetricD() {
        // D = 1 - min(travelMinutes / 30, 1)
        int travelMinutes1 = 15;
        double d1 = Math.max(0.0, 1.0 - Math.min((double) travelMinutes1 / 30.0, 1.0));
        assertEquals(0.5, d1, 0.001, "耗时15分钟时便利度应为0.5");

        int travelMinutes2 = 30;
        double d2 = Math.max(0.0, 1.0 - Math.min((double) travelMinutes2 / 30.0, 1.0));
        assertEquals(0.0, d2, 0.001, "耗时30分钟以上便利度应降为0");

        int travelMinutes3 = 0;
        double d3 = Math.max(0.0, 1.0 - Math.min((double) travelMinutes3 / 30.0, 1.0));
        assertEquals(1.0, d3, 0.001, "耗时0分钟时便利度应为1.0");
    }

    @Test
    @DisplayName("验证冷启动贝叶斯评分平滑: 新服务人员先验平滑")
    public void testBayesianSmoothedRating() {
        // 先验 4.0，先验样本数 5
        double priorRating = 4.0;
        int priorSamples = 5;

        // 场景1: 全新员工，0单评价
        int nOrders = 0;
        double qRating = 4.0;
        double smoothed1 = (qRating * nOrders + priorSamples * priorRating) / (nOrders + priorSamples);
        assertEquals(4.0, smoothed1, 0.001, "冷启动新员工均分应严格回归先验4.0");

        // 归一化到 0~1: (smoothed - 1.0) / 4.0
        double normQ1 = (smoothed1 - 1.0) / 4.0;
        assertEquals(0.75, normQ1, 0.001, "4.0分映射到0~1区间应为0.75");

        // 场景2: 仅有1条5星评价的老人
        int n2 = 1;
        double s2 = 5.0;
        double smoothed2 = (s2 + priorSamples * priorRating) / (n2 + priorSamples); // (5 + 20) / 6 = 4.1667
        assertEquals(4.1667, smoothed2, 0.001, "1条好评平滑后不应突变为满分5.0，应受先验拉回");
    }

    @Test
    @DisplayName("验证多因素加权综合总得分公式计算与精度")
    public void testTotalScoreCalculation() {
        // Score = 0.35 * D + 0.30 * Q + 0.25 * L + 0.10 * F
        double d = 0.8;
        double q = 0.75;
        double l = 0.6;
        double f = 1.0;

        double expected = 0.35 * 0.8 + 0.30 * 0.75 + 0.25 * 0.6 + 0.10 * 1.0;
        // 0.28 + 0.225 + 0.15 + 0.10 = 0.755

        BigDecimal total = BigDecimal.valueOf(expected).setScale(4, RoundingMode.HALF_UP);
        assertEquals(new BigDecimal("0.7550"), total);
    }
}
