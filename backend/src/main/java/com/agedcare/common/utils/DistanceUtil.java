package com.agedcare.common.utils;

import java.math.BigDecimal;

public class DistanceUtil {
    private static final double EARTH_RADIUS = 6371.0; // 地球半径(千米)

    /**
     * 计算两点间的球面大圆直线距离(公里)
     */
    public static double calculateDistanceKm(double lat1, double lng1, double lat2, double lng2) {
        double radLat1 = Math.toRadians(lat1);
        double radLat2 = Math.toRadians(lat2);
        double a = radLat1 - radLat2;
        double b = Math.toRadians(lng1) - Math.toRadians(lng2);

        double s = 2 * Math.asin(Math.sqrt(
                Math.pow(Math.sin(a / 2), 2) +
                        Math.cos(radLat1) * Math.cos(radLat2) * Math.pow(Math.sin(b / 2), 2)
        ));
        return s * EARTH_RADIUS;
    }

    public static double calculateDistanceKm(BigDecimal lat1, BigDecimal lng1, BigDecimal lat2, BigDecimal lng2) {
        if (lat1 == null || lng1 == null || lat2 == null || lng2 == null) {
            return 1.0; // 默认同社区1公里
        }
        return calculateDistanceKm(lat1.doubleValue(), lng1.doubleValue(), lat2.doubleValue(), lng2.doubleValue());
    }

    /**
     * 根据路程、巡航速度(默认15km/h电动车/步行)与安全缓冲时间(默认5~10分钟)估算耗时(分钟)
     */
    public static int estimateTravelMinutes(double distanceKm, double speedKmPerHour, int bufferMinutes) {
        if (speedKmPerHour <= 0) {
            speedKmPerHour = 15.0;
        }
        double hours = distanceKm / speedKmPerHour;
        int minutes = (int) Math.ceil(hours * 60.0);
        return Math.max(minutes + bufferMinutes, bufferMinutes);
    }
}
