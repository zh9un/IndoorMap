/*
 * 파일명: LocationStateManager.java
 * 경로: com.example.navermapapi.appModule.location.manager
 * 작성자: Claude
 * 작성일: 2024-01-04
 */

package com.example.navermapapi.appModule.location.manager;

import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.navermapapi.coreModule.api.environment.model.EnvironmentType;
import com.example.navermapapi.coreModule.api.location.model.LocationData;

import java.util.concurrent.TimeUnit;

/**
 * 위치 추적 상태 및 환경 전환을 관리하는 클래스
 * - 실내외 전환 판단 로직
 * - 히스테리시스 기반 상태 안정화
 * - 전환 이력 관리
 * - GPS/PDR 모드 전환
 */
public class LocationStateManager {
    private static final String TAG = "LocationStateManager";

    // 상태 전환 관련 상수
    private LocationData initialGpsLocation;
    private static final float OUTDOOR_SIGNAL_THRESHOLD = -125.0f;  // dBm
    private static final float INDOOR_SIGNAL_THRESHOLD = -135.0f;   // dBm
    private static final int MIN_SATELLITES_OUTDOOR = 6;
    private static final int MIN_SATELLITES_INDOOR = 3;
    private static final long TRANSITION_HYSTERESIS = TimeUnit.SECONDS.toMillis(10);  // 10초
    private static final int HISTORY_SIZE = 5;  // 상태 이력 크기

    // 추가: 신호 품질 평가를 위한 상수
    private static final float MIN_HDOP = 1.0f;  // 수평 정밀도 희석도
    private static final float MAX_HDOP = 5.0f;
    private static final int SIGNAL_SAMPLES = 5;  // 샘플 수 증가 (3에서 상향)

    // 현재 상태
    private final MutableLiveData<EnvironmentType> currentEnvironment;
    private final MutableLiveData<LocationData> lastKnownLocation;
    private final MutableLiveData<Boolean> isIndoorMode;

    // 상태 전환 이력
    private final CircularBuffer<StateTransition> transitionHistory;
    private long lastTransitionTime;
    private float signalStrengthAccumulator;
    private int samplesCount;

    // 마지막 유효 위치 정보
    private LocationData lastValidGpsLocation;
    private LocationData lastValidPdrLocation;

    /**
     * 상태 전환 정보를 저장하는 내부 클래스
     */
    private static class StateTransition {
        final EnvironmentType fromState;
        final EnvironmentType toState;
        final long timestamp;
        final float signalStrength;
        final int satellites;

        StateTransition(EnvironmentType from, EnvironmentType to, float signal, int sats) {
            this.fromState = from;
            this.toState = to;
            this.timestamp = System.currentTimeMillis();
            this.signalStrength = signal;
            this.satellites = sats;
        }

        @Override
        @NonNull
        public String toString() {
            return String.format("Transition{%s -> %s, signal=%.1f, sats=%d, time=%d}",
                    fromState, toState, signalStrength, satellites, timestamp);
        }
    }

    /**
     * 순환 버퍼 구현
     * 상태 전환 이력을 저장하기 위한 고정 크기 버퍼
     */
    private static class CircularBuffer<T> {
        private final T[] buffer;
        private int writeIndex = 0;
        private final int size;

        @SuppressWarnings("unchecked")
        CircularBuffer(int size) {
            this.buffer = (T[]) new Object[size];
            this.size = size;
        }

        void add(T item) {
            buffer[writeIndex] = item;
            writeIndex = (writeIndex + 1) % size;
        }

        T[] getAll() {
            return buffer;
        }

        void clear() {
            for (int i = 0; i < size; i++) {
                buffer[i] = null;
            }
            writeIndex = 0;
        }
    }

    public LocationStateManager() {
        this.currentEnvironment = new MutableLiveData<>(EnvironmentType.OUTDOOR);
        this.lastKnownLocation = new MutableLiveData<>();
        this.isIndoorMode = new MutableLiveData<>(false);
        this.transitionHistory = new CircularBuffer<>(HISTORY_SIZE);
        this.lastTransitionTime = 0L;
        this.signalStrengthAccumulator = 0f;
        this.samplesCount = 0;

        Log.d(TAG, "LocationStateManager initialized");
    }

