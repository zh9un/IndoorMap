package com.example.navermapapi.appModule.location.manager;

import android.Manifest;
import android.content.Context;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;
import com.naver.maps.geometry.LatLng;
import javax.inject.Inject;
import javax.inject.Singleton;
import dagger.hilt.android.qualifiers.ApplicationContext;
import java.util.concurrent.atomic.AtomicBoolean;

import com.example.navermapapi.coreModule.api.location.model.LocationData;
import com.example.navermapapi.coreModule.api.environment.model.EnvironmentType;
import com.example.navermapapi.gpsModule.api.GpsLocationProvider;
import com.example.navermapapi.beaconModule.api.BeaconLocationProvider;
import com.example.navermapapi.coreModule.api.location.callback.LocationCallback;

@Singleton
public class LocationIntegrationManager {
    private static final String TAG = "LocationIntegrationManager";

    // 전시장 영역 정의
    private static final LatLng EXHIBITION_CENTER = new LatLng(37.123456, 127.123456);
    private static final double EXHIBITION_RADIUS = 100.0;

    private final Context context;
    private final GpsLocationProvider gpsProvider;
    private final BeaconLocationProvider beaconProvider;
    private final MutableLiveData<LocationData> currentLocation;
    private final MutableLiveData<EnvironmentType> currentEnvironment;
    private final AtomicBoolean isInitialized;
    private final AtomicBoolean isConnecting;
    private GoogleApiClient googleApiClient;

    @Inject
    public LocationIntegrationManager(
            @ApplicationContext Context context,
            GpsLocationProvider gpsProvider,
            BeaconLocationProvider beaconProvider
    ) {
        this.context = context;
        this.gpsProvider = gpsProvider;
        this.beaconProvider = beaconProvider;
        this.currentLocation = new MutableLiveData<>();
        this.currentEnvironment = new MutableLiveData<>(EnvironmentType.OUTDOOR);
        this.isInitialized = new AtomicBoolean(false);
        this.isConnecting = new AtomicBoolean(false);

        initializeGoogleApiClient();
        setupLocationCallbacks();
    }

    private void initializeGoogleApiClient() {
        if (isConnecting.get()) return;
        isConnecting.set(true);

        try {
            googleApiClient = new GoogleApiClient.Builder(context)
                    .addApi(LocationServices.API)
                    .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
                        @Override
                        public void onConnected(@Nullable Bundle bundle) {
                            Log.d(TAG, "Google API Client connected");
                            isConnecting.set(false);
                            initialize();
                        }

                        @Override
                        public void onConnectionSuspended(int i) {
                            Log.w(TAG, "Google API Client connection suspended");
                            isConnecting.set(false);
                        }
                    })
                    .addOnConnectionFailedListener(connectionResult -> {
                        Log.e(TAG, "Google API Client connection failed: "
                                + connectionResult.getErrorMessage());
                        isConnecting.set(false);
                    })
                    .build();

