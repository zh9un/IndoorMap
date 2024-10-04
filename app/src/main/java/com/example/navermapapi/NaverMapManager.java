package com.example.navermapapi;

import android.content.Context;
import android.location.Location;
import android.util.Log;

import com.naver.maps.map.MapFragment;
import com.naver.maps.map.NaverMap;
import com.naver.maps.map.OnMapReadyCallback;
import com.naver.maps.map.CameraUpdate;
import com.naver.maps.map.overlay.Marker;
import com.naver.maps.geometry.LatLng;
import com.naver.maps.map.overlay.PolylineOverlay;

import java.util.ArrayList;
import java.util.List;

public class NaverMapManager implements OnMapReadyCallback {

    private Context context;
    private NaverMap naverMap;
    private GPSManager gpsManager;
    private Marker currentMarker;
    private PolylineOverlay polyline = new PolylineOverlay();
    private List<LatLng> pathPoints = new ArrayList<>();

    public NaverMapManager(Context context, GPSManager gpsManager) {
        this.context = context;
        this.gpsManager = gpsManager;

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
            Log.e("NaverMapManager", "네이버 지도 초기화 중 오류 발생", e);
        }
    }

    @Override
    public void onMapReady(NaverMap naverMap) {
        try {
            this.naverMap = naverMap;
            Log.d("NaverMap", "onMapReady 호출됨");
            updateMapWithGPSLocation();
        } catch (Exception e) {
            Log.e("NaverMap", "지도 초기화 중 오류 발생", e);
        }
    }

    public void updateMapWithGPSLocation() {
        Log.d("NaverMapManager", "updateMapWithGPSLocation 시작");
        try {
            Location location = gpsManager.getCurrentLocation();
            if (location != null && naverMap != null) {
                LatLng currentPosition = new LatLng(location.getLatitude(), location.getLongitude());
                updateMarkerPosition(currentPosition);
                moveCamera(currentPosition);
                addPathPoint(currentPosition);
            }
        } catch (Exception e) {
            Log.e("NaverMapManager", "위치 업데이트 중 오류 발생", e);
        }
        Log.d("NaverMapManager", "updateMapWithGPSLocation 종료");
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
        }
    }
}