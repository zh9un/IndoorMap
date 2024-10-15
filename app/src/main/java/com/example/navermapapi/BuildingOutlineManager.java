package com.example.navermapapi;

import android.util.Log;

import com.naver.maps.geometry.LatLng;
import com.naver.maps.map.NaverMap;
import com.naver.maps.map.overlay.PolygonOverlay;

import java.util.ArrayList;
import java.util.List;

public class BuildingOutlineManager {
    private static final String TAG = "BuildingOutlineManager";

    private NaverMap naverMap;
    private PolygonOverlay buildingOutline;
    private List<LatLng> buildingCorners;

    // 건물 모서리 좌표 정보를 전달하기 위한 인터페이스
    public interface OnBuildingCornerCoordinatesListener {
        void onBuildingCornerCoordinatesFetched(String coordinatesText);
    }

    private OnBuildingCornerCoordinatesListener coordinatesListener;

    public void setOnBuildingCornerCoordinatesListener(OnBuildingCornerCoordinatesListener listener) {
        this.coordinatesListener = listener;
    }

    public BuildingOutlineManager(NaverMap naverMap) {
        this.naverMap = naverMap;
        buildingOutline = new PolygonOverlay();

        // 제공된 좌표로 buildingCorners 초기화
        initializeBuildingCorners();
    }

    private void initializeBuildingCorners() {
        buildingCorners = new ArrayList<>();

        // 제공된 좌표를 buildingCorners 리스트에 추가
        buildingCorners.add(new LatLng(37.558425, 127.048625));
        buildingCorners.add(new LatLng(37.558272, 127.048689));
        buildingCorners.add(new LatLng(37.558351, 127.048997));
        buildingCorners.add(new LatLng(37.558168, 127.049076));
        buildingCorners.add(new LatLng(37.558212, 127.049279));
        buildingCorners.add(new LatLng(37.558546, 127.049131));
    }

    public void showBuildingOutline(boolean isIndoor) {
        if (isIndoor) {
            buildingOutline.setCoords(buildingCorners);
            buildingOutline.setColor(0x50FF0000); // 반투명 빨간색
            buildingOutline.setOutlineWidth(5);
            buildingOutline.setOutlineColor(0xFFFF0000); // 불투명 빨간색
            buildingOutline.setMap(naverMap);
            showBuildingCornerCoordinates();
        } else {
            buildingOutline.setMap(null);
        }
    }

    private void showBuildingCornerCoordinates() {
        StringBuilder cornerCoordinates = new StringBuilder("건물 모서리 좌표:\n");
        for (int i = 0; i < buildingCorners.size(); i++) {
            LatLng corner = buildingCorners.get(i);
            cornerCoordinates.append(String.format("모서리 %d: 위도 %.6f, 경도 %.6f\n", i + 1, corner.latitude, corner.longitude));
        }
        Log.d(TAG, cornerCoordinates.toString());

        // UI에 정보를 표시하기 위해 리스너를 통해 MainActivity에 전달
        if (coordinatesListener != null) {
            coordinatesListener.onBuildingCornerCoordinatesFetched(cornerCoordinates.toString());
        }
    }

    public List<LatLng> getBuildingCorners() {
        return buildingCorners;
    }
}
