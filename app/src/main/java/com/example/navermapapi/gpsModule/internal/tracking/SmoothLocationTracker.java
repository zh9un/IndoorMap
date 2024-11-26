package com.example.navermapapi.gpsModule.internal.tracking;

import android.animation.ValueAnimator;
import android.content.Context;
import android.location.Location;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.animation.LinearInterpolator;

import androidx.annotation.NonNull;

import com.naver.maps.geometry.LatLng;
import com.naver.maps.map.CameraAnimation;
import com.naver.maps.map.CameraUpdate;
import com.naver.maps.map.NaverMap;
import com.naver.maps.map.overlay.LocationOverlay;
import com.example.navermapapi.coreModule.utils.filter.NoiseFilter;

/**
 * 부드러운 위치 및 방향 업데이트를 위한 트래커
 */
public class SmoothLocationTracker {
    private static final String TAG = "SmoothLocationTracker";

    private static final long UPDATE_INTERVAL_MS = 5L; // ~200fps
    private static final long ANIMATION_DURATION_MS = 150L;
    private static final float MIN_DISTANCE_THRESHOLD = 0.1f;
    private static final float MIN_BEARING_THRESHOLD = 1.0f;
    private static final float INTERPOLATION_WEIGHT = 0.7f;
    private static final float BEARING_SMOOTHING_FACTOR = 0.6f;

    private final Handler mainHandler;
    private final NoiseFilter bearingFilter;
    private final NoiseFilter latitudeFilter;
    private final NoiseFilter longitudeFilter;
    private NaverMap naverMap;
    private LocationOverlay locationOverlay;

    private Location currentLocation;
    private Location targetLocation;
    private boolean isTracking = false;
    private float lastBearing;

    private ValueAnimator bearingAnimator;

    public SmoothLocationTracker(@NonNull Context context) {
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.bearingFilter = new NoiseFilter(5, 1.2);
        this.latitudeFilter = new NoiseFilter(3, 1.5);
        this.longitudeFilter = new NoiseFilter(3, 1.5);
    }

    public void setNaverMap(@NonNull NaverMap naverMap) {
        this.naverMap = naverMap;
        this.locationOverlay = naverMap.getLocationOverlay();
        this.locationOverlay.setVisible(true);
    }

    public void updateLocation(@NonNull Location location, boolean animate) {
        if (currentLocation == null) {
            updateLocationImmediate(location);
            return;
        }

        targetLocation = filterLocation(location);
        if (animate) {
            interpolateLocation();
        } else {
            updateLocationImmediate(targetLocation);
        }
    }

    private Location filterLocation(@NonNull Location location) {
        double filteredLat = latitudeFilter.filter(location.getLatitude());
        double filteredLng = longitudeFilter.filter(location.getLongitude());
        float filteredBearing = (float) bearingFilter.filter(location.getBearing());

        Location filtered = new Location(location);
        filtered.setLatitude(filteredLat);
        filtered.setLongitude(filteredLng);
        filtered.setBearing(filteredBearing);

        return filtered;
    }

    private void interpolateLocation() {
        if (targetLocation == null || currentLocation == null) return;

        LatLng startPosition = new LatLng(currentLocation.getLatitude(), currentLocation.getLongitude());
        LatLng targetPosition = new LatLng(targetLocation.getLatitude(), targetLocation.getLongitude());

        double latDiff = targetPosition.latitude - startPosition.latitude;
        double lngDiff = targetPosition.longitude - startPosition.longitude;

        double interpolatedLat = startPosition.latitude + latDiff * INTERPOLATION_WEIGHT;
        double interpolatedLng = startPosition.longitude + lngDiff * INTERPOLATION_WEIGHT;

        float bearingDiff = targetLocation.getBearing() - currentLocation.getBearing();
        if (Math.abs(bearingDiff) > 180) {
            bearingDiff = bearingDiff > 0 ? bearingDiff - 360 : bearingDiff + 360;
        }
        float interpolatedBearing = currentLocation.getBearing() + bearingDiff * BEARING_SMOOTHING_FACTOR;

        Location interpolatedLocation = new Location("interpolated");
        interpolatedLocation.setLatitude(interpolatedLat);
        interpolatedLocation.setLongitude(interpolatedLng);
        interpolatedLocation.setBearing(interpolatedBearing);

        updateLocationImmediate(interpolatedLocation);
    }

    private void updateLocationImmediate(@NonNull Location location) {
        currentLocation = location;
        mainHandler.post(() -> {
            if (locationOverlay != null) {
                LatLng position = new LatLng(location.getLatitude(), location.getLongitude());
                locationOverlay.setPosition(position);
                locationOverlay.setBearing(location.getBearing());

                CameraUpdate cameraUpdate = CameraUpdate.scrollTo(position).animate(CameraAnimation.Easing, ANIMATION_DURATION_MS);
                naverMap.moveCamera(cameraUpdate);
            }
        });
    }

    public void startTracking() {
        if (!isTracking) {
            isTracking = true;
            Log.d(TAG, "Tracking started");
        }
    }

    public void stopTracking() {
        if (isTracking) {
            isTracking = false;
            if (bearingAnimator != null) {
                bearingAnimator.cancel();
            }
            Log.d(TAG, "Tracking stopped");
        }
    }

    /**
     * 리소스 정리 및 추적 중지
     */
    public void destroy() {
        stopTracking();
        mainHandler.removeCallbacksAndMessages(null);
        currentLocation = null;
        targetLocation = null;
        Log.d(TAG, "SmoothLocationTracker destroyed");
    }
}
