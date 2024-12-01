package com.example.navermapapi.path.calculator;

import com.naver.maps.geometry.LatLng;
import com.example.navermapapi.path.manager.PathDataManager;
import java.util.List;

public class PathCalculator {
    private static final double TOLERANCE_METERS = 5.0; // 5미터 허용 오차

    private PathCalculator() {
        // Utility class
    }

    public static LatLng findNearestPointOnPath(LatLng point) {
        int nearestNodeIndex = PathDataManager.findNearestNodeIndex(point);
        return PathDataManager.getNodes().get(nearestNodeIndex);
    }

    public static double calculatePathDistance(LatLng start, LatLng end) {
        // 시작점과 끝점이 경로 상에 없다면 가장 가까운 노드로 매핑
        LatLng startOnPath = PathDataManager.isPointOnPath(start, TOLERANCE_METERS) ?
                start : findNearestPointOnPath(start);
        LatLng endOnPath = PathDataManager.isPointOnPath(end, TOLERANCE_METERS) ?
                end : findNearestPointOnPath(end);

        // 경로 상의 시작점부터 끝점까지의 실제 경로 거리 계산
        List<LatLng> pathPoints = PathDataManager.calculatePathBetweenPoints(startOnPath, endOnPath);
        double totalDistance = 0.0;

        for (int i = 0; i < pathPoints.size() - 1; i++) {
            LatLng pointA = pathPoints.get(i);
            LatLng pointB = pathPoints.get(i + 1);

            float[] results = new float[1];
            android.location.Location.distanceBetween(
                    pointA.latitude, pointA.longitude,
                    pointB.latitude, pointB.longitude,
                    results
            );
            totalDistance += results[0];
        }

        return totalDistance;
    }
}
