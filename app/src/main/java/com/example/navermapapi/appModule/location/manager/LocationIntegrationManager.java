package com.example.navermapapi.appModule.location.manager;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
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

    // 수동 환경 설정 관련 변수
    private boolean isEnvironmentForced = false;
    private EnvironmentType forcedEnvironment;

    // 의존성 주입된 위치 제공자
    private final GpsLocationProvider gpsProvider;
    private final BeaconLocationProvider beaconProvider;

    // 위치 및 환경 상태 관리
    private final MutableLiveData<LocationData> currentLocation = new MutableLiveData<>();
    private final MutableLiveData<EnvironmentType> currentEnvironment = new MutableLiveData<>(EnvironmentType.OUTDOOR);

    // 기타 변수
    private final NoiseFilter locationFilter;

    private final AtomicBoolean isInitialized;
    private final AtomicBoolean isTracking;

    // 초기 GPS 위치 저장 (PDR 초기화 시 필요)
    private LocationData initialGpsLocation;

    @Inject
    public LocationIntegrationManager(
            @ApplicationContext Context context,
            GpsLocationProvider gpsProvider,
            BeaconLocationProvider beaconProvider
    ) {
        this.gpsProvider = gpsProvider;
        this.beaconProvider = beaconProvider;
        this.locationFilter = new NoiseFilter(5, 2.0);
        this.isInitialized = new AtomicBoolean(false);
        this.isTracking = new AtomicBoolean(false);

        setupCallbacks();
    }

    public void forceEnvironment(EnvironmentType environment) {
        isEnvironmentForced = true;
        forcedEnvironment = environment;
        handleEnvironmentChange(environment);
    }

    public void resetEnvironment() {
        isEnvironmentForced = false;
        // 자동 환경 감지 로직 재개
    }

    private void handleEnvironmentChange(EnvironmentType newEnvironment) {
        currentEnvironment.setValue(newEnvironment);

        // 환경에 따른 위치 제공자 전환 로직
        switch (newEnvironment) {
            case INDOOR:
                startIndoorTracking();
                break;
            case OUTDOOR:
                startOutdoorTracking();
                break;
            case TRANSITION:
                // 전환 상태 처리
                break;
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
        // 수동 환경 설정이 아닌 경우에만 자동 환경 감지
        if (!isEnvironmentForced) {
            EnvironmentType newEnvironment = determineEnvironment(location);
            if (newEnvironment != currentEnvironment.getValue()) {
                handleEnvironmentChange(newEnvironment);
            }
        }

        if (currentEnvironment.getValue() == EnvironmentType.OUTDOOR) {
            updateCurrentLocation(location);
        }
    }

    private void handlePdrLocation(@NonNull LocationData location) {
        if (currentEnvironment.getValue() == EnvironmentType.INDOOR) {
            updateCurrentLocation(location);
        }
    }

    private void updateCurrentLocation(@NonNull LocationData location) {
        try {
            double filteredLat;
            double filteredLng;

            if (location.getProvider().equals("PDR")) {
                // 초기 GPS 위치를 가져옴
                if (initialGpsLocation != null) {
                    LatLng initialLatLng = new LatLng(
                            initialGpsLocation.getLatitude(),
                            initialGpsLocation.getLongitude()
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
                // GPS 데이터 필터링
                filteredLat = locationFilter.filter(location.getLatitude());
                filteredLng = locationFilter.filter(location.getLongitude());

                // 초기 GPS 위치 설정 (최초 한 번만)
                if (initialGpsLocation == null) {
                    initialGpsLocation = location;
                }
            }

            // 필터링된 위치 데이터로 LocationData 생성
            LocationData filteredLocation = new LocationData.Builder(filteredLat, filteredLng)
                    .accuracy(location.getAccuracy())
                    .altitude(location.getAltitude())
                    .bearing(location.getBearing())
                    .speed(location.getSpeed())
                    .environment(currentEnvironment.getValue())
                    .provider(location.getProvider())
                    .build();

            currentLocation.setValue(filteredLocation);

        } catch (Exception e) {
            Log.e(TAG, "위치 업데이트 중 오류 발생", e);
        }
    }

    private EnvironmentType determineEnvironment(LocationData location) {
        // GPS 정확도를 기반으로 환경 판단
        if (location.getAccuracy() <= 20.0f) {
            return EnvironmentType.OUTDOOR;
        } else {
            return EnvironmentType.INDOOR;
        }
    }

    private void startIndoorTracking() {
        gpsProvider.stopTracking();
        if (currentLocation.getValue() != null) {
            beaconProvider.setInitialLocation(currentLocation.getValue());
        }
        beaconProvider.startTracking();
    }

    private void startOutdoorTracking() {
        beaconProvider.stopTracking();
        gpsProvider.startTracking();
    }

    private void handleGpsProviderDisabled() {
        Log.w(TAG, "GPS 제공자가 비활성화되었습니다.");
    }

    private void handlePdrProviderDisabled() {
        Log.w(TAG, "PDR 제공자가 비활성화되었습니다.");
    }

    public void initialize() {
        if (isInitialized.get()) {
            Log.d(TAG, "이미 초기화되었습니다.");
            return;
        }

        try {
            gpsProvider.initialize();
            beaconProvider.initialize();
            isInitialized.set(true);
            Log.d(TAG, "LocationIntegrationManager 초기화 완료");
        } catch (Exception e) {
            Log.e(TAG, "초기화 중 오류 발생", e);
            isInitialized.set(false);
            throw e;
        }
    }

    public void startTracking() {
        if (!isInitialized.get()) {
            initialize();
        }

        if (isInitialized.get() && !isTracking.getAndSet(true)) {
            EnvironmentType currentEnv = currentEnvironment.getValue();
            switch (currentEnv) {
                case INDOOR:
                    startIndoorTracking();
                    break;
                case OUTDOOR:
                    startOutdoorTracking();
                    break;
                case TRANSITION:
                    // 전환 상태 처리
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

    public LiveData<LocationData> getCurrentLocation() {
        return currentLocation;
    }

    public LiveData<EnvironmentType> getCurrentEnvironment() {
        return currentEnvironment;
    }

    public boolean isPdrOperating() {
        return beaconProvider != null && beaconProvider.isTracking();
    }

    public boolean isBeaconScanning() {
        // 비콘 스캔 여부를 반환하는 로직 구현
        // 비콘 스캔 기능이 없다면 false 반환
        return false;
    }

    public String getStatus() {
        StringBuilder status = new StringBuilder();
        status.append("현재 환경: ").append(currentEnvironment.getValue()).append("\n");
        status.append("현재 위치: ").append(currentLocation.getValue()).append("\n");
        return status.toString();
    }
}
