package com.example.navermapapi.beaconModule.internal.floor;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import java.util.ArrayList;
import java.util.List;

public class PressureProcessor implements SensorEventListener {
    private static final int WINDOW_SIZE = 10;  // 이동 평균 윈도우 크기
    private static final float ALPHA = 0.1f;    // EMA 필터 계수

    private final List<Float> pressureWindow;
    private final List<PressureCallback> callbacks;
    private float filteredPressure;
    private float currentPressure;

    public PressureProcessor() {
        this.pressureWindow = new ArrayList<>(WINDOW_SIZE);
        this.callbacks = new ArrayList<>();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_PRESSURE) {
            processPressureData(event.values[0]);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // 센서 정확도 변경 처리가 필요한 경우 여기에 구현
    }

    private void processPressureData(float pressure) {
        // EMA 필터 적용
        if (filteredPressure == 0) {
            filteredPressure = pressure;
        } else {
            filteredPressure = ALPHA * pressure + (1 - ALPHA) * filteredPressure;
        }

        // 이동 평균 윈도우 업데이트
        updatePressureWindow(filteredPressure);

        // 최종 압력값 계산
        float processedPressure = calculateAveragePressure();
        if (processedPressure != currentPressure) {
            currentPressure = processedPressure;
            notifyPressureChanged(currentPressure);
        }
    }

    private void updatePressureWindow(float pressure) {
        if (pressureWindow.size() >= WINDOW_SIZE) {
            pressureWindow.remove(0);
        }
        pressureWindow.add(pressure);
    }

    private float calculateAveragePressure() {
        if (pressureWindow.isEmpty()) return 0;

        float sum = 0;
        for (float pressure : pressureWindow) {
            sum += pressure;
        }
        return sum / pressureWindow.size();
    }

    public float getCurrentPressure() {
        return currentPressure;
    }

    public void addCallback(PressureCallback callback) {
        if (!callbacks.contains(callback)) {
            callbacks.add(callback);
        }
    }

    public void removeCallback(PressureCallback callback) {
        callbacks.remove(callback);
    }

    private void notifyPressureChanged(float pressure) {
        for (PressureCallback callback : callbacks) {
            callback.onPressureChanged(pressure);
        }
    }

    public interface PressureCallback {
        void onPressureChanged(float pressure);
    }
}