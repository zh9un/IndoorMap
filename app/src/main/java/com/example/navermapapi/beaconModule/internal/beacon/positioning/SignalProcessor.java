package com.example.navermapapi.beaconModule.internal.beacon.positioning;

import com.example.navermapapi.beaconModule.utils.SignalUtils;

public class SignalProcessor {
    private static final double RSSI_AT_1M = -59.0; // 1m 거리에서의 RSSI 기준값
    private static final double PATH_LOSS_PARAMETER = 2.0; // 경로 손실 계수
    private static final double KALMAN_Q = 0.125; // 프로세스 노이즈
    private static final double KALMAN_R = 0.5;   // 측정 노이즈

    private double kalmanX = 0;  // 칼만 필터 상태
    private double kalmanP = 1;  // 오차 공분산

    public double processRssi(int rawRssi) {
        // 노이즈 제거
        int cleanRssi = SignalUtils.removeNoise(rawRssi);

        // 칼만 필터 적용
        return applyKalmanFilter(cleanRssi);
    }

    private double applyKalmanFilter(double measurement) {
        // 예측 단계
        double predictedP = kalmanP + KALMAN_Q;

        // 업데이트 단계
        double kalmanGain = predictedP / (predictedP + KALMAN_R);
        kalmanX = kalmanX + kalmanGain * (measurement - kalmanX);
        kalmanP = (1 - kalmanGain) * predictedP;

        return kalmanX;
    }

    public double calculateDistance(double processedRssi) {
        double ratio = (RSSI_AT_1M - processedRssi) / (10 * PATH_LOSS_PARAMETER);
        double distance = Math.pow(10, ratio);

        // 거리 보정 (실험적으로 결정된 보정 계수)
        return calibrateDistance(distance);
    }

    private double calibrateDistance(double rawDistance) {
        // 실험적으로 결정된 보정 곡선
        // 여기서는 간단한 선형 보정을 사용
        double calibrationFactor = 1.2; // 보정 계수
        double minimumDistance = 0.3;   // 최소 거리

        double calibratedDistance = rawDistance * calibrationFactor;
        return Math.max(calibratedDistance, minimumDistance);
    }
}