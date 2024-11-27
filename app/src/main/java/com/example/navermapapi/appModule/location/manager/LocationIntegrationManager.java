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
@Singleton
public class LocationIntegrationManager {
    private static final String TAG = "LocationIntegrationManager";

    // 상수 정의
    private static final float MIN_GPS_SIGNAL = -140.0f;  // dBm
    private static final int MIN_SATELLITES = 4;
    private static final long TRANSITION_THRESHOLD = 5000L;  // 5초

    // 의존성
    private final GpsLocationProvider gpsProvider;
    private final BeaconLocationProvider beaconProvider;
    private final NoiseFilter locationFilter;

    // 상태 관리
    private final MutableLiveData<LocationData> currentLocation;
    private final MutableLiveData<EnvironmentType> currentEnvironment;
    private final AtomicBoolean isInitialized;
    private final AtomicBoolean isTracking;

    // 전환 관련 변수
    private LocationData lastGpsLocation;
    private LocationData lastPdrLocation;
    private long lastTransitionTime;

    @Inject
    public LocationIntegrationManager(
            @ApplicationContext Context context,
            GpsLocationProvider gpsProvider,
            BeaconLocationProvider beaconProvider
    ) {
        this.gpsProvider = gpsProvider;
        this.beaconProvider = beaconProvider;
        this.locationFilter = new NoiseFilter(5, 2.0);
        this.currentLocation = new MutableLiveData<>();
        this.currentEnvironment = new MutableLiveData<>(EnvironmentType.OUTDOOR);
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
    private void handleGpsProviderDisabled() {
        Log.w(TAG, "GPS provider disabled");
        if (currentEnvironment.getValue() == EnvironmentType.OUTDOOR) {
            // GPS가 비활성화되면 실내 모드로 전환 시도
            handleEnvironmentTransition(EnvironmentType.INDOOR);
        }
    }

    private void handlePdrProviderDisabled() {
        Log.w(TAG, "PDR provider disabled");
        if (currentEnvironment.getValue() == EnvironmentType.INDOOR) {
            // PDR이 비활성화되면 실외 모드로 전환 시도
            handleEnvironmentTransition(EnvironmentType.OUTDOOR);
        }
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

    private void handleGpsLocation(@NonNull LocationData location) {
        lastGpsLocation = location;

        // GPS 신호 기반 환경 판단
        EnvironmentType newEnvironment = determineEnvironment();
        if (newEnvironment != currentEnvironment.getValue()) {
            handleEnvironmentTransition(newEnvironment);
        }

        // 실외일 때만 GPS 위치 사용
        if (currentEnvironment.getValue() == EnvironmentType.OUTDOOR) {
            updateCurrentLocation(location);
        }
    }

    private void handlePdrLocation(@NonNull LocationData location) {
        lastPdrLocation = location;

        // 실내일 때만 PDR 위치 사용
        if (currentEnvironment.getValue() == EnvironmentType.INDOOR) {
            updateCurrentLocation(location);
        }
    }

    private EnvironmentType determineEnvironment() {
        float signalStrength = gpsProvider.getCurrentSignalStrength();
        int satellites = gpsProvider.getVisibleSatellites();

        if (signalStrength > MIN_GPS_SIGNAL && satellites >= MIN_SATELLITES) {
            return EnvironmentType.OUTDOOR;
        } else if (signalStrength < MIN_GPS_SIGNAL || satellites < MIN_SATELLITES) {
            return EnvironmentType.INDOOR;
        }
        return EnvironmentType.TRANSITION;
    }

    private void handleEnvironmentTransition(EnvironmentType newEnvironment) {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastTransitionTime < TRANSITION_THRESHOLD) {
            return;  // 너무 빈번한 전환 방지
        }

        currentEnvironment.setValue(newEnvironment);
        lastTransitionTime = currentTime;

        switch (newEnvironment) {
            case INDOOR:
                startIndoorTracking();
                break;
            case OUTDOOR:
                startOutdoorTracking();
                break;
            case TRANSITION:
                handleTransitionState();
                break;
        }
    }

    private void startIndoorTracking() {
        gpsProvider.stopTracking();
        if (lastGpsLocation != null) {
            beaconProvider.setInitialLocation(lastGpsLocation);
        }
        beaconProvider.startTracking();
    }

    private void startOutdoorTracking() {
        beaconProvider.stopTracking();
        gpsProvider.startTracking();
    }

    private void handleTransitionState() {
        // 전환 상태에서는 두 위치 제공자 모두 활성화
        gpsProvider.startTracking();
        if (lastGpsLocation != null) {
            beaconProvider.setInitialLocation(lastGpsLocation);
            beaconProvider.startTracking();
        }
    }

    private void updateCurrentLocation(LocationData location) {
        // 위치 데이터 필터링
        double filteredLat = locationFilter.filter(location.getLatitude());
        double filteredLng = locationFilter.filter(location.getLongitude());

        LocationData filteredLocation = new LocationData.Builder(filteredLat, filteredLng)
                .accuracy(location.getAccuracy())
                .altitude(location.getAltitude())
                .bearing(location.getBearing())
                .speed(location.getSpeed())
                .environment(currentEnvironment.getValue())
                .provider(location.getProvider())
                .confidence(calculateConfidence(location))
                .build();

        currentLocation.setValue(filteredLocation);
    }

    private float calculateConfidence(LocationData location) {
        if (currentEnvironment.getValue() == EnvironmentType.OUTDOOR) {
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
        // PDR의 신뢰도는 시간이 지날수록 감소
        long timeSinceStart = System.currentTimeMillis() - lastTransitionTime;
        float timeFactor = Math.max(0.3f, 1f - (timeSinceStart / (5 * 60 * 1000f))); // 5분 기준
        return timeFactor;
    }

    // Public API
    public LiveData<LocationData> getCurrentLocation() {
        return currentLocation;
    }

    public LiveData<EnvironmentType> getCurrentEnvironment() {
        return currentEnvironment;
    }

    public void startTracking() {
        if (!isInitialized.get()) {
            initialize();
        }

        if (isInitialized.get() && !isTracking.getAndSet(true)) {
            switch (currentEnvironment.getValue()) {
                case INDOOR:
                    startIndoorTracking();
                    break;
                case OUTDOOR:
                    startOutdoorTracking();
                    break;
                case TRANSITION:
                    handleTransitionState();
                    break;
            }
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

    public String getStatus() {
        return String.format(
                "Environment: %s\nLocation: %s\nInitialized: %s\nTracking: %s",
                currentEnvironment.getValue(),
                currentLocation.getValue(),
                isInitialized.get(),
                isTracking.get()
        );
    }
}