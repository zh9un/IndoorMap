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
    private static final float INDOOR_GPS_SIGNAL_THRESHOLD = 15.0f; // 실내외를 판단하는 GPS 신호 정확도 임계값
    private boolean isIndoor = false; // 현재 위치가 실내인지 여부를 저장하는 변수
    private OnIndoorStatusChangeListener indoorStatusChangeListener; // 실내 상태 변경 시 호출될 리스너

    // 실내 상태 변경 리스너 인터페이스 정의
    public interface OnIndoorStatusChangeListener {
        void onIndoorStatusChanged(boolean isIndoor);
    }

    // GPSManager 초기화: 위치 서비스 설정
    public GPSManager(Context context) {
        this.context = context;
        locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
    }

    // 실내 상태 변경 리스너 설정 메서드
    public void setOnIndoorStatusChangeListener(OnIndoorStatusChangeListener listener) {
        this.indoorStatusChangeListener = listener;
    }

    // 현재 위치 반환 메서드
    public Location getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.d("GPSManager", "GPS 권한이 부여되지 않았습니다.");
            return null; // 위치 권한이 없을 경우 null 반환
        }

        // GPS와 네트워크 위치를 이용해 마지막으로 알려진 위치 가져오기
        Location gpsLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        Location networkLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);

        if (gpsLocation != null) { // GPS 위치 정보가 있을 경우 사용
            currentLocation = gpsLocation;
            Log.d("GPSManager", "GPS 위치 사용");
        } else if (networkLocation != null) { // 네트워크 위치 정보가 있을 경우 사용
            currentLocation = networkLocation;
            Log.d("GPSManager", "네트워크 위치 사용");
        } else {
            Log.d("GPSManager", "사용 가능한 위치 정보 없음");
        }

        return currentLocation;
    }

    // 위치 업데이트 시작 메서드
    public void startLocationUpdates() {
        Log.d("GPSManager", "startLocationUpdates 시작");
        try {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                Log.e("GPSManager", "위치 권한이 없습니다.");
                return; // 위치 권한이 없을 경우 위치 업데이트 요청을 하지 않음
            }
            // GPS와 네트워크 위치 업데이트 요청 (1초마다, 최소 10m 이동 시)
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 10, locationListener);
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000, 10, locationListener);
        } catch (Exception e) {
            Log.e("GPSManager", "위치 업데이트 시작 중 오류 발생", e);
        }
        Log.d("GPSManager", "startLocationUpdates 종료");
    }

    // 위치 업데이트 중지 메서드
    public void stopLocationUpdates() {
        locationManager.removeUpdates(locationListener); // 위치 업데이트 리스너 해제
    }

    // 위치 변경 리스너 구현
    private LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            currentLocation = location; // 현재 위치 업데이트
            Log.d("GPSManager", "위치 업데이트: " + location.toString());

            if (location.hasAccuracy()) { // 위치 정확도 정보가 있을 경우
                float accuracy = location.getAccuracy();
                boolean newIsIndoor = accuracy > INDOOR_GPS_SIGNAL_THRESHOLD; // 정확도가 임계값보다 큰 경우 실내로 판단
                if (newIsIndoor != isIndoor) { // 실내 상태가 변경되었을 경우
                    isIndoor = newIsIndoor;
                    if (indoorStatusChangeListener != null) {
                        indoorStatusChangeListener.onIndoorStatusChanged(isIndoor); // 실내 상태 변경 리스너 호출
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

    // 현재 위치가 실내인지 여부 반환 메서드
    public boolean isIndoor() {
        return isIndoor;
    }
}