package com.example.navermapapi.beaconModule.internal.floor;

public class FloorDetector {
    private static final float FLOOR_HEIGHT_METERS = 3.0f;  // 기본 층고
    private static final float PRESSURE_THRESHOLD = 0.12f;  // 층 변화 감지 임계값

    private final PressureProcessor pressureProcessor;
    private FloorCallback callback;
    private int currentFloor = 1;
    private float baselinePressure;
    private float lastStablePressure;

    public FloorDetector(PressureProcessor pressureProcessor) {
        this.pressureProcessor = pressureProcessor;
        this.pressureProcessor.addCallback(this::onPressureChanged);
    }

    public void setCallback(FloorCallback callback) {
        this.callback = callback;
    }

    public void setCurrentFloor(int floor) {
        this.currentFloor = floor;
        this.baselinePressure = pressureProcessor.getCurrentPressure();
        this.lastStablePressure = baselinePressure;
    }

    private void onPressureChanged(float pressure) {
        if (!isValidPressure(pressure)) return;

        float heightChange = calculateHeightChange(pressure, baselinePressure);
        int estimatedFloor = calculateFloorFromHeight(heightChange);

        if (estimatedFloor != currentFloor && isFloorChangeValid(pressure)) {
            currentFloor = estimatedFloor;
            lastStablePressure = pressure;
            if (callback != null) {
                callback.onFloorChanged(currentFloor);
            }
        }
    }

    private boolean isValidPressure(float pressure) {
        return pressure > 900 && pressure < 1100;  // hPa 단위
    }

    private float calculateHeightChange(float currentPressure, float referencePressure) {
        // 기압차를 높이로 변환 (기압고도 공식 사용)
        return 44330 * (1 - (float)Math.pow(currentPressure/referencePressure, 1/5.255));
    }

    private int calculateFloorFromHeight(float heightChange) {
        // 높이 변화를 층수로 변환
        return Math.round(heightChange / FLOOR_HEIGHT_METERS) + 1;
    }

    private boolean isFloorChangeValid(float currentPressure) {
        // 급격한 변화 검증
        float pressureChange = Math.abs(currentPressure - lastStablePressure);
        return pressureChange >= PRESSURE_THRESHOLD;
    }

    public interface FloorCallback {
        void onFloorChanged(int floor);
    }
}