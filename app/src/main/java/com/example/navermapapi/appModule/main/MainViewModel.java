package com.example.navermapapi.appModule.main;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.example.navermapapi.coreModule.api.environment.model.EnvironmentType;
import com.example.navermapapi.coreModule.api.location.model.LocationData;
import com.naver.maps.geometry.LatLng;
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

    public void initialize() {
        // 초기화 로직
    }

    public void startTracking() {
        // 위치 추적 시작 로직
    }

    public void stopTracking() {
        // 위치 추적 중지 로직
    }
}