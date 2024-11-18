package com.example.navermapapi.coreModule.api.location.callback;

import androidx.annotation.NonNull;
import androidx.annotation.MainThread;
import com.example.navermapapi.coreModule.api.location.model.LocationData;
import com.example.navermapapi.coreModule.api.environment.model.EnvironmentType;

public interface LocationCallback {
    @MainThread
    void onLocationUpdate(@NonNull LocationData location);

    @MainThread
    void onProviderStateChanged(@NonNull String provider, boolean enabled);

    @MainThread
    default void onEnvironmentChanged(@NonNull EnvironmentType newEnvironment) {
        // Optional implementation
    }

    @MainThread
    default void onError(@NonNull LocationError error) {
        // Default implementation
    }

    enum LocationError {
        PERMISSION_DENIED("위치 권한이 없습니다"),
        PROVIDER_DISABLED("위치 제공자가 비활성화되었습니다"),
        TIMEOUT("위치 확인 시간이 초과되었습니다"),
        ACCURACY_INSUFFICIENT("위치 정확도가 불충분합니다"),
        GPS_SIGNAL_LOST("GPS 신호가 약합니다"),
        PDR_CALIBRATION_NEEDED("보행자 추측 항법 보정이 필요합니다");

        private final String message;

        LocationError(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }
    }
}