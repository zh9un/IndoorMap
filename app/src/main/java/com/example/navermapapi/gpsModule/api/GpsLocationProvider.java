/*
 * 파일명: GpsLocationProvider.java
 * 경로: com/example/navermapapi/gpsModule/api/GpsLocationProvider.java
 * 작성자: Claude
 * 작성일: 2024-11-18
 */

package com.example.navermapapi.gpsModule.api;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.location.GnssStatus;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresPermission;

import com.example.navermapapi.coreModule.api.location.callback.LocationCallback;
import com.example.navermapapi.coreModule.api.location.model.LocationData;
import com.example.navermapapi.coreModule.api.environment.model.EnvironmentType;
import com.example.navermapapi.gpsModule.internal.manager.GpsManager;
import com.example.navermapapi.gpsModule.internal.tracking.GpsTracker;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * GPS 기반 위치 제공자 구현
 * - GPS 신호를 통한 실외 위치 추적
 * - 실내외 전환 감지
 * - 배터리 효율적인 위치 업데이트 관리
 */
public class GpsLocationProvider {
    private static final String TAG = "GpsLocationProvider";
    private static final long DEFAULT_UPDATE_INTERVAL = 1000L; // 1초

    private final Context context;
    private final GpsManager gpsManager;
    private final GpsTracker gpsTracker;
    private final CopyOnWriteArrayList<LocationCallback> callbacks;
    private final AtomicBoolean isTracking;

    private long updateInterval = DEFAULT_UPDATE_INTERVAL;
    private LocationData lastLocation;
    private float currentSignalStrength;
    private int visibleSatellites;

    @RequiresPermission(anyOf = {
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
    })
    public GpsLocationProvider(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.callbacks = new CopyOnWriteArrayList<>();
        this.isTracking = new AtomicBoolean(false);

        this.gpsManager = new GpsManager(context, this::handleLocationUpdate);
        this.gpsTracker = new GpsTracker(context, gpsManager);

        initializeGnssMonitoring();
    }

    @SuppressLint("MissingPermission")
    private void initializeGnssMonitoring() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            LocationManager locationManager = (LocationManager)
                    context.getSystemService(Context.LOCATION_SERVICE);

            locationManager.registerGnssStatusCallback(
                    new GnssStatus.Callback() {
                        @Override
                        public void onSatelliteStatusChanged(GnssStatus status) {
                            updateGpsStatus(status);
                        }
                    },
                    null
            );
        }
    }

    private void updateGpsStatus(GnssStatus status) {
        float totalSignal = 0;
        int usedInFix = 0;

        for (int i = 0; i < status.getSatelliteCount(); i++) {
            if (status.usedInFix(i)) {
                totalSignal += status.getCn0DbHz(i);
                usedInFix++;
            }
        }

        visibleSatellites = status.getSatelliteCount();
        currentSignalStrength = usedInFix > 0 ? totalSignal / usedInFix : -160f;

        updateEnvironmentIfNeeded();
    }

    private void handleLocationUpdate(@NonNull Location location) {
        LocationData locationData = new LocationData.Builder(
                location.getLatitude(),
                location.getLongitude()
        )
                .accuracy(location.getAccuracy())
                .altitude(location.hasAltitude() ? (float)location.getAltitude() : 0f)
                .bearing(location.hasBearing() ? location.getBearing() : 0f)
                .speed(location.hasSpeed() ? location.getSpeed() : 0f)
                .environment(EnvironmentType.fromGpsSignal(currentSignalStrength, visibleSatellites))
                .provider("GPS")
                .confidence(calculateConfidence(location))
                .build();

        lastLocation = locationData;
        notifyLocationChanged(locationData);
    }

    private float calculateConfidence(Location location) {
        float accuracy = location.getAccuracy();
        float accuracyFactor = Math.max(0f, Math.min(1f, 50f / accuracy));

        float signalFactor = Math.max(0f, Math.min(1f,
                (currentSignalStrength + 160f) / ((-120f) + 160f)));

        float satelliteFactor = Math.min(1f, visibleSatellites / 8f);

        return (accuracyFactor + signalFactor + satelliteFactor) / 3f;
    }

    private void updateEnvironmentIfNeeded() {
        if (lastLocation != null) {
            EnvironmentType newEnvironment =
                    EnvironmentType.fromGpsSignal(currentSignalStrength, visibleSatellites);

            if (newEnvironment != lastLocation.getEnvironment()) {
                for (LocationCallback callback : callbacks) {
                    callback.onEnvironmentChanged(newEnvironment);
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    public void startTracking() {
        if (!isTracking.getAndSet(true)) {
            gpsManager.startLocationUpdates(updateInterval);
            gpsTracker.startTracking();
        }
    }

    public void stopTracking() {
        if (isTracking.getAndSet(false)) {
            gpsManager.stopLocationUpdates();
            gpsTracker.stopTracking();
        }
    }

    public LocationData getLastLocation() {
        return lastLocation;
    }

    public void registerLocationCallback(@NonNull LocationCallback callback) {
        if (!callbacks.contains(callback)) {
            callbacks.add(callback);

            if (lastLocation != null) {
                callback.onLocationUpdate(lastLocation);
            }
        }
    }

    public void unregisterLocationCallback(@NonNull LocationCallback callback) {
        callbacks.remove(callback);
    }

    private void notifyLocationChanged(LocationData location) {
        for (LocationCallback callback : callbacks) {
            callback.onLocationUpdate(location);
        }
    }

    public void setUpdateInterval(long interval) {
        this.updateInterval = Math.max(100L, interval);
        if (isTracking.get()) {
            gpsManager.updateInterval(updateInterval);
        }
    }

    public boolean isTracking() {
        return isTracking.get();
    }

    public float getCurrentSignalStrength() {
        return currentSignalStrength;
    }

    public int getVisibleSatellites() {
        return visibleSatellites;
    }
}