package com.example.navermapapi;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

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

    public LocationDataManager(Context context) {
        sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

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

    public boolean shouldSaveData() {
        return System.currentTimeMillis() - lastSaveTime > SAVE_INTERVAL;
    }

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