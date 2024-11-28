package com.example.navermapapi.beaconModule.api;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.MainThread;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;
import javax.inject.Singleton;

import com.example.navermapapi.beaconModule.internal.pdr.StepDetector;
import com.example.navermapapi.beaconModule.internal.pdr.OrientationCalculator;
import com.example.navermapapi.beaconModule.internal.beacon.BeaconScanner;
import com.example.navermapapi.beaconModule.internal.positioning.PositionCalculator;
import com.example.navermapapi.coreModule.api.location.model.LocationData;
import com.example.navermapapi.coreModule.api.location.callback.LocationCallback;
import com.example.navermapapi.coreModule.api.environment.model.EnvironmentType;

@Singleton
public class BeaconLocationProvider {
    private static final String TAG = "BeaconLocationProvider";
    private static final long MIN_UPDATE_INTERVAL = 100;
    private static final float BASE_ACCURACY = 1.0f;
    private static final double EARTH_RADIUS = 6371000;

    private final Context context;
    private final List<LocationCallback> callbacks;
    private final AtomicBoolean isTracking;
    private volatile boolean isInitialized = false;

    // 지연 초기화를 위한 필드들
    private StepDetector stepDetector;
    private OrientationCalculator orientationCalculator;
    private BeaconScanner beaconScanner;
    private PositionCalculator positionCalculator;

    private LocationData initialLocation;
    private long lastUpdateTime;
    private double currentX;
    private double currentY;

    @Inject
    public BeaconLocationProvider(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.callbacks = new CopyOnWriteArrayList<>();
        this.isTracking = new AtomicBoolean(false);
    }

    private void processPdrData(double offsetX, double offsetY, float estimatedAccuracy) {
        // PDR 위치 데이터를 생성
        LocationData pdrLocationData = new LocationData.Builder(0.0, 0.0) // 임시로 0.0 설정
                .offsetX(offsetX)
                .offsetY(offsetY)
                .accuracy(estimatedAccuracy)
                .provider("PDR")
                .environment(EnvironmentType.INDOOR)
                .build();

        // 콜백을 통해 위치 업데이트 알림
        notifyLocationChanged(pdrLocationData);
    }

    public void initialize() {
        if (isInitialized) {
            Log.d(TAG, "Already initialized");
            return;
        }

        try {
            // 컴포넌트 초기화
            this.stepDetector = new StepDetector(context);
            this.orientationCalculator = new OrientationCalculator(context);
            this.beaconScanner = new BeaconScanner(context);
            this.positionCalculator = new PositionCalculator();

            // 콜백 설정
            setupCallbacks();

            isInitialized = true;
            Log.d(TAG, "BeaconLocationProvider initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error initializing BeaconLocationProvider", e);
            isInitialized = false;
            throw e;
        }
    }

    private void setupCallbacks() {
        if (!isInitialized) {
            Log.w(TAG, "Cannot setup callbacks before initialization");
            return;
        }

        // 걸음 감지 콜백
        stepDetector.addStepCallback((stepLength, totalSteps) -> {
            if (shouldUpdateLocation()) {
                updatePosition(stepLength);
                lastUpdateTime = System.currentTimeMillis();
            }
        });

        // 방향 변화 콜백
        orientationCalculator.addOrientationCallback(new OrientationCalculator.OrientationCallback() {
            @Override
            public void onOrientationChanged(float azimuth) {
                if (shouldUpdateLocation()) {
                    LocationData location = calculateAbsoluteLocation();
                    if (location != null) {
                        notifyLocationChanged(location);
                    }
                    lastUpdateTime = System.currentTimeMillis();
                }
            }

            @Override
            public void onCalibrationComplete(float initialAzimuth) {
                if (initialLocation != null) {
                    initializePosition();
                }
            }
        });

        // 비콘 스캔 콜백
        beaconScanner.addScanCallback(beacons -> {
            if (isInitialized) {
                double[] position = positionCalculator.calculatePosition(beacons);
                if (position != null) {
                    updatePositionWithBeacon(position[0], position[1]);
                }
            }
        });
    }

    private boolean shouldUpdateLocation() {
        return isInitialized &&
                (System.currentTimeMillis() - lastUpdateTime >= MIN_UPDATE_INTERVAL);
    }

