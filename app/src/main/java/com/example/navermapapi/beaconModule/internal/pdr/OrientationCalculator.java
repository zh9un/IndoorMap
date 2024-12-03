package com.example.navermapapi.beaconModule.internal.pdr;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;
import androidx.annotation.NonNull;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import com.example.navermapapi.coreModule.utils.filter.NoiseFilter;
import com.example.navermapapi.utils.CompassManager;

public class OrientationCalculator implements SensorEventListener {
    private static final String TAG = "OrientationCalculator";
    private static final float ALPHA = 0.15f;
    private static final int SENSOR_DELAY = SensorManager.SENSOR_DELAY_GAME;
    private static final float RAD_TO_DEG = (float) (180.0f / Math.PI);
    private static final double THRESHOLD_ANGLE = 2.0;
    private static final int SAMPLE_SIZE = 5;
    private static final float STABLE_VARIANCE_THRESHOLD = 2.0f;
    private static final long MIN_UPDATE_INTERVAL = 100; // 밀리초

    private static final float MIN_MAGNETIC_FIELD = 25.0f;
    private static final float MAX_MAGNETIC_FIELD = 65.0f;
    private static final float GRAVITY_THRESHOLD = 0.5f;

    private final SensorManager sensorManager;
    private final NoiseFilter orientationFilter;
    private final List<OrientationCallback> callbacks;
    private final CompassManager compassManager;
    private final Queue<Float> azimuthQueue;

    private final float[] accelerometerReading = new float[3];
    private final float[] magnetometerReading = new float[3];
    private final float[] rotationMatrix = new float[9];
    private final float[] orientationAngles = new float[3];

    private float previousAzimuth = 0f;
    private boolean isCalibrated = false;
    private boolean useCompassManager = true;
    private boolean isStable = false;
    private long lastUpdateTime = 0;

    public OrientationCalculator(@NonNull Context context) {
        this.sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        this.orientationFilter = new NoiseFilter(5, 1.5);
        this.callbacks = new ArrayList<>();
        this.compassManager = new CompassManager(context);
        this.azimuthQueue = new LinkedList<>();

        setupCompassCallback();
        initializeSensors();
    }

    private void setupCompassCallback() {
        compassManager.setCompassListener((azimuth, pitch, roll) -> {
            if (useCompassManager && isCalibrated) {
                processNewAzimuth(azimuth);
            }
        });
    }

