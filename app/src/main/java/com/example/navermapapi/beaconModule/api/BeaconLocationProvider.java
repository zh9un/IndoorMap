package com.example.navermapapi.beaconModule.api;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.MainThread;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import com.example.navermapapi.beaconModule.internal.pdr.StepDetector;
import com.example.navermapapi.beaconModule.internal.pdr.OrientationCalculator;
import com.example.navermapapi.coreModule.api.location.model.LocationData;
import com.example.navermapapi.coreModule.api.location.callback.LocationCallback;
import com.example.navermapapi.coreModule.api.environment.model.EnvironmentType;
import com.example.navermapapi.coreModule.api.location.callback.LocationCallback.LocationError;

/**
 * BeaconLocationProvider
 *
 * PDR(Pedestrian Dead Reckoning) 기반 실내 위치 추적 구현
 * 시각 장애인의 정확한 실내 위치 추적을 위해 최적화됨
 */
public class BeaconLocationProvider {
    private static final String TAG = "BeaconLocationProvider";

    // 위치 업데이트 최소 간격 (밀리초)
    private static final long MIN_UPDATE_INTERVAL = 100;

    // 위치 정확도 관련 상수
    private static final float BASE_ACCURACY = 1.0f;
    private static final float MAX_ACCURACY = 10.0f;
    private static final float ACCURACY_PER_STEP = 0.1f;

    // 좌표 변환 상수
    private static final double EARTH_RADIUS = 6371000;

    private final Context context;
    private final StepDetector stepDetector;
    private final OrientationCalculator orientationCalculator;
    private final List<LocationCallback> callbacks;

    // 현재 위치 정보
    private double currentX;
    private double currentY;
    private final AtomicBoolean isTracking;

    // 초기 위치 설정
    private LocationData initialLocation;
    private boolean isInitialized;
    private long lastUpdateTime;

    public BeaconLocationProvider(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.callbacks = new ArrayList<>();
        this.isTracking = new AtomicBoolean(false);

        this.stepDetector = new StepDetector(context);
        this.orientationCalculator = new OrientationCalculator(context);

        setupCallbacks();
        initializeErrorHandling();
    }

    private void setupCallbacks() {
        stepDetector.addStepCallback((stepLength, totalSteps) -> {
            if (isInitialized && shouldUpdateLocation()) {
                updatePosition(stepLength);
                lastUpdateTime = System.currentTimeMillis();
            }
        });

        orientationCalculator.addOrientationCallback(new OrientationCalculator.OrientationCallback() {
            @Override
            public void onOrientationChanged(float azimuth) {
                if (isInitialized && shouldUpdateLocation()) {
                    LocationData location = calculateAbsoluteLocation();
                    if (location != null) {
                        notifyLocationChanged(location);
                    }
                    lastUpdateTime = System.currentTimeMillis();
                }
            }

            @Override
            public void onCalibrationComplete(float initialAzimuth) {
                if (!isInitialized && initialLocation != null) {
                    initializePosition();
                }
            }
        });
    }

