package com.example.navermapapi.beaconModule.internal.pdr.manager;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import java.util.ArrayList;
import java.util.List;

public class PdrSensorManager implements SensorEventListener {
    private final SensorManager systemSensorManager;
    private final List<SensorCallback> callbacks;
    private final float[] accelerometerData = new float[3];
    private final float[] gyroscopeData = new float[3];
    private final float[] magnetometerData = new float[3];

    public PdrSensorManager(Context context) {
        this.systemSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        this.callbacks = new ArrayList<>();
    }

    public void start() {
        // 가속도계
        registerSensor(Sensor.TYPE_ACCELEROMETER);
        // 자이로스코프
        registerSensor(Sensor.TYPE_GYROSCOPE);
        // 지자기계
        registerSensor(Sensor.TYPE_MAGNETIC_FIELD);
    }

    public void stop() {
        systemSensorManager.unregisterListener(this);
    }

    private void registerSensor(int sensorType) {
        Sensor sensor = systemSensorManager.getDefaultSensor(sensorType);
        if (sensor != null) {
            systemSensorManager.registerListener(this, sensor,
                    SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        switch (event.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:
                System.arraycopy(event.values, 0, accelerometerData, 0, 3);
                notifySensorUpdated(SensorType.ACCELEROMETER, accelerometerData);
                break;

            case Sensor.TYPE_GYROSCOPE:
                System.arraycopy(event.values, 0, gyroscopeData, 0, 3);
                notifySensorUpdated(SensorType.GYROSCOPE, gyroscopeData);
                break;

            case Sensor.TYPE_MAGNETIC_FIELD:
                System.arraycopy(event.values, 0, magnetometerData, 0, 3);
                notifySensorUpdated(SensorType.MAGNETOMETER, magnetometerData);
                break;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // 센서 정확도 변경 처리
    }

    public void addCallback(SensorCallback callback) {
        if (!callbacks.contains(callback)) {
            callbacks.add(callback);
        }
    }

    public void removeCallback(SensorCallback callback) {
        callbacks.remove(callback);
    }

    private void notifySensorUpdated(SensorType type, float[] values) {
        for (SensorCallback callback : callbacks) {
            callback.onSensorUpdated(type, values);
        }
    }

    public enum SensorType {
        ACCELEROMETER,
        GYROSCOPE,
        MAGNETOMETER
    }

    public interface SensorCallback {
        void onSensorUpdated(SensorType type, float[] values);
    }
}