package com.example.navermapapi.path.drawer;

import android.graphics.Color;

import com.example.navermapapi.path.manager.PathDataManager;
import com.naver.maps.geometry.LatLng;
import com.naver.maps.map.NaverMap;
import com.naver.maps.map.overlay.PathOverlay;
import com.naver.maps.map.overlay.PolygonOverlay;

import java.util.Arrays;
import java.util.List;

public class PathDrawer {
    private final NaverMap naverMap;
    private PathOverlay pathOverlay;
    private PolygonOverlay boundaryOverlay;

    public PathDrawer(NaverMap naverMap) {
        this.naverMap = naverMap;
        initializePathOverlay();
        initializeBoundaryOverlay();
    }

    private void initializePathOverlay() {
        pathOverlay = new PathOverlay();
        pathOverlay.setWidth(10);
    }

    private void initializeBoundaryOverlay() {
        boundaryOverlay = new PolygonOverlay();
        List<LatLng> coords = Arrays.asList(
                new LatLng(37.558396, 127.048793), // 시작점
                new LatLng(37.558458, 127.049080), // 엘베앞
                new LatLng(37.558352, 127.049129), // 캡스톤 강의실 반대편
                new LatLng(37.558338, 127.049084), // 캡스톤 강의실
                new LatLng(37.558435, 127.049041), //엘베앞보다 안쪽
                new LatLng(37.558369, 127.048801) //시작점의 반대편
        );
        boundaryOverlay.setCoords(coords);
        boundaryOverlay.setColor(Color.argb(50, 0, 255, 0));  // 반투명 빨간색
        boundaryOverlay.setOutlineWidth(5);
        boundaryOverlay.setOutlineColor(Color.GREEN);
    }

    // 경로 영역 표시/숨기기
    public void showBoundary(boolean show) {
        boundaryOverlay.setMap(show ? naverMap : null);
    }

    public void drawPath(LatLng start, LatLng end) {
        if (start != null && end != null) {
            List<LatLng> pathPoints = PathDataManager.calculatePathBetweenPoints(start, end);
            pathOverlay.setCoords(pathPoints);
            pathOverlay.setColor(Color.RED); // 경로 색상 설정
            pathOverlay.setMap(naverMap);
        }
    }

    public void clearPath() {
        if (pathOverlay != null) {
            pathOverlay.setMap(null);
        }
    }

    public void setPathColor(int color) {
        if (pathOverlay != null) {
            pathOverlay.setColor(color);
        }
    }

    public void setPathWidth(int width) {
        if (pathOverlay != null) {
            pathOverlay.setWidth(width);
        }
    }

    // 리소스 정리를 위한 cleanup 메서드
    public void cleanup() {
        if (pathOverlay != null) {
            pathOverlay.setMap(null);
        }
        if (boundaryOverlay != null) {
            boundaryOverlay.setMap(null);
        }
    }
}