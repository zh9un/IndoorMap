package com.example.navermapapi.appModule.main;

import static androidx.fragment.app.FragmentManager.TAG;

import android.annotation.SuppressLint;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.navermapapi.R;
import com.example.navermapapi.constants.ExhibitionConstants;
import com.example.navermapapi.coreModule.api.environment.model.EnvironmentType;
import com.example.navermapapi.coreModule.api.location.model.LocationData;
import com.naver.maps.geometry.LatLng;
import com.naver.maps.map.CameraAnimation;
import com.naver.maps.map.CameraUpdate;
import com.naver.maps.map.NaverMap;
import com.naver.maps.map.overlay.LocationOverlay;
import com.naver.maps.map.overlay.OverlayImage;

import java.util.List;
import dagger.hilt.android.lifecycle.HiltViewModel;
import javax.inject.Inject;

@HiltViewModel
public class MainViewModel extends ViewModel {
    private final MutableLiveData<LocationData> currentLocation = new MutableLiveData<>();
    private final MutableLiveData<EnvironmentType> currentEnvironment = new MutableLiveData<>();
    private final MutableLiveData<String> errorState = new MutableLiveData<>();
    private final MutableLiveData<LatLng> destination = new MutableLiveData<>();
    private final MutableLiveData<List<LatLng>> path = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isDemoMode = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> isNavigating = new MutableLiveData<>(false);
    private final MutableLiveData<Float> currentAzimuth = new MutableLiveData<>();

    public LiveData<Float> getCurrentAzimuth() {
        return currentAzimuth;
    }

    public void setCurrentAzimuth(float azimuth) {
        currentAzimuth.setValue(azimuth);
    }
    private final MutableLiveData<Integer> currentDemoStep = new MutableLiveData<>(0);

    public LiveData<Integer> getCurrentDemoStep() {
        return currentDemoStep;
    }

    public void startDemo() {
        isDemoMode.setValue(true);
        currentDemoStep.setValue(0);
    }

    public void stopDemo() {
        isDemoMode.setValue(false);
        currentDemoStep.setValue(0);
    }

    public void nextDemoStep() {
        Integer current = currentDemoStep.getValue();
        if (current != null && current < ExhibitionConstants.DEMO_SCENARIOS.length - 1) {
            currentDemoStep.setValue(current + 1);
        }
    }

    private boolean autoTrackingEnabled = true;
    private boolean destinationSelectionEnabled = false;

    @Inject
    public MainViewModel() {
    }

    public LiveData<LocationData> getCurrentLocation() {
        return currentLocation;
    }

    public LiveData<EnvironmentType> getCurrentEnvironment() {
        return currentEnvironment;
    }

    public LiveData<String> getErrorState() {
        return errorState;
    }

    public LiveData<LatLng> getDestination() {
        return destination;
    }

    public LiveData<List<LatLng>> getPath() {
        return path;
    }

    public LiveData<Boolean> isDemoMode() {
        return isDemoMode;
    }

    public LiveData<Boolean> isNavigating() {
        return isNavigating;
    }
    // 지도 업데이트 관련 메서드 추가
    @SuppressLint("RestrictedApi")
    public void updateMapLocation(NaverMap naverMap, LocationOverlay locationOverlay, LocationData location) {
        if (location == null || naverMap == null || locationOverlay == null) {
            return;
        }

        try {
            LatLng position = new LatLng(location.getLatitude(), location.getLongitude());
            locationOverlay.setPosition(position);
            locationOverlay.setBearing(location.getBearing());

            if (isAutoTrackingEnabled()) {
                CameraUpdate cameraUpdate = CameraUpdate.scrollTo(position)
                        .animate(CameraAnimation.Easing);
                naverMap.moveCamera(cameraUpdate);
            }

            // 환경에 따라 마커 스타일 변경
            if (location.getEnvironment() == EnvironmentType.INDOOR) {
                locationOverlay.setIcon(OverlayImage.fromResource(R.drawable.ic_indoor_location));
            } else {
                locationOverlay.setIcon(OverlayImage.fromResource(R.drawable.ic_outdoor_location));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating map location", e);
        }
    }
    public boolean isAutoTrackingEnabled() {
        return autoTrackingEnabled;
    }

    public boolean isDestinationSelectionEnabled() {
        return destinationSelectionEnabled;
    }

    public void setAutoTrackingEnabled(boolean enabled) {
        this.autoTrackingEnabled = enabled;
    }

    public void setDestinationSelectionEnabled(boolean enabled) {
        this.destinationSelectionEnabled = enabled;
    }

    public void setDestination(LatLng destination) {
        this.destination.setValue(destination);
    }

    public void setPath(List<LatLng> path) {
        this.path.setValue(path);
    }

    public void setDemoMode(boolean isDemoMode) {
        this.isDemoMode.setValue(isDemoMode);
    }

    public void setNavigating(boolean isNavigating) {
        this.isNavigating.setValue(isNavigating);
    }

    public void updateCurrentLocation(LocationData location) {
        currentLocation.setValue(location);
    }

    public void updateEnvironment(EnvironmentType environment) {
        currentEnvironment.setValue(environment);
    }

    public void setError(String error) {
        errorState.setValue(error);
    }

    public void clearError() {
        errorState.setValue(null);
    }
    @SuppressLint("RestrictedApi")
    public void updateLocationAndEnvironment(LocationData location) {
        Log.d(TAG, "Updating location and environment in ViewModel");
        currentLocation.setValue(location);
        currentEnvironment.setValue(location.getEnvironment());
    }
}