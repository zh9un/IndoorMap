package com.example.navermapapi.path.drawer;

import android.graphics.Color;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.navermapapi.coreModule.api.environment.model.EnvironmentType;
import com.example.navermapapi.path.manager.PathDataManager;
import com.naver.maps.geometry.LatLng;
import com.naver.maps.map.NaverMap;
import com.naver.maps.map.overlay.PathOverlay;
import com.naver.maps.map.overlay.PolygonOverlay;

import java.util.Arrays;
import java.util.List;

/**
 * 내비게이션 경로를 지도에 표시하는 클래스
 */
public class PathDrawer {
    private static final int PATH_WIDTH = 15;  // 경로선 두께
    private static final int PATH_COLOR_INDOOR = Color.GREEN;  // 실내 경로 색상
    private static final int PATH_COLOR_OUTDOOR = Color.BLUE;  // 실외 경로 색상
    private static final int BOUNDARY_COLOR = Color.argb(50, 0, 255, 0);  // 경계선 색상

    private final NaverMap naverMap;
    private final PathOverlay pathOverlay;
    private final PolygonOverlay boundaryOverlay;
    private EnvironmentType currentEnvironment = EnvironmentType.OUTDOOR;

    public PathDrawer(@NonNull NaverMap naverMap) {
        this.naverMap = naverMap;

        // 경로 오버레이 초기화
        this.pathOverlay = new PathOverlay();
        pathOverlay.setWidth(PATH_WIDTH);
        pathOverlay.setColor(PATH_COLOR_OUTDOOR);
        pathOverlay.setMap(null);  // 초기에는 보이지 않음

        // 경계 오버레이 초기화
        this.boundaryOverlay = new PolygonOverlay();
        initializeBoundaryOverlay();
    }

    /**
     * 이동 가능 영역의 경계를 초기화
     */
    private void initializeBoundaryOverlay() {
        List<LatLng> coords = Arrays.asList(
                new LatLng(37.558396, 127.048793), // 시작점
                new LatLng(37.558458, 127.049080), // 엘베앞
                new LatLng(37.558352, 127.049129), // 캡스톤 강의실 반대편
                new LatLng(37.558338, 127.049084), // 캡스톤 강의실
                new LatLng(37.558435, 127.049041), // 엘베앞보다 안쪽
                new LatLng(37.558369, 127.048801)  // 시작점의 반대편
        );

        boundaryOverlay.setCoords(coords);
        boundaryOverlay.setColor(BOUNDARY_COLOR);
        boundaryOverlay.setOutlineWidth(5);
        boundaryOverlay.setOutlineColor(Color.GREEN);
    }

    /**
     * 경로를 그림
     * @param start 시작점
     * @param destination 목적지
     */
    public void drawPath(@NonNull LatLng start, @NonNull LatLng destination) {
        try {
            // PathDataManager를 통해 경로 계산
            List<LatLng> pathPoints = PathDataManager.calculatePathBetweenPoints(start, destination);

            if (pathPoints == null || pathPoints.isEmpty()) {
                pathOverlay.setMap(null);
                return;
            }

            // 경로 표시
            pathOverlay.setCoords(pathPoints);
            pathOverlay.setMap(naverMap);

        } catch (Exception e) {
            pathOverlay.setMap(null);
        }
    }

    /**
     * 현재 환경에 따라 경로 스타일 변경
     * @param environment 현재 환경(실내/실외)
     */
    public void setEnvironment(@NonNull EnvironmentType environment) {
        this.currentEnvironment = environment;
        pathOverlay.setColor(environment == EnvironmentType.INDOOR ?
                PATH_COLOR_INDOOR : PATH_COLOR_OUTDOOR);
    }

    /**
     * 이동 가능 영역 표시/숨김
     * @param show true면 표시, false면 숨김
     */
    public void showBoundary(boolean show) {
        boundaryOverlay.setMap(show ? naverMap : null);
    }

    /**
     * 경로 표시 제거
     */
    public void clearPath() {
        pathOverlay.setMap(null);
    }

    /**
     * 자원 정리
     */
    public void cleanup() {
        clearPath();
        boundaryOverlay.setMap(null);
    }
}