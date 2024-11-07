package com.example.navermapapi.beaconModule.internal.floor;

import java.util.ArrayList;
import java.util.List;

public class FloorTransitionDetector {
    private static final long TRANSITION_TIMEOUT_MS = 5000;  // 전환 타임아웃
    private static final float ELEVATOR_THRESHOLD = 0.3f;    // 엘리베이터 감지 임계값
    private static final float STAIRS_THRESHOLD = 0.15f;     // 계단 감지 임계값

    private final PressureProcessor pressureProcessor;
    private final FloorManager floorManager;
    private final List<Float> pressureHistory;
    private FloorTransitionCallback callback;

    private TransitionState currentState = TransitionState.STABLE;
    private long transitionStartTime;
    private float startPressure;
    private float peakPressure;

    public FloorTransitionDetector(PressureProcessor pressureProcessor, FloorManager floorManager) {
        this.pressureProcessor = pressureProcessor;
        this.floorManager = floorManager;
        this.pressureHistory = new ArrayList<>();
        this.pressureProcessor.addCallback(this::onPressureChanged);
    }

    public void setCallback(FloorTransitionCallback callback) {
        this.callback = callback;
    }

    private void onPressureChanged(float pressure) {
        updatePressureHistory(pressure);

        switch (currentState) {
            case STABLE:
                if (detectTransitionStart(pressure)) {
                    startTransition(pressure);
                }
                break;

            case TRANSITIONING:
                if (isTransitionComplete(pressure)) {
                    completeTransition(pressure);
                } else if (isTransitionTimeout()) {
                    cancelTransition();
                } else {
                    updateTransition(pressure);
                }
                break;
        }
    }

    private void updatePressureHistory(float pressure) {
        pressureHistory.add(pressure);
        if (pressureHistory.size() > 20) {  // 최근 20개 샘플만 유지
            pressureHistory.remove(0);
        }
    }

    private boolean detectTransitionStart(float pressure) {
        if (pressureHistory.size() < 3) return false;

        float pressureChange = Math.abs(pressure - pressureHistory.get(pressureHistory.size() - 3));
        return pressureChange > STAIRS_THRESHOLD;
    }

    private void startTransition(float pressure) {
        currentState = TransitionState.TRANSITIONING;
        transitionStartTime = System.currentTimeMillis();
        startPressure = pressure;
        peakPressure = pressure;
    }

    private boolean isTransitionComplete(float pressure) {
        float totalChange = Math.abs(pressure - startPressure);
        return totalChange > ELEVATOR_THRESHOLD && isStablePressure();
    }

    private boolean isTransitionTimeout() {
        return System.currentTimeMillis() - transitionStartTime > TRANSITION_TIMEOUT_MS;
    }

    private void updateTransition(float pressure) {
        peakPressure = Math.abs(pressure - startPressure) > Math.abs(peakPressure - startPressure)
                ? pressure : peakPressure;
    }

    private void completeTransition(float endPressure) {
        TransitionType type = detectTransitionType();
        int floorChange = calculateFloorChange(startPressure, endPressure);

        if (callback != null) {
            FloorTransitionEvent event = new FloorTransitionEvent(
                    type,
                    floorChange,
                    calculateDuration(),
                    calculateSpeed(),
                    floorManager.getCurrentFloor()
            );
            callback.onFloorTransition(event);
        }

        resetTransition();
    }

    private void cancelTransition() {
        currentState = TransitionState.STABLE;
        // 필요한 경우 취소 이벤트 발생
    }

    private TransitionType detectTransitionType() {
        float maxChangeRate = calculateMaxPressureChangeRate();
        return maxChangeRate > ELEVATOR_THRESHOLD ?
                TransitionType.ELEVATOR : TransitionType.STAIRS;
    }

    private float calculateMaxPressureChangeRate() {
        float maxRate = 0;
        for (int i = 1; i < pressureHistory.size(); i++) {
            float rate = Math.abs(pressureHistory.get(i) - pressureHistory.get(i-1));
            maxRate = Math.max(maxRate, rate);
        }
        return maxRate;
    }

    private int calculateFloorChange(float startPressure, float endPressure) {
        float heightChange = calculateHeightChange(startPressure, endPressure);
        return Math.round(heightChange / 3.0f);  // 3m를 한 층으로 가정
    }

    private float calculateHeightChange(float startPressure, float endPressure) {
        // 기압고도 공식 사용
        return 44330 * (1 - (float)Math.pow(endPressure/startPressure, 1/5.255));
    }

    private boolean isStablePressure() {
        if (pressureHistory.size() < 3) return false;

        float recentAverage = calculateRecentAverage(3);
        float variance = calculateVariance(3, recentAverage);
        return variance < 0.01;  // 임계값
    }

    private float calculateRecentAverage(int samples) {
        float sum = 0;
        int count = Math.min(samples, pressureHistory.size());
        for (int i = pressureHistory.size() - count; i < pressureHistory.size(); i++) {
            sum += pressureHistory.get(i);
        }
        return sum / count;
    }

    private float calculateVariance(int samples, float average) {
        float sumSquares = 0;
        int count = Math.min(samples, pressureHistory.size());
        for (int i = pressureHistory.size() - count; i < pressureHistory.size(); i++) {
            float diff = pressureHistory.get(i) - average;
            sumSquares += diff * diff;
        }
        return sumSquares / count;
    }

    private long calculateDuration() {
        return System.currentTimeMillis() - transitionStartTime;
    }

    private float calculateSpeed() {
        float heightChange = calculateHeightChange(startPressure, peakPressure);
        long duration = calculateDuration();
        return Math.abs(heightChange) / (duration / 1000.0f);  // m/s
    }

    private void resetTransition() {
        currentState = TransitionState.STABLE;
        pressureHistory.clear();
    }

    private enum TransitionState {
        STABLE,
        TRANSITIONING
    }

    public enum TransitionType {
        STAIRS,
        ELEVATOR
    }

    public interface FloorTransitionCallback {
        void onFloorTransition(FloorTransitionEvent event);
    }
}