            googleApiClient.connect();
        } catch (Exception e) {
            Log.e(TAG, "Error initializing Google API Client", e);
            isConnecting.set(false);
        }
    }

    public void initialize() {
        if (isInitialized.get()) {
            Log.d(TAG, "Already initialized");
            return;
        }

        if (!checkPermissions()) {
            Log.w(TAG, "Cannot initialize: permissions not granted");
            return;
        }

        try {
            gpsProvider.initialize();
            beaconProvider.initialize();
            isInitialized.set(true);
            Log.d(TAG, "LocationIntegrationManager initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error during initialization", e);
            isInitialized.set(false);
        }
    }

    private boolean checkPermissions() {
        return ContextCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_FINE_LOCATION) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    private void setupLocationCallbacks() {
        gpsProvider.registerLocationCallback(new LocationCallback() {
            @Override
            public void onLocationUpdate(@NonNull LocationData location) {
                handleGpsLocation(location);
            }

            @Override
            public void onProviderStateChanged(@NonNull String provider, boolean enabled) {
                if (!enabled) {
                    handleGpsProviderDisabled();
                }
            }
        });

        beaconProvider.registerLocationCallback(new LocationCallback() {
            @Override
            public void onLocationUpdate(@NonNull LocationData location) {
                handlePdrLocation(location);
            }

            @Override
            public void onProviderStateChanged(@NonNull String provider, boolean enabled) {
                if (!enabled) {
                    handleBeaconProviderDisabled();
                }
            }
        });
    }

    private void handleGpsLocation(LocationData location) {
        if (!checkPermissions()) return;

        EnvironmentType newEnvironment = location.getEnvironment();

        if (newEnvironment != currentEnvironment.getValue()) {
            handleEnvironmentTransition(newEnvironment, location);
        }

        if (newEnvironment == EnvironmentType.OUTDOOR) {
            currentLocation.setValue(location);
        }
    }

    private void handlePdrLocation(LocationData location) {
        if (currentEnvironment.getValue() == EnvironmentType.INDOOR) {
            currentLocation.setValue(location);
        }
    }

    private void handleEnvironmentTransition(EnvironmentType newEnvironment,
                                             LocationData location) {
        currentEnvironment.setValue(newEnvironment);

        switch (newEnvironment) {
            case INDOOR:
                startIndoorTracking(location);
                break;
            case OUTDOOR:
                startOutdoorTracking(location);
                break;
            case TRANSITION:
                handleTransitionState(location);
                break;
        }
    }

    private void startIndoorTracking(LocationData lastOutdoorLocation) {
        gpsProvider.stopTracking();
        beaconProvider.setInitialLocation(lastOutdoorLocation);
        beaconProvider.startTracking();
    }

    private void startOutdoorTracking(LocationData location) {
        beaconProvider.stopTracking();
        gpsProvider.startTracking();
    }

    private void handleTransitionState(LocationData location) {
        if (isNearExhibition(location)) {
            startIndoorTracking(location);
        } else {
            startOutdoorTracking(location);
        }
    }

    private boolean isNearExhibition(LocationData location) {
        float[] results = new float[1];
        Location.distanceBetween(
                location.getLatitude(), location.getLongitude(),
                EXHIBITION_CENTER.latitude, EXHIBITION_CENTER.longitude,
                results
        );
        return results[0] <= EXHIBITION_RADIUS;
    }

    private void handleGpsProviderDisabled() {
        if (currentEnvironment.getValue() == EnvironmentType.OUTDOOR) {
            LocationData lastLocation = currentLocation.getValue();
            if (lastLocation != null) {
                startIndoorTracking(lastLocation);
            }
        }
    }

    private void handleBeaconProviderDisabled() {
        if (currentEnvironment.getValue() == EnvironmentType.INDOOR) {
            startOutdoorTracking(currentLocation.getValue());
        }
    }

    public LiveData<LocationData> getCurrentLocation() {
        return currentLocation;
    }

    public LiveData<EnvironmentType> getCurrentEnvironment() {
        return currentEnvironment;
    }

    public void startTracking() {
        if (!isInitialized.get()) {
            initialize();
        }

        if (isInitialized.get()) {
            if (currentEnvironment.getValue() == EnvironmentType.INDOOR) {
                beaconProvider.startTracking();
            } else {
                gpsProvider.startTracking();
            }
        }
    }

    public void stopTracking() {
        gpsProvider.stopTracking();
        beaconProvider.stopTracking();
    }

    public void cleanup() {
        stopTracking();
        if (googleApiClient != null && googleApiClient.isConnected()) {
            googleApiClient.disconnect();
        }
    }

    public String getStatus() {
        return String.format(
                "Environment: %s\nLocation: %s\nInitialized: %s",
                currentEnvironment.getValue(),
                currentLocation.getValue(),
                isInitialized.get()
        );
    }
}