package com.example.navermapapi.appModule.location.manager;

import android.Manifest;
import android.content.Context;
import android.location.Location;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.naver.maps.geometry.LatLng;
import javax.inject.Inject;
import javax.inject.Singleton;
import dagger.hilt.android.qualifiers.ApplicationContext;
import java.util.concurrent.atomic.AtomicBoolean;

import com.example.navermapapi.coreModule.api.location.model.LocationData;
import com.example.navermapapi.coreModule.api.environment.model.EnvironmentType;
import com.example.navermapapi.coreModule.utils.filter.NoiseFilter;
import com.example.navermapapi.gpsModule.api.GpsLocationProvider;
import com.example.navermapapi.beaconModule.api.BeaconLocationProvider;
import com.example.navermapapi.coreModule.api.location.callback.LocationCallback;
import com.naver.maps.geometry.LatLng;
import com.example.navermapapi.beaconModule.internal.pdr.PdrPositionManager;
/*
 * 파일명: LocationIntegrationManager.java
 * 경로: com.example.navermapapi.appModule.location.manager
 * 수정일: 2024-11-28
 */

@Singleton
public class LocationIntegrationManager {
    private static final String TAG = "LocationIntegrationManager";

    // 의존성
    private final GpsLocationProvider gpsProvider;
    private final BeaconLocationProvider beaconProvider;
    private final LocationStateManager stateManager;
    private final NoiseFilter locationFilter;

    // 상태 관리
    private final AtomicBoolean isInitialized;
    private final AtomicBoolean isTracking;

    @Inject
    public LocationIntegrationManager(
            @ApplicationContext Context context,
            GpsLocationProvider gpsProvider,
            BeaconLocationProvider beaconProvider
    ) {
        this.gpsProvider = gpsProvider;
        this.beaconProvider = beaconProvider;
        this.stateManager = new LocationStateManager();
        this.locationFilter = new NoiseFilter(5, 2.0);
        this.isInitialized = new AtomicBoolean(false);
        this.isTracking = new AtomicBoolean(false);

        setupCallbacks();
    }

    private void setupCallbacks() {
        // GPS 위치 업데이트 콜백
        gpsProvider.registerLocationCallback(new LocationCallback() {
            @Override
            public void onLocationUpdate(@NonNull LocationData location) {
                handleGpsLocation(location);
            }

            @Override
            public void onProviderStateChanged(@NonNull String provider, boolean enabled) {
                if (!enabled) {
                    handleGpsProviderDisabled();
                }
            }
        });

        // PDR 위치 업데이트 콜백
        beaconProvider.registerLocationCallback(new LocationCallback() {
            @Override
            public void onLocationUpdate(@NonNull LocationData location) {
                handlePdrLocation(location);
            }

            @Override
            public void onProviderStateChanged(@NonNull String provider, boolean enabled) {
                if (!enabled) {
                    handlePdrProviderDisabled();
                }
            }
        });
    }

    private void handleGpsLocation(@NonNull LocationData location) {
        // GPS 신호 강도 및 위성 정보 업데이트
        float signalStrength = gpsProvider.getCurrentSignalStrength();
        int satellites = gpsProvider.getVisibleSatellites();
        stateManager.updateSignalStrength(signalStrength, satellites);

        // 현재 환경에 따른 위치 업데이트 처리
        EnvironmentType currentEnv = stateManager.getCurrentEnvironment().getValue();
        if (currentEnv == EnvironmentType.OUTDOOR ||
                currentEnv == EnvironmentType.TRANSITION) {
            updateCurrentLocation(location);
        }
    }

    private void handlePdrLocation(@NonNull LocationData location) {
        EnvironmentType currentEnv = stateManager.getCurrentEnvironment().getValue();
        if (currentEnv == EnvironmentType.INDOOR ||
                currentEnv == EnvironmentType.TRANSITION) {
            updateCurrentLocation(location);
        }
    }

