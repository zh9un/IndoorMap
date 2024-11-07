package com.example.navermapapi.beaconModule.internal.floor;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import java.util.ArrayList;
import java.util.List;

public class FloorManager {
    private final Context context;
    private final PressureProcessor pressureProcessor;
    private final FloorTransitionDetector transitionDetector;
    private final List<FloorCallback> callbacks;
    private final SensorManager sensorManager;
    private final Sensor pressureSensor;

    private int currentFloor = 1;
    private boolean isRunning = false;

    public FloorManager(Context context) {
        this.context = context;
        this.callbacks = new ArrayList<>();

        // 센서 매니저 초기화
        this.sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        this.pressureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE);

        // 압력 처리기 및 전환 감지기 초기화
        this.pressureProcessor = new PressureProcessor();
        this.transitionDetector = new FloorTransitionDetector(pressureProcessor, this);

        initializeListeners();
    }

    private void initializeListeners() {
        transitionDetector.setCallback(event -> {
            if (event.isValid()) {
                handleFloorTransition(event);
            }
        });
    }

    public void start() {
        if (!isRunning && pressureSensor != null) {
            sensorManager.registerListener(
                    pressureProcessor,
                    pressureSensor,
                    SensorManager.SENSOR_DELAY_NORMAL
            );
            isRunning = true;
        }
    }

    public void stop() {
        if (isRunning) {
            sensorManager.unregisterListener(pressureProcessor);
            isRunning = false;
        }
    }

    private void handleFloorTransition(FloorTransitionEvent event) {
        int newFloor = event.getTargetFloor();
        if (newFloor != currentFloor) {
            currentFloor = newFloor;
            notifyFloorChanged(currentFloor, FloorChangeType.DETECTED);
            notifyFloorTransition(event);
        }
    }

    public void setInitialFloor(int floor) {
        this.currentFloor = floor;
        notifyFloorChanged(floor, FloorChangeType.INITIAL);
    }

    public int getCurrentFloor() {
        return currentFloor;
    }

    public void addCallback(FloorCallback callback) {
        if (!callbacks.contains(callback)) {
            callbacks.add(callback);
        }
    }

    public void removeCallback(FloorCallback callback) {
        callbacks.remove(callback);
    }

    private void notifyFloorChanged(int floor, FloorChangeType type) {
        for (FloorCallback callback : callbacks) {
            callback.onFloorChanged(floor, type);
        }
    }

    private void notifyFloorTransition(FloorTransitionEvent event) {
        for (FloorCallback callback : callbacks) {
            callback.onFloorTransition(event);
        }
    }

    public enum FloorChangeType {
        INITIAL,    // 초기 설정
        DETECTED,   // 감지됨
        CONFIRMED   // 확인됨
    }

    public interface FloorCallback {
        void onFloorChanged(int floor, FloorChangeType type);
        void onFloorTransition(FloorTransitionEvent event);
    }
}