    /**
     * 실내 모드 설정
     * @param indoor true이면 실내 모드, false이면 실외 모드
     */
    public void setIndoorMode(boolean indoor) {
        if (indoor != isIndoorMode.getValue()) {
            isIndoorMode.setValue(indoor);
            if (indoor) {
                // 마지막 유효 GPS 위치 저장
                LocationData current = lastKnownLocation.getValue();
                if (current != null && "GPS".equals(current.getProvider())) {
                    lastValidGpsLocation = current;
                    Log.d(TAG, "Stored last valid GPS location: " + lastValidGpsLocation);
                }
            } else {
                // 마지막 유효 PDR 위치 저장
                LocationData current = lastKnownLocation.getValue();
                if (current != null && "PDR".equals(current.getProvider())) {
                    lastValidPdrLocation = current;
                    Log.d(TAG, "Stored last valid PDR location: " + lastValidPdrLocation);
                }
            }
            Log.d(TAG, "Indoor mode changed to: " + indoor);
        }
    }

    /**
     * 현재 실내 모드 상태 관찰을 위한 LiveData 반환
     */
    public LiveData<Boolean> getIndoorMode() {
        return isIndoorMode;
    }

    /**
     * 마지막 유효 GPS 위치 반환
     */
    @Nullable
    public LocationData getLastValidGpsLocation() {
        return lastValidGpsLocation;
    }

    /**
     * 마지막 유효 PDR 위치 반환
     */
    @Nullable
    public LocationData getLastValidPdrLocation() {
        return lastValidPdrLocation;
    }

    /**
     * 초기 GPS 위치를 설정 (최초 한 번만 저장)
     */
    public void setInitialGpsLocation(@NonNull LocationData location) {
        if (initialGpsLocation == null && "GPS".equals(location.getProvider())) {
            initialGpsLocation = location;
            Log.d(TAG, "Initial GPS location set: " + location);
        }
    }

    /**
     * 초기 GPS 위치 반환
     */
    @Nullable
    public LocationData getInitialGpsLocation() {
        return initialGpsLocation;
    }

    /**
     * GPS 신호 강도 업데이트 및 환경 판단
     * 실내 모드일 때는 GPS 신호 처리하지 않음
     */
    public void updateSignalStrength(float signalStrength, int satellites) {
        if (Boolean.TRUE.equals(isIndoorMode.getValue())) {
            return;  // 실내 모드에서는 GPS 신호 무시
        }

        signalStrengthAccumulator += signalStrength;
        samplesCount++;

        if (samplesCount >= 3) {  // 3샘플 이상 수집 시 판단
            float averageSignal = signalStrengthAccumulator / samplesCount;
            evaluateEnvironment(averageSignal, satellites);

            // 평균 초기화
            signalStrengthAccumulator = 0f;
            samplesCount = 0;
        }
    }

    /**
     * 환경 상태 평가 및 전환 처리
     */
    private void evaluateEnvironment(float signalStrength, int satellites) {
        EnvironmentType currentType = currentEnvironment.getValue();
        if (currentType == null) return;

        EnvironmentType newType = determineEnvironmentType(signalStrength, satellites, currentType);

        if (newType != currentType && shouldTransition(newType)) {
            performTransition(currentType, newType, signalStrength, satellites);
        }
    }

    /**
     * 현재 조건에 따른 환경 타입 결정
     */
    private EnvironmentType determineEnvironmentType(
            float signalStrength,
            int satellites,
            EnvironmentType currentType) {

        // 히스테리시스 적용
        if (currentType == EnvironmentType.OUTDOOR) {
            // 실외->실내 전환은 더 엄격한 조건 적용
            if (signalStrength < INDOOR_SIGNAL_THRESHOLD || satellites < MIN_SATELLITES_INDOOR) {
                return EnvironmentType.INDOOR;
            }
        } else if (currentType == EnvironmentType.INDOOR) {
            // 실내->실외 전환은 더 확실한 조건 필요
            if (signalStrength > OUTDOOR_SIGNAL_THRESHOLD && satellites >= MIN_SATELLITES_OUTDOOR) {
                return EnvironmentType.OUTDOOR;
            }
        }

        // 전환 중인 경우
        if (currentType == EnvironmentType.TRANSITION) {
            if (signalStrength > OUTDOOR_SIGNAL_THRESHOLD && satellites >= MIN_SATELLITES_OUTDOOR) {
                return EnvironmentType.OUTDOOR;
            } else if (signalStrength < INDOOR_SIGNAL_THRESHOLD || satellites < MIN_SATELLITES_INDOOR) {
                return EnvironmentType.INDOOR;
            }
        }

        return currentType;
    }

