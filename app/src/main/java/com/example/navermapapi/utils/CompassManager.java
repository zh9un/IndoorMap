package com.example.navermapapi.utils;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;
import androidx.annotation.NonNull;
import java.util.LinkedList;
import java.util.Queue;

public class CompassManager implements SensorEventListener {
    private static final String TAG = "CompassManager";
    private static final float ALPHA = 0.15f;
    private static final double MIN_CHANGE_THRESHOLD = 2.0;
    private static final int SAMPLE_SIZE = 5;
    private static final int SENSOR_DELAY = SensorManager.SENSOR_DELAY_GAME;
    private static final float GRAVITY_THRESHOLD = 0.5f;
    private static final float STABLE_VARIANCE_THRESHOLD = 2.0f;

    private final SensorManager sensorManager;
    private final float[] accelerometerReading = new float[3];
    private final float[] magnetometerReading = new float[3];
    private final float[] rotationMatrix = new float[9];
    private final float[] orientationAngles = new float[3];
    private final Queue<Float> azimuthQueue = new LinkedList<>();

    private float magneticDeclination = -7.5f;  // 서울 기준 자기 편차
    private boolean hasInitialReading = false;
    private float lastCompassAngle = 0f;
    private CompassListener compassListener;
    private boolean isStable = false;
    private long lastUpdateTime = 0;
    private static final long MIN_UPDATE_INTERVAL = 100; // 밀리초

    public interface CompassListener {
        void onCompassChanged(float azimuth, float pitch, float roll);
    }

    public CompassManager(@NonNull Context context) {
        this.sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        initializeSensors();
    }

    private void initializeSensors() {
        Sensor accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        Sensor magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

        if (accelerometer != null && magnetometer != null) {
            sensorManager.registerListener(this, accelerometer, SENSOR_DELAY);
            sensorManager.registerListener(this, magnetometer, SENSOR_DELAY);
            Log.d(TAG, "Sensors initialized successfully");
        } else {
            Log.e(TAG, "Required sensors not available");
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        boolean dataUpdated = false;

        switch (event.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:
                System.arraycopy(event.values, 0, accelerometerReading, 0, 3);
                dataUpdated = true;
                break;
            case Sensor.TYPE_MAGNETIC_FIELD:
                System.arraycopy(event.values, 0, magnetometerReading, 0, 3);
                dataUpdated = true;
                break;
        }

        if (dataUpdated && shouldUpdateOrientation()) {
            updateOrientation();
        }
    }

    private boolean shouldUpdateOrientation() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastUpdateTime < MIN_UPDATE_INTERVAL) {
            return false;
        }
        lastUpdateTime = currentTime;
        return isDeviceStable();
    }

    private boolean isDeviceStable() {
        float totalAcceleration = (float) Math.sqrt(
                accelerometerReading[0] * accelerometerReading[0] +
                        accelerometerReading[1] * accelerometerReading[1] +
                        accelerometerReading[2] * accelerometerReading[2]
        );

        isStable = Math.abs(totalAcceleration - SensorManager.GRAVITY_EARTH) < GRAVITY_THRESHOLD;
        return isStable;
    }

    private void updateOrientation() {
        if (!SensorManager.getRotationMatrix(rotationMatrix, null, accelerometerReading, magnetometerReading)) {
            return;
        }

        SensorManager.getOrientation(rotationMatrix, orientationAngles);
        float azimuth = (float) Math.toDegrees(orientationAngles[0]) + magneticDeclination;
        float pitch = (float) Math.toDegrees(orientationAngles[1]);
        float roll = (float) Math.toDegrees(orientationAngles[2]);

        // 방위각을 0-360 범위로 정규화
        azimuth = (azimuth + 360) % 360;

        // 이동 평균 계산을 위한 큐 업데이트
        azimuthQueue.offer(azimuth);
        if (azimuthQueue.size() > SAMPLE_SIZE) {
            azimuthQueue.poll();
        }

        if (!hasInitialReading) {
            lastCompassAngle = azimuth;
            hasInitialReading = true;
            return;
        }

        // 방위각 안정성 검사
        if (isAzimuthStable()) {
            float avgAzimuth = calculateAverageAzimuth();
            float smoothedAzimuth = smoothAzimuth(avgAzimuth);

            if (Math.abs(smoothedAzimuth - lastCompassAngle) >= MIN_CHANGE_THRESHOLD) {
                lastCompassAngle = smoothedAzimuth;
                if (compassListener != null) {
                    compassListener.onCompassChanged(smoothedAzimuth, pitch, roll);
                }
            }
        }
    }

    private boolean isAzimuthStable() {
        if (azimuthQueue.size() < SAMPLE_SIZE) {
            return false;
        }

        float mean = 0;
        for (float azimuth : azimuthQueue) {
            mean += azimuth;
        }
        mean /= azimuthQueue.size();

        float variance = 0;
        for (float azimuth : azimuthQueue) {
            float diff = azimuth - mean;
            if (Math.abs(diff) > 180) {
                diff = 360 - Math.abs(diff);
            }
            variance += diff * diff;
        }
        variance /= azimuthQueue.size();

        return variance < STABLE_VARIANCE_THRESHOLD;
    }

    private float calculateAverageAzimuth() {
        float sinSum = 0;
        float cosSum = 0;

        for (float azimuth : azimuthQueue) {
            double rad = Math.toRadians(azimuth);
            sinSum += Math.sin(rad);
            cosSum += Math.cos(rad);
        }

        double avgRad = Math.atan2(sinSum / azimuthQueue.size(), cosSum / azimuthQueue.size());
        return (float) ((Math.toDegrees(avgRad) + 360) % 360);
    }

    private float smoothAzimuth(float newAzimuth) {
        float diff = newAzimuth - lastCompassAngle;
        if (Math.abs(diff) > 180) {
            if (diff > 0) {
                diff -= 360;
            } else {
                diff += 360;
            }
        }

        return (lastCompassAngle + ALPHA * diff + 360) % 360;
    }

    public void setCompassListener(CompassListener listener) {
        this.compassListener = listener;
    }

    public float getCurrentAzimuth() {
        return lastCompassAngle;
    }

    public void start() {
        initializeSensors();
        hasInitialReading = false;
        azimuthQueue.clear();
    }

    public void stop() {
        sensorManager.unregisterListener(this);
        hasInitialReading = false;
        azimuthQueue.clear();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        if (sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            if (accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) {
                Log.w(TAG, "Magnetic field sensor is unreliable");
            }
        }
    }
}