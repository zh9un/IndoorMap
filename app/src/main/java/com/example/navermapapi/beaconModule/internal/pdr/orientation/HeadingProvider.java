package com.example.navermapapi.beaconModule.internal.pdr.orientation;

import com.example.navermapapi.beaconModule.internal.pdr.manager.PdrSensorManager;
import com.example.navermapapi.beaconModule.internal.pdr.manager.PdrSensorManager.SensorType;

public class HeadingProvider {
    private static final float ALPHA = 0.15f; // 로우패스 필터 계수
    private static final int HEADING_HISTORY_SIZE = 5;

    private final float[] magneticField = new float[3];
    private final float[] gravity = new float[3];
    private final float[] rotationMatrix = new float[9];
    private final float[] orientationAngles = new float[3];
    private final float[] headingHistory = new float[HEADING_HISTORY_SIZE];
    private int historyIndex = 0;

    private HeadingCallback callback;
    private float currentHeading = 0;
    private float magneticDeclination = 0; // 자기 편각

    public HeadingProvider(PdrSensorManager sensorManager) {
        // 초기화
        for (int i = 0; i < HEADING_HISTORY_SIZE; i++) {
            headingHistory[i] = 0;
        }
    }

    public void setHeadingCallback(HeadingCallback callback) {
        this.callback = callback;
    }

    public void setMagneticDeclination(float declination) {
        this.magneticDeclination = declination;
    }

    public void processMagnetometerData(float[] values) {
        // 자기장 데이터 필터링
        magneticField[0] = ALPHA * values[0] + (1 - ALPHA) * magneticField[0];
        magneticField[1] = ALPHA * values[1] + (1 - ALPHA) * magneticField[1];
        magneticField[2] = ALPHA * values[2] + (1 - ALPHA) * magneticField[2];

        calculateHeading();
    }

    public void processAccelerometerData(float[] values) {
        // 중력 데이터 필터링
        gravity[0] = ALPHA * values[0] + (1 - ALPHA) * gravity[0];
        gravity[1] = ALPHA * values[1] + (1 - ALPHA) * gravity[1];
        gravity[2] = ALPHA * values[2] + (1 - ALPHA) * gravity[2];

        calculateHeading();
    }

    private void calculateHeading() {
        if (android.hardware.SensorManager.getRotationMatrix(rotationMatrix, null, gravity, magneticField)) {
            android.hardware.SensorManager.getOrientation(rotationMatrix, orientationAngles);
            float rawHeading = (float) Math.toDegrees(orientationAngles[0]);

            // 자기 편각 보정
            rawHeading += magneticDeclination;

            // 0-360도 범위로 정규화
            while (rawHeading < 0) rawHeading += 360;
            while (rawHeading >= 360) rawHeading -= 360;

            // 이동 평균 필터 적용
            updateHeadingHistory(rawHeading);
            float smoothedHeading = calculateAverageHeading();

            if (Math.abs(smoothedHeading - currentHeading) > 1.0) {
                currentHeading = smoothedHeading;
                if (callback != null) {
                    callback.onHeadingChanged(currentHeading);
                }
            }
        }
    }

    private void updateHeadingHistory(float heading) {
        headingHistory[historyIndex] = heading;
        historyIndex = (historyIndex + 1) % HEADING_HISTORY_SIZE;
    }

    private float calculateAverageHeading() {
        float sinSum = 0;
        float cosSum = 0;

        for (float heading : headingHistory) {
            double radians = Math.toRadians(heading);
            sinSum += Math.sin(radians);
            cosSum += Math.cos(radians);
        }

        double averageRadians = Math.atan2(sinSum, cosSum);
        float averageDegrees = (float) Math.toDegrees(averageRadians);

        if (averageDegrees < 0) {
            averageDegrees += 360;
        }

        return averageDegrees;
    }

    public float getCurrentHeading() {
        return currentHeading;
    }

    public interface HeadingCallback {
        void onHeadingChanged(double heading);
    }
}