package com.example.navermapapi;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;

import androidx.core.app.ActivityCompat;

public class GPSManager {

    private Context context;
    private LocationManager locationManager;
    private Location currentLocation;
    private static final float INDOOR_GPS_SIGNAL_THRESHOLD = 15.0f;
    private boolean isIndoor = false;
    private OnIndoorStatusChangeListener indoorStatusChangeListener;

    public interface OnIndoorStatusChangeListener {
        void onIndoorStatusChanged(boolean isIndoor);
    }

    public GPSManager(Context context) {
        this.context = context;
        locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
    }

    public void setOnIndoorStatusChangeListener(OnIndoorStatusChangeListener listener) {
        this.indoorStatusChangeListener = listener;
    }

    public Location getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.d("GPSManager", "GPS 권한이 부여되지 않았습니다.");
            return null;
        }

        Location gpsLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        Location networkLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);

        if (gpsLocation != null) {
            currentLocation = gpsLocation;
            Log.d("GPSManager", "GPS 위치 사용");
        } else if (networkLocation != null) {
            currentLocation = networkLocation;
            Log.d("GPSManager", "네트워크 위치 사용");
        } else {
            Log.d("GPSManager", "사용 가능한 위치 정보 없음");
        }

        return currentLocation;
    }

    public void startLocationUpdates() {
        Log.d("GPSManager", "startLocationUpdates 시작");
        try {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                Log.e("GPSManager", "위치 권한이 없습니다.");
                return;
            }
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 10, locationListener);
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000, 10, locationListener);
        } catch (Exception e) {
            Log.e("GPSManager", "위치 업데이트 시작 중 오류 발생", e);
        }
        Log.d("GPSManager", "startLocationUpdates 종료");
    }

    public void stopLocationUpdates() {
        locationManager.removeUpdates(locationListener);
    }

    private LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            currentLocation = location;
            Log.d("GPSManager", "위치 업데이트: " + location.toString());

            if (location.hasAccuracy()) {
                float accuracy = location.getAccuracy();
                boolean newIsIndoor = accuracy > INDOOR_GPS_SIGNAL_THRESHOLD;
                if (newIsIndoor != isIndoor) {
                    isIndoor = newIsIndoor;
                    if (indoorStatusChangeListener != null) {
                        indoorStatusChangeListener.onIndoorStatusChanged(isIndoor);
                    }
                }
            }
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {}

        @Override
        public void onProviderEnabled(String provider) {}

        @Override
        public void onProviderDisabled(String provider) {}
    };

    public boolean isIndoor() {
        return isIndoor;
    }
}