    /**
     * 상태 전환이 적절한지 판단
     */
    private boolean shouldTransition(EnvironmentType newType) {
        long currentTime = System.currentTimeMillis();

        // 최소 전환 간격 확인
        if (currentTime - lastTransitionTime < TRANSITION_HYSTERESIS) {
            return false;
        }

        // 이전 전환 이력 분석
        StateTransition[] history = transitionHistory.getAll();
        int rapidTransitions = 0;

        for (StateTransition transition : history) {
            if (transition != null &&
                    currentTime - transition.timestamp < TimeUnit.MINUTES.toMillis(1)) {
                rapidTransitions++;
            }
        }

        // 1분 내 3번 이상 전환 시도 시 차단
        return rapidTransitions < 3;
    }

    /**
     * 상태 전환 수행
     */
    private void performTransition(
            EnvironmentType fromState,
            EnvironmentType toState,
            float signalStrength,
            int satellites) {

        // 전환 이력 기록
        transitionHistory.add(new StateTransition(fromState, toState, signalStrength, satellites));
        lastTransitionTime = System.currentTimeMillis();

        // 상태 업데이트 및 로깅
        Log.d(TAG, String.format("Environment transition: %s -> %s (signal: %.1f, sats: %d)",
                fromState, toState, signalStrength, satellites));

        currentEnvironment.setValue(toState);
        setIndoorMode(toState == EnvironmentType.INDOOR);
    }

    /**
     * 위치 정보 업데이트
     */
    public void updateLocation(@NonNull LocationData location) {
        boolean indoor = Boolean.TRUE.equals(isIndoorMode.getValue());

        // 실내 모드일 때는 GPS 데이터 무시
        if (indoor && "GPS".equals(location.getProvider())) {
            Log.d(TAG, "Ignoring GPS location in indoor mode: " + location);
            return;
        }

        // 실외 모드일 때는 PDR 데이터 무시
        if (!indoor && "PDR".equals(location.getProvider())) {
            Log.d(TAG, "Ignoring PDR location in outdoor mode: " + location);
            return;
        }

        lastKnownLocation.setValue(location);
        Log.d(TAG, "Location updated: " + location);
    }

    /**
     * 현재 환경 상태 조회
     */
    public LiveData<EnvironmentType> getCurrentEnvironment() {
        return currentEnvironment;
    }

    /**
     * 마지막 알려진 위치 조회
     */
    public LiveData<LocationData> getLastKnownLocation() {
        return lastKnownLocation;
    }

    /**
     * 현재 상태 정보 조회
     */
    @NonNull
    public String getStatusInfo() {
        LocationData location = lastKnownLocation.getValue();
        return String.format("Environment: %s, Indoor: %s, Location: %s, Last Transition: %d ms ago",
                currentEnvironment.getValue(),
                isIndoorMode.getValue(),
                location != null ? location.toString() : "unknown",
                System.currentTimeMillis() - lastTransitionTime);
    }

    /**
     * 모든 상태 초기화
     */
    public void reset() {
        currentEnvironment.setValue(EnvironmentType.OUTDOOR);
        isIndoorMode.setValue(false);
        lastKnownLocation.setValue(null);
        initialGpsLocation = null;
        lastValidGpsLocation = null;
        lastValidPdrLocation = null;
        transitionHistory.clear();
        lastTransitionTime = 0L;
        signalStrengthAccumulator = 0f;
        samplesCount = 0;

        Log.d(TAG, "LocationStateManager reset");
    }
}