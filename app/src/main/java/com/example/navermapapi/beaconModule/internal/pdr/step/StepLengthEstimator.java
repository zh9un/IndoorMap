package com.example.navermapapi.beaconModule.internal.pdr.step;

public class StepLengthEstimator {
    private static final double MIN_STEP_LENGTH = 0.5; // 최소 보폭 (미터)
    private static final double MAX_STEP_LENGTH = 0.8; // 최대 보폭 (미터)
    private static final double K = 0.0004; // 보폭 추정 계수

    public double estimateStepLength(float acceleration) {
        // 가속도 기반 보폭 추정
        // SL = K * sqrt(a_max - a_min)
        double stepLength = K * Math.sqrt(acceleration);

        // 보폭 범위 제한
        stepLength = Math.max(MIN_STEP_LENGTH,
                Math.min(MAX_STEP_LENGTH, stepLength));

        return stepLength;
    }

    // 사용자 신장 기반 보폭 조정
    public void calibrateForHeight(double heightInCm) {
        // 사용자 신장에 따른 보폭 범위 조정
        double scaleFactor = heightInCm / 170.0; // 170cm 기준

        double newMinStepLength = MIN_STEP_LENGTH * scaleFactor;
        double newMaxStepLength = MAX_STEP_LENGTH * scaleFactor;

        // 보폭 범위 업데이트 로직 구현
    }
}