package com.example.navermapapi.gpsModule.internal.manager;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresPermission;

import com.example.navermapapi.coreModule.utils.filter.NoiseFilter;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * GPS 하드웨어 관리 및 위치 업데이트 처리
 * - GPS 센서 직접 제어
 * - 배터리 효율적인 위치 업데이트
 * - 위치 데이터 필터링
 */
public class GpsManager {
    private static final String TAG = "GpsManager";

    // 최소 업데이트 간격 및 거리
    private static final float MIN_DISTANCE = 1.0f;  // 미터
    private static final long MIN_TIME = 100L;       // 밀리초

    private final Context context;
    private final LocationManager locationManager;
    private final LocationUpdateCallback callback;
    private final Handler handler;
    private final NoiseFilter latitudeFilter;
    private final NoiseFilter longitudeFilter;
    private final AtomicBoolean isUpdating;

    private long updateInterval;
    private Location lastLocation;

    public interface LocationUpdateCallback {
        void onLocationChanged(@NonNull Location location);
    }

    @RequiresPermission(anyOf = {
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
    })
    public GpsManager(
            @NonNull Context context,
            @NonNull LocationUpdateCallback callback
    ) {
        this.context = context.getApplicationContext();
        this.callback = callback;
        this.locationManager = (LocationManager)
                context.getSystemService(Context.LOCATION_SERVICE);
        this.handler = new Handler(Looper.getMainLooper());
        this.isUpdating = new AtomicBoolean(false);

        // 위치 필터링을 위한 설정
        this.latitudeFilter = new NoiseFilter(5, 2.0);
        this.longitudeFilter = new NoiseFilter(5, 2.0);
    }

    private final LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(@NonNull Location location) {
            handleLocationUpdate(location);
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
            // API 29부터 deprecated되었지만 하위 버전 호환성을 위해 유지
        }

        @Override
        public void onProviderEnabled(@NonNull String provider) {
            // 위치 제공자 활성화 시 처리
        }

        @Override
        public void onProviderDisabled(@NonNull String provider) {
            // 위치 제공자 비활성화 시 처리
        }
    };

    @RequiresPermission(anyOf = {
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
    })
    public void startLocationUpdates(long interval) {
        if (!isUpdating.getAndSet(true)) {
            this.updateInterval = interval;

            try {
                locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        Math.max(MIN_TIME, interval),
                        MIN_DISTANCE,
                        locationListener,
                        Looper.getMainLooper()
                );
            } catch (SecurityException e) {
                isUpdating.set(false);
                // 권한 관련 예외 처리
            }
        }
    }

    public void stopLocationUpdates() {
        if (isUpdating.getAndSet(false)) {
            locationManager.removeUpdates(locationListener);
            handler.removeCallbacksAndMessages(null);
        }
    }

    @SuppressLint("MissingPermission")
    public void updateInterval(long interval) {
        if (isUpdating.get()) {
            stopLocationUpdates();
            startLocationUpdates(interval);
        }
    }

    private void handleLocationUpdate(@NonNull Location location) {
        // 정확도가 너무 낮은 위치는 무시
        if (location.getAccuracy() > 50.0f) {
            return;
        }

        // 위치 데이터 필터링
        double filteredLat = latitudeFilter.filter(location.getLatitude());
        double filteredLng = longitudeFilter.filter(location.getLongitude());

        location.setLatitude(filteredLat);
        location.setLongitude(filteredLng);

        // 이전 위치와 비교하여 급격한 변화 확인
        if (isValidLocationChange(location)) {
            lastLocation = location;

            // 메인 스레드에서 콜백 호출
            handler.post(() -> callback.onLocationChanged(location));
        }
    }

    private boolean isValidLocationChange(@NonNull Location newLocation) {
        if (lastLocation == null) {
            return true;
        }

        // 시간 차이가 너무 크면 유효하지 않음
        long timeDiff = newLocation.getTime() - lastLocation.getTime();
        if (timeDiff > updateInterval * 2) {
            return false;
        }

        // 속도를 고려한 최대 예상 거리 계산
        float maxDistance = (newLocation.hasSpeed() ? newLocation.getSpeed() : 5.0f)
                * (timeDiff / 1000.0f) * 1.5f;  // 50% 마진

        // 실제 거리가 예상 거리보다 크면 유효하지 않음
        float actualDistance = lastLocation.distanceTo(newLocation);
        return actualDistance <= maxDistance;
    }

    public boolean isTracking() {
        return isUpdating.get();
    }

    @RequiresPermission(anyOf = {
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
    })
    public Location getLastKnownLocation() {
        try {
            return locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        } catch (SecurityException e) {
            return null;
        }
    }
}