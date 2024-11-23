package com.example.navermapapi.gpsModule.api;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.location.GnssStatus;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.NonNull;
import android.content.pm.PackageManager;
import androidx.core.content.ContextCompat;
import javax.inject.Inject;
import javax.inject.Singleton;
import dagger.hilt.android.qualifiers.ApplicationContext;

import com.example.navermapapi.coreModule.api.location.callback.LocationCallback;
import com.example.navermapapi.coreModule.api.location.model.LocationData;
import com.example.navermapapi.coreModule.api.environment.model.EnvironmentType;
import com.example.navermapapi.gpsModule.internal.manager.GpsManager;
import com.example.navermapapi.gpsModule.internal.tracking.GpsTracker;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

@Singleton
public class GpsLocationProvider {
    private static final String TAG = "GpsLocationProvider";
    private static final long DEFAULT_UPDATE_INTERVAL = 1000L;

    private final Context context;
    private final GpsManager gpsManager;
    private final GpsTracker gpsTracker;
    private final CopyOnWriteArrayList<LocationCallback> callbacks;
    private final AtomicBoolean isTracking;
    private boolean isInitialized = false;

    private long updateInterval = DEFAULT_UPDATE_INTERVAL;
    private LocationData lastLocation;
    private float currentSignalStrength = -160f;
    private int visibleSatellites = 0;

    @Inject
    public GpsLocationProvider(@ApplicationContext Context context) {
        this.context = context.getApplicationContext();
        this.callbacks = new CopyOnWriteArrayList<>();
        this.isTracking = new AtomicBoolean(false);
        this.gpsManager = new GpsManager(context, this::handleLocationUpdate);
        this.gpsTracker = new GpsTracker(context, gpsManager);
        Log.d(TAG, "GpsLocationProvider constructed");
    }

    public void initialize() {
        if (isInitialized) {
            Log.d(TAG, "Already initialized");
            return;
        }

        if (!checkPermissions()) {
            Log.w(TAG, "Cannot initialize: permissions not granted");
            return;
        }

        try {
            initializeGnssMonitoring();
            isInitialized = true;
            Log.d(TAG, "GpsLocationProvider initialized successfully");
        } catch (SecurityException e) {
            Log.e(TAG, "Security exception during initialization", e);
            isInitialized = false;
        } catch (Exception e) {
            Log.e(TAG, "Error during initialization", e);
            isInitialized = false;
        }
    }

    private boolean checkPermissions() {
        boolean fineLocation = ContextCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean coarseLocation = ContextCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        return fineLocation || coarseLocation;
    }

    @SuppressLint("MissingPermission")
    private void initializeGnssMonitoring() {
        if (!checkPermissions()) {
            Log.w(TAG, "Cannot initialize GNSS monitoring: permissions not granted");
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            LocationManager locationManager = (LocationManager)
                    context.getSystemService(Context.LOCATION_SERVICE);

            if (locationManager == null) {
                Log.e(TAG, "LocationManager is null");
                return;
            }

            GnssStatus.Callback gnssStatusCallback = new GnssStatus.Callback() {
                @Override
                public void onSatelliteStatusChanged(@NonNull GnssStatus status) {
                    updateGpsStatus(status);
                }
            };

            try {
                locationManager.registerGnssStatusCallback(
                        gnssStatusCallback,
                        new Handler(Looper.getMainLooper())
                );
                Log.d(TAG, "GNSS monitoring initialized successfully");
            } catch (SecurityException e) {
                Log.e(TAG, "Security exception during GNSS monitoring initialization", e);
            }
        } else {
            Log.w(TAG, "GNSS status callbacks not supported on this Android version");
        }
    }

    private void updateGpsStatus(GnssStatus status) {
        if (!checkPermissions()) {
            Log.w(TAG, "Location permissions not granted");
            return;
        }

        float totalSignal = 0;
        int usedInFix = 0;

        for (int i = 0; i < status.getSatelliteCount(); i++) {
            if (status.usedInFix(i)) {
                totalSignal += status.getCn0DbHz(i);
                usedInFix++;
            }
        }

        visibleSatellites = status.getSatelliteCount();
        currentSignalStrength = usedInFix > 0 ? totalSignal / usedInFix : -160f;

        Log.d(TAG, String.format("GPS Status: satellites=%d, signal=%.2f",
                visibleSatellites, currentSignalStrength));

        updateEnvironmentIfNeeded();
    }

    private void handleLocationUpdate(@NonNull Location location) {
        if (!checkPermissions()) {
            Log.w(TAG, "Location permissions not granted");
            return;
        }

        try {
            LocationData locationData = new LocationData.Builder(
                    location.getLatitude(),
                    location.getLongitude()
            )
                    .accuracy(location.getAccuracy())
                    .altitude(location.hasAltitude() ? (float) location.getAltitude() : 0f)
                    .bearing(location.hasBearing() ? location.getBearing() : 0f)
                    .speed(location.hasSpeed() ? location.getSpeed() : 0f)
                    .environment(EnvironmentType.fromGpsSignal(currentSignalStrength, visibleSatellites))
                    .provider("GPS")
                    .confidence(calculateConfidence(location))
                    .build();

            lastLocation = locationData;
            notifyLocationChanged(locationData);
            Log.d(TAG, "Location updated: " + locationData);
        } catch (Exception e) {
            Log.e(TAG, "Error in handleLocationUpdate", e);
        }
    }

