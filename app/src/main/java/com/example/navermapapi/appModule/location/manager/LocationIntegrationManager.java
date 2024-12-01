package com.example.navermapapi.appModule.location.manager;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import javax.inject.Inject;
import javax.inject.Singleton;
import dagger.hilt.android.qualifiers.ApplicationContext;
import java.util.concurrent.atomic.AtomicBoolean;

import com.example.navermapapi.coreModule.api.location.model.LocationData;
import com.example.navermapapi.coreModule.api.environment.model.EnvironmentType;
import com.example.navermapapi.coreModule.api.location.callback.LocationCallback;
import com.example.navermapapi.gpsModule.api.GpsLocationProvider;
import com.example.navermapapi.beaconModule.api.BeaconLocationProvider;

@Singleton
public class LocationIntegrationManager {
    private static final String TAG = "LocationIntegrationManager";

    // 수동 환경 설정 관련 변수
    private boolean isEnvironmentForced = false;
    private EnvironmentType forcedEnvironment;

    // 의존성 주입된 위치 제공자들
    private final GpsLocationProvider gpsProvider;
    private final BeaconLocationProvider beaconProvider;

    // 위치 및 환경 상태 관리
    private final MutableLiveData<LocationData> currentLocation;
    private final MutableLiveData<EnvironmentType> currentEnvironment;
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
        this.currentLocation = new MutableLiveData<>();
        this.currentEnvironment = new MutableLiveData<>(EnvironmentType.OUTDOOR);
        this.isInitialized = new AtomicBoolean(false);
        this.isTracking = new AtomicBoolean(false);

        setupCallbacks();
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
            Log.d(TAG, "LocationIntegrationManager initialized");
        } catch (Exception e) {
            Log.e(TAG, "Error initializing", e);
            isInitialized.set(false);
            throw e;
        }
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
                    Log.w(TAG, "GPS provider disabled");
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
                    Log.w(TAG, "PDR provider disabled");
                }
            }
        });
    }

    private void handleGpsLocation(@NonNull LocationData location) {
        if (!isEnvironmentForced) {
            EnvironmentType newEnvironment = determineEnvironment(location);
            if (newEnvironment != currentEnvironment.getValue()) {
                handleEnvironmentChange(newEnvironment);
            }
        }

        if (currentEnvironment.getValue() == EnvironmentType.OUTDOOR) {
            currentLocation.setValue(location);
        }
    }

    private void handlePdrLocation(@NonNull LocationData location) {
        if (currentEnvironment.getValue() == EnvironmentType.INDOOR) {
            currentLocation.setValue(location);
        }
    }

    private EnvironmentType determineEnvironment(LocationData location) {
        if (location.getAccuracy() <= 20.0f && gpsProvider.getVisibleSatellites() >= 4) {
            return EnvironmentType.OUTDOOR;
        } else if (location.getAccuracy() > 50.0f || gpsProvider.getVisibleSatellites() < 3) {
            return EnvironmentType.INDOOR;
        }
        return EnvironmentType.TRANSITION;
    }

    public void forceEnvironment(EnvironmentType environment) {
        isEnvironmentForced = true;
        forcedEnvironment = environment;
        handleEnvironmentChange(environment);
    }

    public void resetEnvironment() {
        isEnvironmentForced = false;
        LocationData currentLocation = this.currentLocation.getValue();
        if (currentLocation != null) {
            handleEnvironmentChange(determineEnvironment(currentLocation));
        }
    }

    private void handleEnvironmentChange(EnvironmentType newEnvironment) {
        EnvironmentType currentEnv = currentEnvironment.getValue();
        if (currentEnv == newEnvironment) return;

        currentEnvironment.setValue(newEnvironment);

        switch (newEnvironment) {
            case INDOOR:
                startIndoorTracking();
                break;
            case OUTDOOR:
                startOutdoorTracking();
                break;
            case TRANSITION:
                // 전환 상태에서는 현재 사용 중인 제공자 유지
                break;
        }
    }

    private void startIndoorTracking() {
        gpsProvider.stopTracking();
        LocationData lastLocation = currentLocation.getValue();
        if (lastLocation != null) {
            beaconProvider.setInitialLocation(lastLocation);
        }
        beaconProvider.startTracking();
    }

    private void startOutdoorTracking() {
        beaconProvider.stopTracking();
        gpsProvider.startTracking();
    }

    public void startTracking() {
        if (!isInitialized.get()) {
            initialize();
        }

        if (isInitialized.get() && !isTracking.getAndSet(true)) {
            EnvironmentType currentEnv = currentEnvironment.getValue();
            if (currentEnv == EnvironmentType.INDOOR) {
                startIndoorTracking();
            } else {
                startOutdoorTracking();
            }
        }
    }

    public void stopTracking() {
        if (isTracking.getAndSet(false)) {
            gpsProvider.stopTracking();
            beaconProvider.stopTracking();
        }
    }

    public LiveData<LocationData> getCurrentLocation() {
        return currentLocation;
    }

    public LiveData<EnvironmentType> getCurrentEnvironment() {
        return currentEnvironment;
    }

    // Debug 및 모니터링용 메서드들
    public int getStepCount() {
        return isPdrOperating() ? beaconProvider.getStepCount() : 0;
    }

    public double getDistanceTraveled() {
        return isPdrOperating() ? beaconProvider.getDistanceTraveled() : 0.0;
    }

    public float getCurrentHeading() {
        LocationData location = currentLocation.getValue();
        return location != null ? location.getBearing() : -1;
    }

    public boolean isPdrOperating() {
        return beaconProvider != null && beaconProvider.isTracking();
    }

    public int getDetectedBeaconCount() {
        return isPdrOperating() ? beaconProvider.getBeaconCount() : 0;
    }

    public float getCurrentSignalStrength() {
        return gpsProvider != null ? gpsProvider.getCurrentSignalStrength() : -160f;
    }

    public int getVisibleSatellites() {
        return gpsProvider != null ? gpsProvider.getVisibleSatellites() : 0;
    }

    public boolean isGpsAvailable() {
        return gpsProvider != null && gpsProvider.isTracking();
    }

    public void resetPdrSystem() {
        if (isPdrOperating()) {
            LocationData current = currentLocation.getValue();
            if (current != null) {
                beaconProvider.setInitialLocation(current);
                beaconProvider.stopTracking();
                beaconProvider.startTracking();
            }
        }
    }

    public void cleanup() {
        stopTracking();
        if (gpsProvider != null) {
            gpsProvider.stopTracking();
        }
        if (beaconProvider != null) {
            beaconProvider.cleanup();
        }
        isInitialized.set(false);
    }

    public boolean isInitialized() {
        return isInitialized.get();
    }

    public boolean isTracking() {
        return isTracking.get();
    }

    public void updateDemoLocation(@NonNull LocationData location) {
        if (location.getProvider().equals("DEMO")) {
            currentLocation.setValue(location);
        }
    }
}