package com.example.navermapapi.appModule.location.manager;

import static com.example.navermapapi.coreModule.api.location.callback.LocationCallback.LocationError.ACCURACY_INSUFFICIENT;
import static com.example.navermapapi.coreModule.api.location.callback.LocationCallback.LocationError.PERMISSION_DENIED;
import static com.example.navermapapi.coreModule.api.location.callback.LocationCallback.LocationError.PROVIDER_DISABLED;

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
import com.example.navermapapi.utils.CoordinateConverter;
import com.naver.maps.geometry.LatLng;

/**
 * GPS와 PDR 기반 위치 추적을 통합 관리하는 클래스
 */
@Singleton
public class LocationIntegrationManager {
    private static final String TAG = "LocationIntegrationMgr";
    private static final long MIN_PROVIDER_SWITCH_INTERVAL = 3000L; // 3초

    private final Context context;
    private final GpsLocationProvider gpsProvider;
    private final BeaconLocationProvider beaconProvider;
    private final LocationStateManager stateManager;
    private final CoordinateConverter coordinateConverter;

    // 상태 관리
    private final MutableLiveData<LocationData> currentLocation;
    private final MutableLiveData<EnvironmentType> currentEnvironment;
    private final AtomicBoolean isInitialized;
    private final AtomicBoolean isTracking;

    // 위치 제공자 전환 관련
    private long lastProviderSwitchTime;
    private boolean isTransitioning;

    @Inject
    public LocationIntegrationManager(
            @ApplicationContext Context context,
            GpsLocationProvider gpsProvider,
            BeaconLocationProvider beaconProvider
    ) {
        this.context = context.getApplicationContext();
        this.gpsProvider = gpsProvider;
        this.beaconProvider = beaconProvider;
        this.stateManager = new LocationStateManager();
        this.coordinateConverter = new CoordinateConverter();

        this.currentLocation = new MutableLiveData<>();
        this.currentEnvironment = new MutableLiveData<>(EnvironmentType.OUTDOOR);
        this.isInitialized = new AtomicBoolean(false);
        this.isTracking = new AtomicBoolean(false);

        setupCallbacks();
        Log.d(TAG, "LocationIntegrationManager initialized");
    }

    /**
     * 시스템 초기화
     */
    public void initialize() {
        if (isInitialized.get()) {
            Log.d(TAG, "Already initialized");
            return;
        }

        try {
            gpsProvider.initialize();
            beaconProvider.initialize();
            isInitialized.set(true);
            Log.d(TAG, "Location providers initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error initializing location providers", e);
            isInitialized.set(false);
            throw e;
        }
    }

