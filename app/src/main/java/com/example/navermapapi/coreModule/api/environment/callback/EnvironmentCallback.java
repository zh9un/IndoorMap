package com.example.navermapapi.coreModule.api.environment.callback;

import androidx.annotation.NonNull;
import androidx.annotation.MainThread;
import com.example.navermapapi.coreModule.api.environment.model.EnvironmentType;

public interface EnvironmentCallback {
    /**
     * 환경 변화가 감지되었을 때 호출되는 콜백
     * @param newEnvironment 새로 감지된 환경 타입
     * @param confidence 환경 판단의 신뢰도 (0.0 ~ 1.0)
     */
    @MainThread
    void onEnvironmentChanged(@NonNull EnvironmentType newEnvironment, float confidence);

    /**
     * 환경 감지 시스템의 상태가 변경되었을 때 호출
     * @param isAvailable 시스템 사용 가능 여부
     */
    @MainThread
    void onEnvironmentDetectionStateChanged(boolean isAvailable);

    /**
     * 환경 감지 중 오류 발생 시 호출
     * @param error 발생한 오류
     */
    @MainThread
    default void onError(@NonNull EnvironmentError error) {
        // Default implementation
    }

    /**
     * 환경 감지 관련 오류 정의
     */
    enum EnvironmentError {
        SENSOR_UNAVAILABLE("필요한 센서를 사용할 수 없습니다"),
        CALIBRATION_NEEDED("센서 보정이 필요합니다"),
        GPS_SIGNAL_UNSTABLE("GPS 신호가 불안정합니다"),
        MAGNETIC_INTERFERENCE("자기장 간섭이 감지되었습니다");

        private final String message;

        EnvironmentError(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }
    }

    /**
     * 환경 전환 진행 상태 업데이트 콜백
     * @param fromEnvironment 이전 환경
     * @param toEnvironment 전환 목표 환경
     * @param progress 전환 진행률 (0.0 ~ 1.0)
     */
    @MainThread
    default void onEnvironmentTransitionProgress(
            @NonNull EnvironmentType fromEnvironment,
            @NonNull EnvironmentType toEnvironment,
            float progress
    ) {
        // Optional implementation
    }
}