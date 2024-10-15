package com.example.navermapapi;

import android.location.Location;
import android.util.Log;

public class BuildingLocationManager {
    private static final String TAG = "BuildingLocationManager";
    private Location lastKnownBuildingLocation;

    public void updateBuildingLocation(Location location) {
        if (location != null) {
            lastKnownBuildingLocation = location;
            Log.d(TAG, "Building location updated: " + location.getLatitude() + ", " + location.getLongitude());
        }
    }

    public Location getBuildingLocation() {
        return lastKnownBuildingLocation;
    }

    public String getBuildingCoordinates() {
        if (lastKnownBuildingLocation != null) {
            return String.format("위도: %.6f, 경도: %.6f",
                    lastKnownBuildingLocation.getLatitude(),
                    lastKnownBuildingLocation.getLongitude());
        }
        return "건물 위치 정보 없음";
    }
}