    private void initializeSensors() {
        try {
            Sensor accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            Sensor magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

            if (accelerometer != null && magnetometer != null) {
                sensorManager.registerListener(this, accelerometer, SENSOR_DELAY);
                sensorManager.registerListener(this, magnetometer, SENSOR_DELAY);
                Log.i(TAG, "Orientation sensors initialized");
            } else {
                Log.e(TAG, "Required sensors not available");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error initializing sensors", e);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (!useCompassManager) {
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
        if (!useCompassManager) {
            boolean rotationOK = SensorManager.getRotationMatrix(
                    rotationMatrix,
                    null,
                    accelerometerReading,
                    magnetometerReading
            );

            if (!rotationOK) return;

            SensorManager.getOrientation(rotationMatrix, orientationAngles);
            float azimuth = (float) Math.toDegrees(orientationAngles[0]);
            if (azimuth < 0) {
                azimuth += 360;
            }

            processNewAzimuth(azimuth);
        }
    }

    private void processNewAzimuth(float azimuth) {
        azimuthQueue.offer(azimuth);
        if (azimuthQueue.size() > SAMPLE_SIZE) {
            azimuthQueue.poll();
        }

        if (isAzimuthStable()) {
            float avgAzimuth = calculateAverageAzimuth();
            float filteredAzimuth = (float) orientationFilter.filter(avgAzimuth);

            if (shouldUpdateAzimuth(filteredAzimuth)) {
                handleOrientationChange(filteredAzimuth);
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

    private boolean shouldUpdateAzimuth(float newAzimuth) {
        float diff = Math.abs(newAzimuth - previousAzimuth);
        if (diff > 180) {
            diff = 360 - diff;
        }
        return diff >= THRESHOLD_ANGLE;
    }

    private void handleOrientationChange(float azimuth) {
        // 급격한 변화 감지 및 보정
        float diff = azimuth - previousAzimuth;
        if (Math.abs(diff) > 180) {
            if (diff > 0) {
                diff -= 360;
            } else {
                diff += 360;
            }
        }

        // 부드러운 전환 적용
        float smoothedAzimuth = previousAzimuth + ALPHA * diff;
        smoothedAzimuth = (smoothedAzimuth + 360) % 360;

        // 자기장 간섭 확인
        if (!isMagneticInterference()) {
            previousAzimuth = smoothedAzimuth;
            notifyOrientationChanged(smoothedAzimuth);
        } else {
            handleMagneticInterference(azimuth);
        }
    }

    private boolean isMagneticInterference() {
        float totalMagnetic = 0;
        for (float value : magnetometerReading) {
            totalMagnetic += value * value;
        }
        totalMagnetic = (float) Math.sqrt(totalMagnetic);

        return totalMagnetic < MIN_MAGNETIC_FIELD || totalMagnetic > MAX_MAGNETIC_FIELD;
    }

    private void handleMagneticInterference(float currentAzimuth) {
        float filteredAzimuth = previousAzimuth + ALPHA * (currentAzimuth - previousAzimuth);
        filteredAzimuth = (filteredAzimuth + 360) % 360;
        previousAzimuth = filteredAzimuth;
        notifyOrientationChanged(filteredAzimuth);
    }

    public void calibrate() {
        if (!isCalibrated) {
            if (useCompassManager) {
                compassManager.start();
                isCalibrated = true;
                notifyCalibrationComplete(compassManager.getCurrentAzimuth());
            } else {
                if (isValidOrientation()) {
                    float initialAzimuth = orientationAngles[0] * RAD_TO_DEG;
                    initialAzimuth = (initialAzimuth + 360) % 360;
                    previousAzimuth = initialAzimuth;
                    isCalibrated = true;
                    notifyCalibrationComplete(initialAzimuth);
                }
            }
        }
    }

    private boolean isValidOrientation() {
        return Math.abs(accelerometerReading[0]) < 3 &&
                Math.abs(accelerometerReading[1]) < 3 &&
                Math.abs(accelerometerReading[2]) < 11;
    }

    public void addOrientationCallback(@NonNull OrientationCallback callback) {
        if (!callbacks.contains(callback)) {
            callbacks.add(callback);
        }
    }

    private void notifyOrientationChanged(float azimuth) {
        for (OrientationCallback callback : callbacks) {
            callback.onOrientationChanged(azimuth);
        }
    }

    private void notifyCalibrationComplete(float initialAzimuth) {
        for (OrientationCallback callback : callbacks) {
            callback.onCalibrationComplete(initialAzimuth);
        }
    }

    public interface OrientationCallback {
        void onOrientationChanged(float azimuth);
        void onCalibrationComplete(float initialAzimuth);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        if (sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            if (accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) {
                Log.w(TAG, "Magnetic field sensor is unreliable");
            }
        }
    }

    public float getCurrentAzimuth() {
        return useCompassManager ? compassManager.getCurrentAzimuth() : previousAzimuth;
    }

    public boolean isCalibrated() {
        return isCalibrated;
    }

    public void setUseCompassManager(boolean use) {
        this.useCompassManager = use;
        if (use) {
            compassManager.start();
            azimuthQueue.clear();
        } else {
            compassManager.stop();
            azimuthQueue.clear();
        }
    }

    public void destroy() {
        sensorManager.unregisterListener(this);
        compassManager.stop();
        callbacks.clear();
        azimuthQueue.clear();
        isCalibrated = false;
    }
}