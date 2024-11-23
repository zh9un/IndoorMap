package com.example.navermapapi;

import android.app.Application;
import android.util.Log;

import dagger.hilt.android.HiltAndroidApp;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.naver.maps.map.NaverMapSdk;

@HiltAndroidApp
public class NavigationApplication extends Application {
    private static final String TAG = "NavigationApplication";

    @Override
    public void onCreate() {
        super.onCreate();
        initializeServices();
    }

    private void initializeServices() {
        try {
            // Naver Map 초기화
            NaverMapSdk.getInstance(this).setClient(
                    new NaverMapSdk.NaverCloudPlatformClient("9fk3x9j37i")
            );

            // Google Play Services 사용 가능 여부 확인
            if (!isGooglePlayServicesAvailable()) {
                Log.e(TAG, "Google Play Services not available");
                return;
            }

            Log.d(TAG, "Services initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error initializing services", e);
        }
    }

    private boolean isGooglePlayServicesAvailable() {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        int resultCode = apiAvailability.isGooglePlayServicesAvailable(this);
        return resultCode == ConnectionResult.SUCCESS;
    }
}