    private void initializeErrorHandling() {
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            Log.e(TAG, "Uncaught exception in " + thread.getName(), throwable);
            stopTracking();
            notifyError(LocationError.PDR_CALIBRATION_NEEDED);
        });
    }

    private boolean shouldUpdateLocation() {
        return System.currentTimeMillis() - lastUpdateTime >= MIN_UPDATE_INTERVAL;
    }

    private void updatePosition(float stepLength) {
        try {
            float azimuth = orientationCalculator.getCurrentAzimuth();
            double angle = Math.toRadians(azimuth);

            currentX += stepLength * Math.sin(angle);
            currentY += stepLength * Math.cos(angle);

            LocationData newLocation = calculateAbsoluteLocation();
            if (newLocation != null) {
                notifyLocationChanged(newLocation);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating position", e);
            notifyError(LocationError.PDR_CALIBRATION_NEEDED);
        }
    }

    @Nullable
    private LocationData calculateAbsoluteLocation() {
        if (initialLocation == null) return null;

        try {
            double lat = initialLocation.getLatitude();
            double lng = initialLocation.getLongitude();

            double dLat = Math.toDegrees(currentY / EARTH_RADIUS);
            double dLng = Math.toDegrees(currentX / (EARTH_RADIUS * Math.cos(Math.toRadians(lat))));

            float bearing = (float) Math.toDegrees(Math.atan2(currentX, currentY));
            if (bearing < 0) bearing += 360;

            return new LocationData.Builder(lat + dLat, lng + dLng)
                    .accuracy(calculateAccuracy())
                    .bearing(bearing)
                    .environment(EnvironmentType.INDOOR)
                    .provider("PDR")
                    .confidence(calculateConfidence())
                    .build();

        } catch (Exception e) {
            Log.e(TAG, "Error calculating absolute location", e);
            return null;
        }
    }

    private float calculateAccuracy() {
        int stepCount = stepDetector.getStepCount();
        return Math.min(BASE_ACCURACY + (stepCount * ACCURACY_PER_STEP), MAX_ACCURACY);
    }

    private float calculateConfidence() {
        float accuracy = calculateAccuracy();
        return Math.max(0, 1 - (accuracy / MAX_ACCURACY));
    }

    public void startTracking() {
        if (!isTracking.getAndSet(true)) {
            orientationCalculator.calibrate();
            notifyProviderStateChanged("PDR", true);
            Log.i(TAG, "Started PDR tracking");
        }
    }

    public void stopTracking() {
        if (isTracking.getAndSet(false)) {
            resetTracking();
            notifyProviderStateChanged("PDR", false);
            Log.i(TAG, "Stopped PDR tracking");
        }
    }

    public void setInitialLocation(@NonNull LocationData location) {
        this.initialLocation = location;
        if (!isInitialized && orientationCalculator.isCalibrated()) {
            initializePosition();
        }
    }

    private void initializePosition() {
        currentX = 0;
        currentY = 0;
        isInitialized = true;
        lastUpdateTime = System.currentTimeMillis();
        notifyLocationChanged(initialLocation);
        Log.i(TAG, "Initialized PDR tracking position");
    }

    private void resetTracking() {
        currentX = 0;
        currentY = 0;
        isInitialized = false;
        lastUpdateTime = 0;
        stepDetector.destroy();
        orientationCalculator.destroy();
    }

    @Nullable
    public LocationData getLastLocation() {
        return calculateAbsoluteLocation();
    }

    public void registerLocationCallback(@NonNull LocationCallback callback) {
        if (!callbacks.contains(callback)) {
            callbacks.add(callback);
        }
    }

    public void unregisterLocationCallback(@NonNull LocationCallback callback) {
        callbacks.remove(callback);
    }

    @MainThread
    private void notifyLocationChanged(@NonNull LocationData location) {
        for (LocationCallback callback : callbacks) {
            callback.onLocationUpdate(location);
        }
    }

    @MainThread
    private void notifyProviderStateChanged(@NonNull String provider, boolean enabled) {
        for (LocationCallback callback : callbacks) {
            callback.onProviderStateChanged(provider, enabled);
        }
    }

    @MainThread
    private void notifyError(@NonNull LocationError error) {
        for (LocationCallback callback : callbacks) {
            callback.onError(error);
        }
    }

    public boolean isTracking() {
        return isTracking.get();
    }

    @NonNull
    public String getTrackingStatus() {
        StringBuilder status = new StringBuilder();
        status.append("PDR Tracking Status:\n");
        status.append("Initialized: ").append(isInitialized).append("\n");
        status.append("Tracking: ").append(isTracking.get()).append("\n");
        status.append("Steps: ").append(stepDetector.getStepCount()).append("\n");
        status.append("Heading: ").append(
                String.format("%.1f", orientationCalculator.getCurrentAzimuth())
        ).append("°\n");
        status.append("Relative X: ").append(String.format("%.2f", currentX)).append("m\n");
        status.append("Relative Y: ").append(String.format("%.2f", currentY)).append("m\n");
        status.append("Accuracy: ").append(String.format("%.1f", calculateAccuracy())).append("m\n");
        status.append("Confidence: ").append(String.format("%.2f", calculateConfidence())).append("\n");
        return status.toString();
    }
}