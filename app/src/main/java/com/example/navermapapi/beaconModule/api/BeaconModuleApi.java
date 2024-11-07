package com.example.navermapapi.beaconModule.api;

import android.content.Context;
import android.util.Log;
import com.example.navermapapi.beaconModule.utils.BeaconUtils;
import com.example.navermapapi.beaconModule.location.BeaconLocationProvider;
public class BeaconModuleApi {
    private static final String TAG = "BeaconModuleApi";
    private static BeaconModuleApi instance;

    private final Context context;
    private final BeaconLocationProvider locationProvider;
    private boolean isInitialized = false;

    private BeaconModuleApi(Context context) {
        this.context = context.getApplicationContext();
        this.locationProvider = new BeaconLocationProvider(context);
    }

    public static synchronized BeaconModuleApi getInstance(Context context) {
        if (instance == null) {
            instance = new BeaconModuleApi(context.getApplicationContext());
        }
        return instance;
    }

    public void initialize() {
        if (isInitialized) {
            Log.w(TAG, "BeaconModule is already initialized");
            return;
        }

        try {
            // 비콘 설정 로드
            BeaconUtils.loadBeaconConfig(context);

            // 필요한 권한 체크
            if (!checkRequiredPermissions()) {
                throw new IllegalStateException("Required permissions are not granted");
            }

            // Bluetooth 상태 체크
            if (!checkBluetoothStatus()) {
                throw new IllegalStateException("Bluetooth is not available");
            }

            isInitialized = true;
            Log.i(TAG, "BeaconModule initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize BeaconModule", e);
            throw new RuntimeException("BeaconModule initialization failed", e);
        }
    }

    public void start() {
        if (!isInitialized) {
            throw new IllegalStateException("BeaconModule is not initialized");
        }
        locationProvider.start();
    }

    public void stop() {
        if (isInitialized) {
            locationProvider.stop();
        }
    }

    public BeaconLocationProvider getLocationProvider() {
        return locationProvider;
    }

    private boolean checkRequiredPermissions() {
        // 필요한 권한 체크 로직
        // - Bluetooth
        // - 위치
        // - 활동 감지
        return true; // 실제 구현 필요
    }

    private boolean checkBluetoothStatus() {
        // Bluetooth 상태 체크 로직
        return true; // 실제 구현 필요
    }

    public boolean isInitialized() {
        return isInitialized;
    }

    public void setInitialFloor(int floor) {
        locationProvider.getFloorManager().setInitialFloor(floor);
    }

    public void setLocationCallback(BeaconLocationProvider.LocationCallback callback) {
        locationProvider.addCallback(callback);
    }

    public void removeLocationCallback(BeaconLocationProvider.LocationCallback callback) {
        locationProvider.removeCallback(callback);
    }
}