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

    // 네이버 지도 매니저 초기화 - GPSManager와 네이버 지도 연동하여 현재 위치를 지도에 표시하는 역할을 합니다.
    public NaverMapManager(Context context, GPSManager gpsManager) {
        this.context = context;
        this.gpsManager = gpsManager;

        try {
            // 지도 프래그먼트를 초기화하고, 지도를 비동기로 준비합니다.
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
            // 네이버 지도가 준비되었을 때 호출됩니다.
            this.naverMap = naverMap;
            Log.d("NaverMap", "onMapReady 호출됨");
            updateMapWithGPSLocation(); // GPS 위치를 기반으로 지도를 업데이트합니다.
        } catch (Exception e) {
            Log.e("NaverMap", "지도 초기화 중 오류 발생", e);
        }
    }

    // GPS 위치를 사용하여 지도에 현재 위치를 업데이트하는 메서드입니다.
    public void updateMapWithGPSLocation() {
        Log.d("NaverMapManager", "updateMapWithGPSLocation 시작");
        try {
            Location location = gpsManager.getCurrentLocation();
            if (location != null && naverMap != null) {
                LatLng currentPosition = new LatLng(location.getLatitude(), location.getLongitude());
                updateMarkerPosition(currentPosition); // 현재 위치에 마커를 업데이트합니다.
                moveCamera(currentPosition); // 카메라를 현재 위치로 이동합니다.
                addPathPoint(currentPosition); // 이동 경로를 그리기 위해 현재 위치를 경로 목록에 추가합니다.
            }
        } catch (Exception e) {
            Log.e("NaverMapManager", "위치 업데이트 중 오류 발생", e);
        }
        Log.d("NaverMapManager", "updateMapWithGPSLocation 종료");
    }

    // 현재 위치에 마커를 업데이트하는 메서드입니다.
    private void updateMarkerPosition(LatLng position) {
        if (currentMarker == null) {
            // 마커가 아직 생성되지 않았다면 새로 생성하고 현재 위치에 설정합니다.
            currentMarker = new Marker();
            currentMarker.setPosition(position);
            currentMarker.setMap(naverMap);
        } else {
            // 이미 마커가 존재한다면 위치만 업데이트합니다.
            currentMarker.setPosition(position);
        }
    }

    // 지도의 카메라를 주어진 위치로 이동시키는 메서드입니다.
    private void moveCamera(LatLng position) {
        CameraUpdate cameraUpdate = CameraUpdate.scrollTo(position);
        naverMap.moveCamera(cameraUpdate);
    }

    // 경로 목록에 새로운 위치를 추가하고, 경로를 그리기 위해 폴리라인을 업데이트합니다.
    private void addPathPoint(LatLng point) {
        pathPoints.add(point);
        polyline.setCoords(pathPoints);
        polyline.setMap(naverMap);
    }

    // 실내 위치 데이터를 기반으로 지도를 업데이트하는 메서드입니다.
    // 실내에서 위치 변화를 반영할 수 있도록 xOffset과 yOffset을 적용하여 위치를 계산합니다.
    public void updateIndoorPositionOnMap(float xOffset, float yOffset) {
        Location location = gpsManager.getCurrentLocation();
        if (location != null && naverMap != null) {
            LatLng currentPosition = new LatLng(
                    location.getLatitude() + xOffset,
                    location.getLongitude() + yOffset
            );
            updateMarkerPosition(currentPosition); // 위치에 맞게 마커 업데이트
            moveCamera(currentPosition); // 카메라 이동
            addPathPoint(currentPosition); // 경로 업데이트
        }
    }
}
