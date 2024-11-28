package com.example.navermapapi.path.drawer;

import android.graphics.Color;

import com.example.navermapapi.path.manager.PathDataManager;
import com.naver.maps.geometry.LatLng;
import com.naver.maps.map.NaverMap;
import com.naver.maps.map.overlay.PathOverlay;
import java.util.List;

public class PathDrawer {
    private final NaverMap naverMap;
    private PathOverlay pathOverlay;

    public PathDrawer(NaverMap naverMap) {
        this.naverMap = naverMap;
        initializePathOverlay();
    }

    private void initializePathOverlay() {
        pathOverlay = new PathOverlay();
        pathOverlay.setWidth(10);
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
}
