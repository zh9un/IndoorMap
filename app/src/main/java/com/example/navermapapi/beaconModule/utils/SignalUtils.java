// SignalUtils.java
package com.example.navermapapi.beaconModule.utils;

import java.util.List;

public class SignalUtils {
    private static final double ALPHA = 0.2; // EMA 가중치
    private static final int MIN_RSSI = -100;
    private static final int MAX_RSSI = -20;

    // RSSI 필터링 (지수이동평균)
    public static double filterRssi(double previousRssi, int currentRssi) {
        if (previousRssi == 0) {
            return currentRssi;
        }
        return ALPHA * currentRssi + (1 - ALPHA) * previousRssi;
    }

    // RSSI 유효성 검사
    public static boolean isValidRssi(int rssi) {
        return rssi >= MIN_RSSI && rssi <= MAX_RSSI;
    }

    // 신호 강도 품질 평가 (0~1)
    public static double calculateSignalQuality(int rssi) {
        if (!isValidRssi(rssi)) return 0.0;
        return (double)(rssi - MIN_RSSI) / (MAX_RSSI - MIN_RSSI);
    }

    // 이동 평균 계산
    public static double calculateMovingAverage(List<Integer> rssiValues) {
        if (rssiValues == null || rssiValues.isEmpty()) {
            return 0.0;
        }
        return rssiValues.stream()
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0.0);
    }

    // 노이즈 제거
    public static int removeNoise(int rssi) {
        return Math.min(Math.max(rssi, MIN_RSSI), MAX_RSSI);
    }
}