    private void updateCurrentLocation(@NonNull LocationData location) {
        try {
            double filteredLat;
            double filteredLng;

            // PDR 데이터를 기반으로 좌표 계산
            if (location.getProvider().equals("PDR")) {
                // 초기 GPS 위치를 가져옴
                LocationData initialLocationData = stateManager.getInitialGpsLocation();
                if (initialLocationData != null) {
                    LatLng initialLatLng = new LatLng(
                            initialLocationData.getLatitude(),
                            initialLocationData.getLongitude()
                    );
                    PdrPositionManager pdrManager = new PdrPositionManager(initialLatLng);

                    // PDR의 offsetX, offsetY를 LatLng로 변환
                    LatLng pdrLatLng = pdrManager.calculateLatLng(
                            location.getOffsetX(),
                            location.getOffsetY()
                    );

                    // 필터링 적용
                    filteredLat = locationFilter.filter(pdrLatLng.latitude);
                    filteredLng = locationFilter.filter(pdrLatLng.longitude);
                } else {
                    Log.w(TAG, "초기 GPS 위치가 없어 PDR 위치를 계산할 수 없습니다.");
                    return;
                }
            } else {
                // 기존 GPS 데이터의 경우 필터링만 적용
                filteredLat = locationFilter.filter(location.getLatitude());
                filteredLng = locationFilter.filter(location.getLongitude());

                // 초기 GPS 위치 설정 (최초 한 번만)
                stateManager.setInitialGpsLocation(location);
            }

            // 필터링된 위치 데이터로 LocationData 생성
            LocationData filteredLocation = new LocationData.Builder(filteredLat, filteredLng)
                    .accuracy(location.getAccuracy())
                    .altitude(location.getAltitude())
                    .bearing(location.getBearing())
                    .speed(location.getSpeed())
                    .environment(stateManager.getCurrentEnvironment().getValue())
                    .provider(location.getProvider())
                    .confidence(calculateConfidence(location))
                    .build();

            stateManager.updateLocation(filteredLocation);

        } catch (Exception e) {
            Log.e(TAG, "Error updating location", e);
        }
    }

    private float calculateConfidence(LocationData location) {
        if (stateManager.getCurrentEnvironment().getValue() == EnvironmentType.OUTDOOR) {
            float signalStrength = gpsProvider.getCurrentSignalStrength();
            int satellites = gpsProvider.getVisibleSatellites();
            return calculateGpsConfidence(signalStrength, satellites, location.getAccuracy());
        } else {
            return calculatePdrConfidence(location);
        }
    }

    private float calculateGpsConfidence(float signalStrength, int satellites, float accuracy) {
        float signalFactor = Math.max(0f, Math.min(1f, (signalStrength + 160f) / 40f));
        float satelliteFactor = Math.min(1f, satellites / 8f);
        float accuracyFactor = Math.max(0f, Math.min(1f, 50f / accuracy));
        return (signalFactor + satelliteFactor + accuracyFactor) / 3f;
    }

    private float calculatePdrConfidence(LocationData location) {
        long timeSinceStart = System.currentTimeMillis() - location.getTimestamp();
        return Math.max(0.3f, 1f - (timeSinceStart / (5 * 60 * 1000f))); // 5분 기준
    }

    private void handleGpsProviderDisabled() {
        Log.w(TAG, "GPS provider disabled");
    }

    private void handlePdrProviderDisabled() {
        Log.w(TAG, "PDR provider disabled");
    }

    public void initialize() {
        if (isInitialized.get()) {
            Log.d(TAG, "Already initialized");
            return;
        }

        try {
            gpsProvider.initialize();
            beaconProvider.initialize();
            isInitialized.set(true);
            Log.d(TAG, "LocationIntegrationManager initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error during initialization", e);
            isInitialized.set(false);
            throw e;
        }
    }

    public void startTracking() {
        if (!isInitialized.get()) {
            initialize();
        }

        if (isInitialized.get() && !isTracking.getAndSet(true)) {
            EnvironmentType currentEnv = stateManager.getCurrentEnvironment().getValue();
            switch (currentEnv) {
                case INDOOR:
                    startIndoorTracking();
                    break;
                case OUTDOOR:
                    startOutdoorTracking();
                    break;
                case TRANSITION:
                    startTransitionTracking();
                    break;
            }
        }
    }

    private void startIndoorTracking() {
        gpsProvider.stopTracking();
        LocationData lastLocation = stateManager.getLastKnownLocation().getValue();
        if (lastLocation != null) {
            beaconProvider.setInitialLocation(lastLocation);
        }
        beaconProvider.startTracking();
    }

    private void startOutdoorTracking() {
        beaconProvider.stopTracking();
        gpsProvider.startTracking();
    }

    private void startTransitionTracking() {
        gpsProvider.startTracking();
        LocationData lastLocation = stateManager.getLastKnownLocation().getValue();
        if (lastLocation != null) {
            beaconProvider.setInitialLocation(lastLocation);
            beaconProvider.startTracking();
        }
    }

    public void stopTracking() {
        if (isTracking.getAndSet(false)) {
            gpsProvider.stopTracking();
            beaconProvider.stopTracking();
        }
    }

    public void cleanup() {
        stopTracking();
        gpsProvider.stopTracking();
        beaconProvider.cleanup();
        isInitialized.set(false);
    }

    public LiveData<LocationData> getCurrentLocation() {
        return stateManager.getLastKnownLocation();
    }

    public LiveData<EnvironmentType> getCurrentEnvironment() {
        return stateManager.getCurrentEnvironment();
    }

    public String getStatus() {
        return stateManager.getStatusInfo();
    }
}