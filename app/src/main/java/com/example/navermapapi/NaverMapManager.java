package com.example.navermapapi;

import android.content.Context;
import android.location.Location;
import android.util.Log;

import androidx.annotation.NonNull;

import com.naver.maps.map.LocationTrackingMode;
import com.naver.maps.map.MapFragment;
import com.naver.maps.map.NaverMap;
import com.naver.maps.map.OnMapReadyCallback;
import com.naver.maps.map.CameraUpdate;
import com.naver.maps.map.overlay.Marker;
import com.naver.maps.geometry.LatLng;
import com.naver.maps.map.overlay.PolylineOverlay;
import com.naver.maps.map.overlay.InfoWindow;
import com.naver.maps.map.util.FusedLocationSource;

import java.util.ArrayList;
import java.util.List;

public class NaverMapManager implements OnMapReadyCallback, GPSManager.OnLocationUpdateListener {

    private static final String TAG = "NaverMapManager";
    private Context context;
    private NaverMap naverMap;
    private GPSManager gpsManager;
    private Marker currentMarker;
    private PolylineOverlay polyline = new PolylineOverlay();
    private List<LatLng> pathPoints = new ArrayList<>();
    private InfoWindow infoWindow;

    public NaverMapManager(Context context, GPSManager gpsManager) {
        this.context = context;
        this.gpsManager = gpsManager;
        gpsManager.setOnLocationUpdateListener(this);

        try {
            MapFragment mapFragment = (MapFragment) ((MainActivity) context).getSupportFragmentManager()
                    .findFragmentById(R.id.map);
            if (mapFragment == null) {
                mapFragment = MapFragment.newInstance();
                ((MainActivity) context).getSupportFragmentManager().beginTransaction()
                        .add(R.id.map, mapFragment)
                        .commit();
            }
            mapFragment.getMapAsync(this);
        } catch (Exception e) {
            Log.e(TAG, "네이버 지도 초기화 중 오류 발생", e);
        }
    }

    @Override
    public void onMapReady(NaverMap naverMap) {
        try {
            this.naverMap = naverMap;
            Log.d(TAG, "onMapReady 호출됨");

            // 정보 창 초기화
            infoWindow = new InfoWindow();
            infoWindow.setAdapter(new InfoWindow.DefaultTextAdapter(context) {
                @NonNull
                @Override
                public CharSequence getText(@NonNull InfoWindow infoWindow) {
                    LatLng position = infoWindow.getPosition();
                    return String.format("위도: %.6f\n경도: %.6f", position.latitude, position.longitude);
                }
            });

            updateMapWithGPSLocation();
            naverMap.setLocationTrackingMode(LocationTrackingMode.Follow);
        } catch (Exception e) {
            Log.e(TAG, "지도 초기화 중 오류 발생", e);
        }
    }

    @Override
    public void onLocationUpdated(Location location) {
        updateMapWithLocation(location);
    }

    public void updateMapWithGPSLocation() {
        Log.d(TAG, "updateMapWithGPSLocation 시작");
        try {
            Location location = gpsManager.getCurrentLocation();
            if (location != null) {
                updateMapWithLocation(location);
            }
        } catch (Exception e) {
            Log.e(TAG, "위치 업데이트 중 오류 발생", e);
        }
        Log.d(TAG, "updateMapWithGPSLocation 종료");
    }

    private void updateMapWithLocation(Location location) {
        if (naverMap != null) {
            LatLng currentPosition = new LatLng(location.getLatitude(), location.getLongitude());
            updateMarkerPosition(currentPosition);
            moveCamera(currentPosition);
            addPathPoint(currentPosition);
            showCoordinates(currentPosition);
        }
    }

    private void updateMarkerPosition(LatLng position) {
        if (currentMarker == null) {
            currentMarker = new Marker();
            currentMarker.setPosition(position);
            currentMarker.setMap(naverMap);
        } else {
            currentMarker.setPosition(position);
        }
    }

    private void moveCamera(LatLng position) {
        CameraUpdate cameraUpdate = CameraUpdate.scrollTo(position);
        naverMap.moveCamera(cameraUpdate);
    }

    private void addPathPoint(LatLng point) {
        pathPoints.add(point);
        polyline.setCoords(pathPoints);
        polyline.setMap(naverMap);
    }

    private void showCoordinates(LatLng position) {
        infoWindow.setPosition(position);
        infoWindow.open(naverMap);
    }

    public void updateIndoorPositionOnMap(float xOffset, float yOffset) {
        Location location = gpsManager.getCurrentLocation();
        if (location != null && naverMap != null) {
            LatLng currentPosition = new LatLng(
                    location.getLatitude() + xOffset,
                    location.getLongitude() + yOffset
            );
            updateMarkerPosition(currentPosition);
            moveCamera(currentPosition);
            addPathPoint(currentPosition);
            showCoordinates(currentPosition);
        }
    }
}