/*
 * 파일명: LocationStateManager.java
 * 경로: com.example.navermapapi.appModule.location.manager
 * 작성자: Claude
 * 작성일: 2024-11-28
 */

package com.example.navermapapi.appModule.location.manager;

import android.util.Log;
import androidx.annotation.NonNull;
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
 */
public class LocationStateManager {
    private static final String TAG = "LocationStateManager";

    // 상태 전환 관련 상수
    private static final float OUTDOOR_SIGNAL_THRESHOLD = -130.0f;  // dBm
    private static final float INDOOR_SIGNAL_THRESHOLD = -140.0f;   // dBm
    private static final int MIN_SATELLITES_OUTDOOR = 4;
    private static final int MIN_SATELLITES_INDOOR = 2;
    private static final long TRANSITION_HYSTERESIS = TimeUnit.SECONDS.toMillis(10);  // 10초
    private static final int HISTORY_SIZE = 5;  // 상태 이력 크기

    // 현재 상태
    private final MutableLiveData<EnvironmentType> currentEnvironment;
    private final MutableLiveData<LocationData> lastKnownLocation;

    // 상태 전환 이력
    private final CircularBuffer<StateTransition> transitionHistory;
    private long lastTransitionTime;
    private float signalStrengthAccumulator;
    private int samplesCount;

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
    }

    /**
     * 순환 버퍼 구현
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
    }

    public LocationStateManager() {
        this.currentEnvironment = new MutableLiveData<>(EnvironmentType.OUTDOOR);
        this.lastKnownLocation = new MutableLiveData<>();
        this.transitionHistory = new CircularBuffer<>(HISTORY_SIZE);
        this.lastTransitionTime = 0L;
        this.signalStrengthAccumulator = 0f;
        this.samplesCount = 0;
    }

    /**
     * GPS 신호 강도 업데이트 및 환경 판단
     */
    public void updateSignalStrength(float signalStrength, int satellites) {
        // 이동 평균 계산
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
    }

    /**
     * 마지막 위치 정보 업데이트
     */
    public void updateLocation(@NonNull LocationData location) {
        lastKnownLocation.setValue(location);
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
        return String.format("Environment: %s, Location: %s, Last Transition: %d ms ago",
                currentEnvironment.getValue(),
                location != null ? location.toString() : "unknown",
                System.currentTimeMillis() - lastTransitionTime);
    }
}