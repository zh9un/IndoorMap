package com.example.navermapapi.coreModule.utils.filter;

import androidx.annotation.NonNull;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Queue;

/**
 * 센서 데이터의 노이즈를 제거하는 필터 클래스
 * 이동 평균과 칼만 필터 기반의 노이즈 제거를 수행
 */
public class NoiseFilter {
    private static final int DEFAULT_WINDOW_SIZE = 10;
    private static final double DEFAULT_OUTLIER_THRESHOLD = 2.0;

    private final Queue<Double> window;
    private final int windowSize;
    private final double outlierThreshold;

    private double sum;
    private double lastValue;
    private boolean isInitialized;

    // 칼만 필터 파라미터
    private double estimatedValue;
    private double errorCovariance;
    private static final double PROCESS_NOISE = 0.001;
    private static final double MEASUREMENT_NOISE = 0.1;
    private static final double INITIAL_ESTIMATE = 0.0;
    private static final double INITIAL_ERROR_COVARIANCE = 1.0;

    /**
     * 기본 설정으로 NoiseFilter 인스턴스 생성
     */
    public NoiseFilter() {
        this(DEFAULT_WINDOW_SIZE, DEFAULT_OUTLIER_THRESHOLD);
    }

    /**
     * 사용자 정의 설정으로 NoiseFilter 인스턴스 생성
     * @param windowSize 이동 평균 윈도우 크기
     * @param outlierThreshold 이상치 판단 임계값 (표준편차의 배수)
     */
    public NoiseFilter(int windowSize, double outlierThreshold) {
        if (windowSize <= 0) {
            throw new IllegalArgumentException("Window size must be positive");
        }
        if (outlierThreshold <= 0) {
            throw new IllegalArgumentException("Outlier threshold must be positive");
        }

        this.windowSize = windowSize;
        this.outlierThreshold = outlierThreshold;
        this.window = new LinkedList<>();
        this.sum = 0.0;
        this.isInitialized = false;

        // 칼만 필터 초기화
        this.estimatedValue = INITIAL_ESTIMATE;
        this.errorCovariance = INITIAL_ERROR_COVARIANCE;
    }

    /**
     * 새로운 데이터 포인트를 필터링
     * @param value 필터링할 새로운 값
     * @return 필터링된 값
     */
    public synchronized double filter(double value) {
        if (!isInitialized) {
            initializeFilter(value);
            return value;
        }

        if (isOutlier(value)) {
            return lastValue;
        }

        // 칼만 필터 적용
        double kalmanValue = applyKalmanFilter(value);

        // 이동 평균 업데이트
        updateMovingAverage(kalmanValue);

        // 최종 필터링 값 계산 (칼만 필터와 이동 평균의 가중 평균)
        double filteredValue = calculateFilteredValue(kalmanValue);

        lastValue = filteredValue;
        return filteredValue;
    }

    /**
     * 필터 초기화
     * @param initialValue 초기값
     */
    private void initializeFilter(double initialValue) {
        lastValue = initialValue;
        window.offer(initialValue);
        sum = initialValue;
        isInitialized = true;
        estimatedValue = initialValue;
    }

    /**
     * 이상치 여부 판단
     * @param value 검사할 값
     * @return 이상치 여부
     */
    private boolean isOutlier(double value) {
        if (window.size() < 2) {
            return false;
        }

        double mean = sum / window.size();
        double variance = window.stream()
                .mapToDouble(x -> Math.pow(x - mean, 2))
                .average()
                .orElse(0.0);
        double stdDev = Math.sqrt(variance);

        return Math.abs(value - mean) > stdDev * outlierThreshold;
    }

    /**
     * 칼만 필터 적용
     * @param measurement 측정값
     * @return 칼만 필터 적용된 값
     */
    private double applyKalmanFilter(double measurement) {
        // 예측 단계
        double predictedErrorCovariance = errorCovariance + PROCESS_NOISE;

        // 업데이트 단계
        double kalmanGain = predictedErrorCovariance / (predictedErrorCovariance + MEASUREMENT_NOISE);
        estimatedValue = estimatedValue + kalmanGain * (measurement - estimatedValue);
        errorCovariance = (1 - kalmanGain) * predictedErrorCovariance;

        return estimatedValue;
    }

    /**
     * 이동 평균 업데이트
     * @param value 새로운 값
     */
    private void updateMovingAverage(double value) {
        if (window.size() >= windowSize) {
            sum -= window.poll();
        }
        window.offer(value);
        sum += value;
    }

    /**
     * 최종 필터링 값 계산
     * @param kalmanValue 칼만 필터 값
     * @return 최종 필터링된 값
     */
    private double calculateFilteredValue(double kalmanValue) {
        double movingAverage = sum / window.size();
        double alpha = 0.7; // 칼만 필터와 이동 평균의 가중치
        return alpha * kalmanValue + (1 - alpha) * movingAverage;
    }

    /**
     * 필터 초기화
     */
    public synchronized void reset() {
        window.clear();
        sum = 0.0;
        isInitialized = false;
        estimatedValue = INITIAL_ESTIMATE;
        errorCovariance = INITIAL_ERROR_COVARIANCE;
    }

    /**
     * 현재 윈도우에 있는 필터링된 값들 반환
     * @return 필터링된 값들의 배열
     */
    @NonNull
    public double[] getFilteredValues() {
        return window.stream().mapToDouble(Double::doubleValue).toArray();
    }

    /**
     * 가장 최근 필터링된 값 반환
     * @return 최근 필터링된 값
     */
    public double getLastValue() {
        return lastValue;
    }

    /**
     * 현재 윈도우 크기 반환
     * @return 윈도우 크기
     */
    public int getWindowSize() {
        return windowSize;
    }

    /**
     * 현재 윈도우의 평균값 반환
     * @return 평균값
     */
    public double getMean() {
        return window.isEmpty() ? 0.0 : sum / window.size();
    }
}