package com.example.navermapapi;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

public class CustomSensorManager implements SensorEventListener {
    private static final String TAG = "CustomSensorManager";

    private final SensorManager systemSensorManager;
    private final Sensor accelerometer;
    private final Sensor magnetometer;
    private final Sensor gyroscope;
    private final Sensor stepDetector;

    private final float[] accelerometerReading = new float[3];
    private final float[] magnetometerReading = new float[3];
    private final float[] gyroscopeReading = new float[3];

    private SensorDataListener sensorDataListener;

    public interface SensorDataListener {
        void onSensorDataChanged(float[] accelerometerData, float[] magnetometerData, float[] gyroscopeData);
        void onStepDetected();
    }

    public CustomSensorManager(Context context) {
        systemSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        accelerometer = systemSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        magnetometer = systemSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        gyroscope = systemSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        stepDetector = systemSensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);
    }

    public void setSensorDataListener(SensorDataListener listener) {
        this.sensorDataListener = listener;
    }

    public void registerListeners() {
        systemSensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
        systemSensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_GAME);
        systemSensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_GAME);
        systemSensorManager.registerListener(this, stepDetector, SensorManager.SENSOR_DELAY_NORMAL);
        Log.d(TAG, "Sensor listeners registered");
    }

    public void unregisterListeners() {
        systemSensorManager.unregisterListener(this);
        Log.d(TAG, "Sensor listeners unregistered");
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (sensorDataListener == null) return;

        switch (event.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:
                System.arraycopy(event.values, 0, accelerometerReading, 0, accelerometerReading.length);
                break;
            case Sensor.TYPE_MAGNETIC_FIELD:
                System.arraycopy(event.values, 0, magnetometerReading, 0, magnetometerReading.length);
                break;
            case Sensor.TYPE_GYROSCOPE:
                System.arraycopy(event.values, 0, gyroscopeReading, 0, gyroscopeReading.length);
                break;
            case Sensor.TYPE_STEP_DETECTOR:
                sensorDataListener.onStepDetected();
                return;
        }

        sensorDataListener.onSensorDataChanged(accelerometerReading, magnetometerReading, gyroscopeReading);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // 센서 정확도 변경 시 처리 (현재는 구현하지 않음)
    }

    public float[] getAccelerometerReading() {
        return accelerometerReading.clone();
    }

    public float[] getMagnetometerReading() {
        return magnetometerReading.clone();
    }

    public float[] getGyroscopeReading() {
        return gyroscopeReading.clone();
    }

    public static void getRotationMatrix(float[] rotationMatrix, Object o, float[] accelerometerReading, float[] magnetometerReading) {
        SensorManager.getRotationMatrix(rotationMatrix, null, accelerometerReading, magnetometerReading);
    }

    public static void getOrientation(float[] rotationMatrix, float[] orientation) {
        SensorManager.getOrientation(rotationMatrix, orientation);
    }
}