    private float calculateConfidence(Location location) {
        float accuracy = location.getAccuracy();
        float accuracyFactor = Math.max(0f, Math.min(1f, 50f / accuracy));
        float signalFactor = Math.max(0f, Math.min(1f, (currentSignalStrength + 160f) / ((-120f) + 160f)));
        float satelliteFactor = Math.min(1f, visibleSatellites / 8f);
        return (accuracyFactor + signalFactor + satelliteFactor) / 3f;
    }

    private void updateEnvironmentIfNeeded() {
        if (!checkPermissions()) {
            Log.w(TAG, "Location permissions not granted");
            return;
        }

        if (lastLocation != null) {
            EnvironmentType newEnvironment =
                    EnvironmentType.fromGpsSignal(currentSignalStrength, visibleSatellites);

            if (newEnvironment != lastLocation.getEnvironment()) {
                // 방법 1: Builder 생성자에 LocationData 전달
                lastLocation = new LocationData.Builder(lastLocation)
                        .environment(newEnvironment)
                        .build();

                // 또는 방법 2: 필드 수동 설정
                /*
                lastLocation = new LocationData.Builder(
                        lastLocation.getLatitude(),
                        lastLocation.getLongitude()
                )
                        .accuracy(lastLocation.getAccuracy())
                        .altitude(lastLocation.getAltitude())
                        .bearing(lastLocation.getBearing())
                        .speed(lastLocation.getSpeed())
                        .environment(newEnvironment)
                        .provider(lastLocation.getProvider())
                        .confidence(lastLocation.getConfidence())
                        .build();
                */

                for (LocationCallback callback : callbacks) {
                    callback.onEnvironmentChanged(newEnvironment);
                }
                Log.d(TAG, "Environment changed to: " + newEnvironment);
            }
        }
    }

    public void startTracking() {
        if (!checkPermissions()) {
            Log.w(TAG, "Cannot start tracking: location permission not granted");
            return;
        }

        if (!isInitialized) {
            initialize();
            if (!isInitialized) {
                Log.w(TAG, "Initialization failed, cannot start tracking");
                return;
            }
        }

        if (isTracking.compareAndSet(false, true)) {
            try {
                gpsManager.startLocationUpdates(updateInterval);
                gpsTracker.startTracking();
                Log.d(TAG, "Tracking started successfully");
            } catch (SecurityException e) {
                Log.e(TAG, "Security exception while starting tracking", e);
                isTracking.set(false);
            } catch (Exception e) {
                Log.e(TAG, "Error starting tracking", e);
                isTracking.set(false);
            }
        }
    }

    public void stopTracking() {
        if (isTracking.compareAndSet(true, false)) {
            gpsManager.stopLocationUpdates();
            gpsTracker.stopTracking();
            Log.d(TAG, "Tracking stopped");
        }
    }

    public LocationData getLastLocation() {
        if (!checkPermissions()) {
            Log.w(TAG, "Location permissions not granted");
            return null;
        }
        return lastLocation;
    }

    public void registerLocationCallback(@NonNull LocationCallback callback) {
        if (callback == null) {
            Log.w(TAG, "registerLocationCallback called with null callback");
            return;
        }

        if (!callbacks.contains(callback)) {
            callbacks.add(callback);

            if (lastLocation != null) {
                callback.onLocationUpdate(lastLocation);
            }
            Log.d(TAG, "Location callback registered");
        }
    }

    public void unregisterLocationCallback(@NonNull LocationCallback callback) {
        callbacks.remove(callback);
        Log.d(TAG, "Location callback unregistered");
    }

    private void notifyLocationChanged(LocationData location) {
        if (location == null) {
            Log.w(TAG, "notifyLocationChanged called with null location");
            return;
        }

        for (LocationCallback callback : callbacks) {
            try {
                callback.onLocationUpdate(location);
            } catch (Exception e) {
                Log.e(TAG, "Error notifying location callback", e);
            }
        }
    }

    public void setUpdateInterval(long interval) {
        this.updateInterval = Math.max(100L, interval);
        if (isTracking.get()) {
            gpsManager.updateInterval(updateInterval);
        }
        Log.d(TAG, "Update interval set to: " + updateInterval);
    }

    public boolean isTracking() {
        return isTracking.get();
    }

    public float getCurrentSignalStrength() {
        return currentSignalStrength;
    }

    public int getVisibleSatellites() {
        return visibleSatellites;
    }
}
