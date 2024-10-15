package com.example.navermapapi;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.ActivityCompat;

public class GPSManager {

    private static final String TAG = "GPSManager";
    private Context context;
    private LocationManager locationManager;
    private Location currentLocation;
    private static final float INDOOR_GPS_SIGNAL_THRESHOLD = 15.0f;
    private boolean isIndoor = false;
    private OnIndoorStatusChangeListener indoorStatusChangeListener;
    private OnLocationUpdateListener locationUpdateListener;
    private BuildingLocationManager buildingLocationManager;

    public interface OnIndoorStatusChangeListener {
        void onIndoorStatusChanged(boolean isIndoor, String buildingCoordinates);
    }

    public interface OnLocationUpdateListener {
        void onLocationUpdated(Location location);
    }

    public GPSManager(Context context) {
        this.context = context;
        locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        buildingLocationManager = new BuildingLocationManager();
    }

    public void setOnIndoorStatusChangeListener(OnIndoorStatusChangeListener listener) {
        this.indoorStatusChangeListener = listener;
    }

    public void setOnLocationUpdateListener(OnLocationUpdateListener listener) {
        this.locationUpdateListener = listener;
    }

    public Location getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "GPS 권한이 부여되지 않았습니다.");
            return null;
        }

        Location gpsLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        Location networkLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);

        if (gpsLocation != null && (networkLocation == null || gpsLocation.getTime() > networkLocation.getTime())) {
            currentLocation = gpsLocation;
            Log.d(TAG, "GPS 위치 사용");
        } else if (networkLocation != null) {
            currentLocation = networkLocation;
            Log.d(TAG, "네트워크 위치 사용");
        } else {
            Log.d(TAG, "사용 가능한 위치 정보 없음");
        }

        return currentLocation;
    }

    public Location getBuildingLocation() {
        return buildingLocationManager.getBuildingLocation();
    }

    public void startLocationUpdates() {
        Log.d(TAG, "startLocationUpdates 시작");
        try {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "위치 권한이 없습니다.");
                return;
            }
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 100, 1, locationListener, Looper.getMainLooper());
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 100, 1, locationListener, Looper.getMainLooper());
        } catch (Exception e) {
            Log.e(TAG, "위치 업데이트 시작 중 오류 발생", e);
        }
        Log.d(TAG, "startLocationUpdates 종료");
    }

    public void stopLocationUpdates() {
        locationManager.removeUpdates(locationListener);
    }

    private LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            currentLocation = location;
            Log.d(TAG, "위치 업데이트: " + location.toString());

            if (location.hasAccuracy()) {
                float accuracy = location.getAccuracy();
                boolean newIsIndoor = accuracy > INDOOR_GPS_SIGNAL_THRESHOLD;
                if (newIsIndoor != isIndoor) {
                    isIndoor = newIsIndoor;
                    if (indoorStatusChangeListener != null) {
                        if (isIndoor) {
                            buildingLocationManager.updateBuildingLocation(location);
                            indoorStatusChangeListener.onIndoorStatusChanged(true, buildingLocationManager.getBuildingCoordinates());
                        } else {
                            indoorStatusChangeListener.onIndoorStatusChanged(false, null);
                        }
                    }
                }
                if (!isIndoor) {
                    buildingLocationManager.updateBuildingLocation(location);
                }
            }

            if (locationUpdateListener != null) {
                locationUpdateListener.onLocationUpdated(location);
            }
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
            // Deprecated in API level 29
        }

        @Override
        public void onProviderEnabled(String provider) {
            // Not used
        }

        @Override
        public void onProviderDisabled(String provider) {
            // Not used
        }
    };

    public boolean isIndoor() {
        return isIndoor;
    }

    public String getBuildingCoordinates() {
        return buildingLocationManager.getBuildingCoordinates();
    }
}
