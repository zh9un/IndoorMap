package com.example.navermapapi;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

/**
 * LocationDataManager 클래스
 * GPS 위경도 데이터의 저장, 로드, 관리를 담당합니다.
 * SharedPreferences를 사용하여 데이터를 로컬에 저장합니다.
 */
public class LocationDataManager {
    private static final String TAG = "LocationDataManager";
    private static final String PREF_NAME = "LocationData";
    private static final String KEY_LAST_LATITUDE = "last_latitude";
    private static final String KEY_LAST_LONGITUDE = "last_longitude";
    private static final String KEY_LAST_TIME = "last_time";
    private static final String KEY_TOTAL_DISTANCE = "total_distance";

    private SharedPreferences sharedPreferences;
    private long lastSaveTime = 0;
    private static final long SAVE_INTERVAL = 5000; // 5초마다 저장

    /**
     * 생성자
     * @param context 애플리케이션 컨텍스트
     */
    public LocationDataManager(Context context) {
        sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    /**
     * 위치 데이터를 저장합니다.
     * @param latitude 위도
     * @param longitude 경도
     * @param totalDistance 총 이동 거리
     */
    public void saveLocationData(double latitude, double longitude, double totalDistance) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(KEY_LAST_LATITUDE, Double.toString(latitude));
        editor.putString(KEY_LAST_LONGITUDE, Double.toString(longitude));
        editor.putLong(KEY_LAST_TIME, System.currentTimeMillis());
        editor.putString(KEY_TOTAL_DISTANCE, Double.toString(totalDistance));
        editor.apply();

        lastSaveTime = System.currentTimeMillis();
        Log.d(TAG, "Location data saved: Lat=" + latitude + ", Lon=" + longitude + ", Total Distance=" + totalDistance);
    }

    /**
     * 저장된 위치 데이터를 불러옵니다.
     * @return LocationData 객체, 저장된 데이터가 없으면 null
     */
    public LocationData loadLocationData() {
        String latitudeStr = sharedPreferences.getString(KEY_LAST_LATITUDE, null);
        String longitudeStr = sharedPreferences.getString(KEY_LAST_LONGITUDE, null);
        long time = sharedPreferences.getLong(KEY_LAST_TIME, 0);
        String totalDistanceStr = sharedPreferences.getString(KEY_TOTAL_DISTANCE, "0");

        if (latitudeStr == null || longitudeStr == null) {
            Log.d(TAG, "No saved location data found");
            return null;
        }

        double latitude = Double.parseDouble(latitudeStr);
        double longitude = Double.parseDouble(longitudeStr);
        double totalDistance = Double.parseDouble(totalDistanceStr);

        Log.d(TAG, "Location data loaded: Lat=" + latitude + ", Lon=" + longitude + ", Time=" + time + ", Total Distance=" + totalDistance);
        return new LocationData(latitude, longitude, time, totalDistance);
    }

    /**
     * 데이터를 저장해야 하는지 여부를 반환합니다.
     * @return 마지막 저장 시간으로부터 SAVE_INTERVAL이 지났으면 true, 아니면 false
     */
    public boolean shouldSaveData() {
        return System.currentTimeMillis() - lastSaveTime > SAVE_INTERVAL;
    }

    /**
     * 위치 데이터를 담는 내부 클래스
     */
    public static class LocationData {
        public final double latitude;
        public final double longitude;
        public final long time;
        public final double totalDistance;

        public LocationData(double latitude, double longitude, long time, double totalDistance) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.time = time;
            this.totalDistance = totalDistance;
        }
    }
}