package com.example.navermapapi.gpsModule.internal.tracking;

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Location;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.navermapapi.coreModule.utils.filter.NoiseFilter;
import com.example.navermapapi.gpsModule.internal.manager.GpsManager;
import com.naver.maps.geometry.LatLng;
import com.naver.maps.map.LocationSource;
import com.naver.maps.map.overlay.LocationOverlay;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * GPS 추적 및 경로 관리
 * - 위치 데이터 필터링
 * - 이동 경로 기록 및 관리
 * - 네이버 맵 연동
 */
public class GpsTracker implements LocationSource {
    private static final String TAG = "GpsTracker";

    // 경로 관리 설정
    private static final int MAX_PATH_POINTS = 1000;  // 최대 경로 포인트 수
    private static final long MAX_POINT_AGE = TimeUnit.MINUTES.toMillis(30);  // 30분
    private static final float MIN_DISTANCE_BETWEEN_POINTS = 2.0f;  // 미터

    private final Context context;
    private final GpsManager gpsManager;
    private final NoiseFilter bearingFilter;
    private final NoiseFilter speedFilter;
    private final LinkedList<TrackedLocation> locationHistory;

    private LocationOverlay locationOverlay;
    private OnLocationChangedListener mapListener;
    private boolean isTracking;

    /**
     * 추적된 위치 정보를 저장하는 내부 클래스
     */
    private static class TrackedLocation {
        final Location location;
        final long timestamp;

        TrackedLocation(Location location) {
            this.location = location;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > MAX_POINT_AGE;
        }
    }

    public GpsTracker(@NonNull Context context, @NonNull GpsManager gpsManager) {
        this.context = context.getApplicationContext();
        this.gpsManager = gpsManager;
        this.bearingFilter = new NoiseFilter(10, 2.0);  // 방향 필터링
        this.speedFilter = new NoiseFilter(5, 2.0);     // 속도 필터링
        this.locationHistory = new LinkedList<>();
    }

    /**
     * 위치 추적 시작
     */
    @SuppressLint("MissingPermission")
    public void startTracking() {
        if (!isTracking) {
            isTracking = true;
            gpsManager.startLocationUpdates(1000);  // 1초 간격으로 업데이트
        }
    }

    /**
     * 위치 추적 중지
     */
    public void stopTracking() {
        if (isTracking) {
            isTracking = false;
            gpsManager.stopLocationUpdates();
        }
    }

    /**
     * 새로운 위치 업데이트 처리
     */
    private void handleLocationUpdate(@NonNull Location location) {
        // 위치 데이터 필터링
        Location filteredLocation = filterLocation(location);

        // 경로에 추가
        updateLocationHistory(filteredLocation);

        // 지도 업데이트
        updateMapLocation(filteredLocation);
    }

    /**
     * 위치 데이터 필터링
     */
    private Location filterLocation(@NonNull Location location) {
        Location filtered = new Location(location);

        // 방향 필터링
        if (location.hasBearing()) {
            filtered.setBearing((float) bearingFilter.filter(location.getBearing()));
        }

        // 속도 필터링
        if (location.hasSpeed()) {
            filtered.setSpeed((float) speedFilter.filter(location.getSpeed()));
        }

        return filtered;
    }

    /**
     * 위치 이력 업데이트
     */
    private void updateLocationHistory(@NonNull Location location) {
        // 오래된 포인트 제거
        removeExpiredPoints();

        // 이전 위치와의 거리가 최소 거리보다 큰 경우에만 추가
        if (shouldAddNewPoint(location)) {
            locationHistory.addLast(new TrackedLocation(location));

            // 최대 포인트 수 제한
            while (locationHistory.size() > MAX_PATH_POINTS) {
                locationHistory.removeFirst();
            }
        }
    }

    /**
     * 새로운 위치 포인트를 추가해야 하는지 확인
     */
    private boolean shouldAddNewPoint(@NonNull Location location) {
        if (locationHistory.isEmpty()) {
            return true;
        }

        Location lastLocation = locationHistory.getLast().location;
        return lastLocation.distanceTo(location) >= MIN_DISTANCE_BETWEEN_POINTS;
    }

    /**
     * 오래된 포인트 제거
     */
    private void removeExpiredPoints() {
        while (!locationHistory.isEmpty() && locationHistory.getFirst().isExpired()) {
            locationHistory.removeFirst();
        }
    }

    /**
     * 지도상의 위치 업데이트
     */
    private void updateMapLocation(@NonNull Location location) {
        // Naver Map 리스너 업데이트
        if (mapListener != null) {
            mapListener.onLocationChanged(location);
        }

        // 위치 오버레이 업데이트
        if (locationOverlay != null) {
            locationOverlay.setPosition(new LatLng(
                    location.getLatitude(),
                    location.getLongitude()
            ));

            if (location.hasBearing()) {
                locationOverlay.setBearing(location.getBearing());
            }
        }
    }

    /**
     * 현재까지의 이동 경로 반환
     */
    @NonNull
    public List<LatLng> getPathPoints() {
        List<LatLng> points = new ArrayList<>(locationHistory.size());
        for (TrackedLocation tracked : locationHistory) {
            points.add(new LatLng(
                    tracked.location.getLatitude(),
                    tracked.location.getLongitude()
            ));
        }
        return points;
    }

    /**
     * 위치 오버레이 설정
     */
    public void setLocationOverlay(@Nullable LocationOverlay overlay) {
        this.locationOverlay = overlay;
    }

    /**
     * LocationSource 인터페이스 구현
     */
    @Override
    public void activate(@NonNull OnLocationChangedListener listener) {
        mapListener = listener;

        // 마지막 알려진 위치로 초기화
        @SuppressLint("MissingPermission") Location lastLocation = gpsManager.getLastKnownLocation();
        if (lastLocation != null) {
            listener.onLocationChanged(lastLocation);
        }
    }

    @Override
    public void deactivate() {
        mapListener = null;
    }

    /**
     * 평균 이동 속도 계산 (최근 10개 포인트 기준)
     */
    public float getAverageSpeed() {
        if (locationHistory.size() < 2) {
            return 0.0f;
        }

        float totalSpeed = 0.0f;
        int count = 0;

        List<TrackedLocation> recent = new ArrayList<>(
                locationHistory.subList(
                        Math.max(0, locationHistory.size() - 10),
                        locationHistory.size()
                )
        );

        for (int i = 1; i < recent.size(); i++) {
            Location prev = recent.get(i-1).location;
            Location curr = recent.get(i).location;

            float distance = prev.distanceTo(curr);
            long timeDiff = curr.getTime() - prev.getTime();

            if (timeDiff > 0) {
                totalSpeed += (distance / timeDiff) * 1000;  // m/s로 변환
                count++;
            }
        }

        return count > 0 ? totalSpeed / count : 0.0f;
    }

    /**
     * 이동 거리 계산
     */
    public float getTotalDistance() {
        if (locationHistory.size() < 2) {
            return 0.0f;
        }

        float distance = 0.0f;
        Location prev = null;

        for (TrackedLocation tracked : locationHistory) {
            if (prev != null) {
                distance += prev.distanceTo(tracked.location);
            }
            prev = tracked.location;
        }

        return distance;
    }

    /**
     * 추적 상태 확인
     */
    public boolean isTracking() {
        return isTracking;
    }
}