    private void updatePosition(float stepLength) {
        float azimuth = orientationCalculator.getCurrentAzimuth();
        double angle = Math.toRadians(azimuth);

        currentX += stepLength * Math.sin(angle);
        currentY += stepLength * Math.cos(angle);

        LocationData newLocation = calculateAbsoluteLocation();
        if (newLocation != null) {
            notifyLocationChanged(newLocation);
        }
    }

    private void updatePositionWithBeacon(double x, double y) {
        double weight = 0.3; // 비콘 위치의 가중치
        currentX = (1 - weight) * currentX + weight * x;
        currentY = (1 - weight) * currentY + weight * y;

        LocationData newLocation = calculateAbsoluteLocation();
        if (newLocation != null) {
            notifyLocationChanged(newLocation);
        }
    }

    @Nullable
    private LocationData calculateAbsoluteLocation() {
        if (initialLocation == null) return null;

        double lat = initialLocation.getLatitude();
        double lng = initialLocation.getLongitude();

        double dLat = Math.toDegrees(currentY / EARTH_RADIUS);
        double dLng = Math.toDegrees(currentX /
                (EARTH_RADIUS * Math.cos(Math.toRadians(lat))));

        return new LocationData.Builder(lat + dLat, lng + dLng)
                .accuracy(calculateAccuracy())
                .bearing(orientationCalculator.getCurrentAzimuth())
                .environment(EnvironmentType.INDOOR)
                .provider("PDR")
                .build();
    }

    private float calculateAccuracy() {
        return Math.min(BASE_ACCURACY + (stepDetector.getStepCount() * 0.1f), 10.0f);
    }

    public void startTracking() {
        if (!isInitialized) {
            Log.e(TAG, "Cannot start tracking before initialization");
            return;
        }

        if (!isTracking.getAndSet(true)) {
            try {
                orientationCalculator.calibrate();
                beaconScanner.startScanning();
                notifyProviderStateChanged("PDR", true);
            } catch (Exception e) {
                Log.e(TAG, "Error starting tracking", e);
                isTracking.set(false);
                throw e;
            }
        }
    }

    public void stopTracking() {
        if (isTracking.getAndSet(false)) {
            try {
                beaconScanner.stopScanning();
                resetTracking();
                notifyProviderStateChanged("PDR", false);
            } catch (Exception e) {
                Log.e(TAG, "Error stopping tracking", e);
                throw e;
            }
        }
    }

    public void setInitialLocation(@NonNull LocationData location) {
        this.initialLocation = location;
        if (isInitialized && orientationCalculator.isCalibrated()) {
            initializePosition();
        }
    }

    private void initializePosition() {
        currentX = 0;
        currentY = 0;
        lastUpdateTime = System.currentTimeMillis();
        notifyLocationChanged(initialLocation);
    }

    private void resetTracking() {
        currentX = 0;
        currentY = 0;
        lastUpdateTime = 0;

        if (stepDetector != null) stepDetector.destroy();
        if (orientationCalculator != null) orientationCalculator.destroy();
        if (beaconScanner != null) beaconScanner.stopScanning();
    }

    public void registerLocationCallback(@NonNull LocationCallback callback) {
        callbacks.add(callback);
    }

    public void unregisterLocationCallback(@NonNull LocationCallback callback) {
        callbacks.remove(callback);
    }

    private void notifyLocationChanged(@NonNull LocationData location) {
        for (LocationCallback callback : callbacks) {
            try {
                callback.onLocationUpdate(location);
            } catch (Exception e) {
                Log.e(TAG, "Error notifying location callback", e);
            }
        }
    }

    private void notifyProviderStateChanged(@NonNull String provider, boolean enabled) {
        for (LocationCallback callback : callbacks) {
            try {
                callback.onProviderStateChanged(provider, enabled);
            } catch (Exception e) {
                Log.e(TAG, "Error notifying provider state callback", e);
            }
        }
    }

    public void cleanup() {
        stopTracking();
        resetTracking();
        callbacks.clear();
        isInitialized = false;
    }

    public boolean isInitialized() {
        return isInitialized;
    }

    public boolean isTracking() {
        return isTracking.get();
    }
}