    /**
     * 위치 제공자 콜백 설정
     */
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
                    if (currentEnvironment.getValue() == EnvironmentType.OUTDOOR) {
                        handleProviderFailure(EnvironmentType.OUTDOOR);
                    }
                }
            }

            @Override
            public void onError(@NonNull LocationError error) {
                Log.e(TAG, "GPS provider error: " + error.getMessage());
                handleProviderError("GPS", error);
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
                    if (currentEnvironment.getValue() == EnvironmentType.INDOOR) {
                        handleProviderFailure(EnvironmentType.INDOOR);
                    }
                }
            }

            @Override
            public void onError(@NonNull LocationError error) {
                Log.e(TAG, "PDR provider error: " + error.getMessage());
                handleProviderError("PDR", error);
            }
        });
    }

    /**
     * GPS 위치 데이터 처리
     */
    private void handleGpsLocation(@NonNull LocationData location) {
        // 실내 모드에서는 GPS 데이터 무시
        if (stateManager.getIndoorMode().getValue()) {
            Log.d(TAG, "Ignoring GPS location in indoor mode");
            return;
        }

        // GPS 신호 강도로 환경 판단
        if (!isTransitioning) {
            float signalStrength = gpsProvider.getCurrentSignalStrength();
            int satellites = gpsProvider.getVisibleSatellites();
            stateManager.updateSignalStrength(signalStrength, satellites);
        }

        // 최초 GPS 위치 저장
        if (stateManager.getInitialGpsLocation() == null) {
            stateManager.setInitialGpsLocation(location);
        }

        if (currentEnvironment.getValue() == EnvironmentType.OUTDOOR) {
            currentLocation.setValue(location);
            Log.d(TAG, "Updated outdoor location: " + location);
        }
    }

    /**
     * PDR 위치 데이터 처리
     */
    private void handlePdrLocation(@NonNull LocationData location) {
        // 실외 모드에서는 PDR 데이터 무시
        if (!stateManager.getIndoorMode().getValue()) {
            Log.d(TAG, "Ignoring PDR location in outdoor mode");
            return;
        }

        if (currentEnvironment.getValue() == EnvironmentType.INDOOR) {
            // 상대 좌표를 절대 좌표로 변환
            LocationData absoluteLocation = convertToAbsoluteLocation(location);
            currentLocation.setValue(absoluteLocation);
            Log.d(TAG, "Updated indoor location: " + absoluteLocation);
        }
    }

    /**
     * PDR 상대 좌표를 절대 좌표로 변환
     */
    @Nullable
    private LocationData convertToAbsoluteLocation(@NonNull LocationData pdrLocation) {
        LocationData referenceLocation = stateManager.getInitialGpsLocation();
        if (referenceLocation == null) {
            Log.w(TAG, "No reference location available for coordinate conversion");
            return null;
        }

        try {
            LatLng referencePoint = new LatLng(
                    referenceLocation.getLatitude(),
                    referenceLocation.getLongitude()
            );

            coordinateConverter.setReferencePoint(referencePoint);
            LatLng absolutePosition = coordinateConverter.toLatLng(
                    pdrLocation.getOffsetX(),
                    pdrLocation.getOffsetY()
            );

            return new LocationData.Builder(
                    absolutePosition.latitude,
                    absolutePosition.longitude
            )
                    .accuracy(pdrLocation.getAccuracy())
                    .bearing(pdrLocation.getBearing())
                    .environment(EnvironmentType.INDOOR)
                    .provider("PDR")
                    .build();

        } catch (Exception e) {
            Log.e(TAG, "Error converting PDR coordinates", e);
            return null;
        }
    }

    /**
     * 위치 제공자 오류 처리
     */
    private void handleProviderError(String provider, LocationCallback.LocationError error) {
        Log.e(TAG, String.format("Provider error: %s - %s", provider, error.getMessage()));

        // 오류 상황에 따른 대응
        switch (error) {
            case PERMISSION_DENIED:
                // 권한 재요청 필요
                break;
            case PROVIDER_DISABLED:
                // 다른 제공자로 전환 시도
                break;
            case ACCURACY_INSUFFICIENT:
                // 정확도 향상 대기
                break;
            default:
                // 기본 오류 처리
                break;
        }
    }

    /**
     * 위치 제공자 실패 처리
     */
    private void handleProviderFailure(EnvironmentType environment) {
        if (environment == EnvironmentType.INDOOR) {
            // 실내 추적 실패 시 실외 모드로 전환 시도
            if (gpsProvider.isTracking()) {
                forceEnvironment(EnvironmentType.OUTDOOR);
            }
        } else {
            // 실외 추적 실패 시 실내 모드로 전환 시도
            if (beaconProvider.isTracking()) {
                forceEnvironment(EnvironmentType.INDOOR);
            }
        }
    }

    /**
     * 환경 강제 설정
     */
    public void forceEnvironment(EnvironmentType environment) {
        if (System.currentTimeMillis() - lastProviderSwitchTime < MIN_PROVIDER_SWITCH_INTERVAL) {
            Log.d(TAG, "Provider switch blocked: too frequent");
            return;
        }

        isTransitioning = true;
        currentEnvironment.setValue(environment);
        stateManager.setIndoorMode(environment == EnvironmentType.INDOOR);

        switch (environment) {
            case INDOOR:
                handleIndoorTransition();
                break;
            case OUTDOOR:
                handleOutdoorTransition();
                break;
            case TRANSITION:
                handleTransitionState();
                break;
        }

        lastProviderSwitchTime = System.currentTimeMillis();
        isTransitioning = false;
    }

    /**
     * 실내 전환 처리
     */
    private void handleIndoorTransition() {
        // GPS 완전 비활성화
        gpsProvider.stopTracking();

        LocationData lastLocation = currentLocation.getValue();
        if (lastLocation != null) {
            // PDR 초기화 및 시작
            beaconProvider.setInitialLocation(lastLocation);
            beaconProvider.startTracking();
            Log.d(TAG, "Switched to indoor mode with initial location: " + lastLocation);
        } else {
            Log.w(TAG, "No initial location available for indoor transition");
        }
    }

    /**
     * 실외 전환 처리
     */
    private void handleOutdoorTransition() {
        // PDR 비활성화
        beaconProvider.stopTracking();

        // GPS 추적 시작
        gpsProvider.startTracking();
        Log.d(TAG, "Switched to outdoor mode");
    }

    /**
     * 전환 상태 처리
     */
    private void handleTransitionState() {
        // 두 제공자의 데이터를 모두 수집하여 부드러운 전환 준비
        LocationData gpsLocation = gpsProvider.getLastLocation();
        LocationData pdrLocation = beaconProvider.getLastLocation();

        if (gpsLocation != null && pdrLocation != null) {
            // 두 위치의 가중 평균 계산
            float gpsWeight = calculateGpsWeight(gpsLocation);
            LocationData blendedLocation = blendLocations(gpsLocation, pdrLocation, gpsWeight);

            if (blendedLocation != null) {
                currentLocation.setValue(blendedLocation);
                Log.d(TAG, "Updated transition location: " + blendedLocation);
            }
        }
    }

    /**
     * GPS 가중치 계산
     */
    private float calculateGpsWeight(@NonNull LocationData gpsLocation) {
        float signalStrength = gpsProvider.getCurrentSignalStrength();
        int satellites = gpsProvider.getVisibleSatellites();

        // -50dBm에서 -130dBm 사이를 0~1로 정규화
        float normalizedSignal = Math.min(1.0f, Math.max(0.0f,
                (signalStrength + 130) / 80));

        // 위성 수를 0~1로 정규화 (최소 4개, 최대 12개 기준)
        float normalizedSats = Math.min(1.0f, Math.max(0.0f,
                (satellites - 4) / 8.0f));

        // 신호 강도와 위성 수의 가중 평균
        return (normalizedSignal * 0.7f) + (normalizedSats * 0.3f);
    }

    /**
     * 두 위치의 가중 평균 계산
     */
    @Nullable
    private LocationData blendLocations(
            @NonNull LocationData location1,
            @NonNull LocationData location2,
            float weight1
    ) {
        try {
            double lat = (location1.getLatitude() * weight1) +
                    (location2.getLatitude() * (1 - weight1));
            double lng = (location1.getLongitude() * weight1) +
                    (location2.getLongitude() * (1 - weight1));
            float bearing = (location1.getBearing() * weight1) +
                    (location2.getBearing() * (1 - weight1));

            return new LocationData.Builder(lat, lng)
                    .accuracy(Math.max(location1.getAccuracy(), location2.getAccuracy()))
                    .bearing(bearing)
                    .environment(EnvironmentType.TRANSITION)
                    .provider("HYBRID")
                    .build();

        } catch (Exception e) {
            Log.e(TAG, "Error blending locations", e);
            return null;
        }
    }

    /**
     * 위치 추적 시작
     */
    public void startTracking() {
        if (!isInitialized.get()) {
            initialize();
        }

        if (isInitialized.get() && !isTracking.getAndSet(true)) {
            EnvironmentType currentEnv = currentEnvironment.getValue();
            LocationData initialLocation = currentLocation.getValue();

            if (currentEnv == EnvironmentType.INDOOR) {
                handleIndoorTransition();
            } else {
                handleOutdoorTransition();
            }

            Log.d(TAG, "Location tracking started in " + currentEnv + " mode");
        }
    }

    /**
     * 위치 추적 중지
     */
    public void stopTracking() {
        if (isTracking.getAndSet(false)) {
            gpsProvider.stopTracking();
            beaconProvider.stopTracking();
            Log.d(TAG, "Location tracking stopped");
        }
    }

    /**
     * 현재 위치 조회
     */
    public LiveData<LocationData> getCurrentLocation() {
        return currentLocation;
    }

    /**
     * 현재 환경 조회
     */
    public LiveData<EnvironmentType> getCurrentEnvironment() {
        return currentEnvironment;
    }

    // 디버깅 및 모니터링용 메서드들
    /**
     * PDR 걸음 수 조회
     */
    public int getStepCount() {
        return isPdrOperating() ? beaconProvider.getStepCount() : 0;
    }

    /**
     * PDR 이동 거리 조회
     */
    public double getDistanceTraveled() {
        return isPdrOperating() ? beaconProvider.getDistanceTraveled() : 0.0;
    }

    /**
     * 현재 방향 조회
     */
    public float getCurrentHeading() {
        LocationData location = currentLocation.getValue();
        return location != null ? location.getBearing() : -1;
    }

    /**
     * PDR 동작 여부 확인
     */
    public boolean isPdrOperating() {
        return beaconProvider != null && beaconProvider.isTracking();
    }

    /**
     * 감지된 비콘 수 조회
     */
    public int getDetectedBeaconCount() {
        return isPdrOperating() ? beaconProvider.getBeaconCount() : 0;
    }

    /**
     * GPS 신호 강도 조회
     */
    public float getCurrentSignalStrength() {
        return gpsProvider != null ? gpsProvider.getCurrentSignalStrength() : -160f;
    }

    /**
     * 가시 위성 수 조회
     */
    public int getVisibleSatellites() {
        return gpsProvider != null ? gpsProvider.getVisibleSatellites() : 0;
    }

    /**
     * GPS 사용 가능 여부 확인
     */
    public boolean isGpsAvailable() {
        return gpsProvider != null && gpsProvider.isTracking();
    }

    /**
     * PDR 시스템 초기화
     */
    public void resetPdrSystem() {
        if (isPdrOperating()) {
            LocationData current = currentLocation.getValue();
            if (current != null) {
                beaconProvider.setInitialLocation(current);
                beaconProvider.stopTracking();
                beaconProvider.startTracking();
                Log.d(TAG, "PDR system reset");
            }
        }
    }

    /**
     * 모든 리소스 정리
     */
    public void cleanup() {
        stopTracking();
        if (gpsProvider != null) {
            gpsProvider.stopTracking();
        }
        if (beaconProvider != null) {
            beaconProvider.cleanup();
        }
        isInitialized.set(false);

        // 상태 초기화
        stateManager.reset();
        currentLocation.setValue(null);
        currentEnvironment.setValue(EnvironmentType.OUTDOOR);

        Log.d(TAG, "LocationIntegrationManager cleaned up");
    }

    /**
     * 초기화 여부 확인
     */
    public boolean isInitialized() {
        return isInitialized.get();
    }

    /**
     * 추적 상태 확인
     */
    public boolean isTracking() {
        return isTracking.get();
    }

    /**
     * 데모 위치 업데이트 (테스트용)
     */
    public void updateDemoLocation(@NonNull LocationData location) {
        Log.d(TAG, "Updating demo location: " + location);

        try {
            // 환경 변화도 함께 처리
            EnvironmentType newEnvironment = location.getEnvironment();
            currentEnvironment.setValue(newEnvironment);

            // 위치 업데이트
            currentLocation.setValue(location);

            Log.d(TAG, "Demo location update successful");
        } catch (Exception e) {
            Log.e(TAG, "Error updating demo location", e);
        }
    }

    /**
     * 현재 상태 정보 조회
     */
    @NonNull
    public String getStatusInfo() {
        return String.format("Tracking: %s, Environment: %s, Indoor: %s, GPS Available: %s, PDR Operating: %s",
                isTracking.get(),
                currentEnvironment.getValue(),
                stateManager.getIndoorMode().getValue(),
                isGpsAvailable(),
                isPdrOperating());
    }
}