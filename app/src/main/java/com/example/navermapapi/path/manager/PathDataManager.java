package com.example.navermapapi.path.manager;

import com.naver.maps.geometry.LatLng;
import java.util.*;

public class PathDataManager {
    // 노드 리스트
    private static final List<LatLng> NODES;
    // 간선 리스트 (인접 리스트)
    private static final Map<Integer, List<Edge>> EDGES;

    static {
        NODES = new ArrayList<>();
        EDGES = new HashMap<>();

        // 실제 정보문학관 경로의 주요 노드들 추가
        NODES.add(new LatLng(37.558396, 127.048793)); // 노드 0: 시작점
        NODES.add(new LatLng(37.558458, 127.049080)); // 노드 1: 엘베앞
        NODES.add(new LatLng(37.558352, 127.049129)); // 노드 2: 캡스톤 강의실 반대편
        NODES.add(new LatLng(37.558338, 127.049084)); // 노드 3: 캡스톤 강의실
        NODES.add(new LatLng(37.558435, 127.049041)); // 노드 4: 엘베앞보다 안쪽
        NODES.add(new LatLng(37.558369, 127.048801)); // 노드 5: 시작점의 반대편

        // 실제 경로에 맞게 간선 추가 (양방향)
        addEdge(0, 1); // 시작점 - 엘베앞
        addEdge(1, 2); // 엘베앞 - 캡스톤 강의실 반대편
        addEdge(2, 3); // 캡스톤 강의실 반대편 - 캡스톤 강의실
        addEdge(3, 4); // 캡스톤 강의실 - 엘베앞보다 안쪽
        addEdge(4, 5); // 엘베앞보다 안쪽 - 시작점의 반대편
        addEdge(5, 0); // 시작점의 반대편 - 시작점
    }


    // 범위 내에 있는지 확인하는 메서드 추가
    public static boolean isPointInBoundary(LatLng point) {
        LatLng[] boundary = {
                new LatLng(37.558396, 127.048793), // 시작점
                new LatLng(37.558458, 127.049080), // 엘베앞
                new LatLng(37.558352, 127.049129), // 캡스톤 강의실 반대편
                new LatLng(37.558338, 127.049084), // 캡스톤 강의실
                new LatLng(37.558435, 127.049041), // 엘베앞보다 안쪽
                new LatLng(37.558369, 127.048801)  // 시작점의 반대편
        };

        return isPointInPolygon(point, boundary);
    }

    // 점이 다각형 안에 있는지 확인하는 메서드
    private static boolean isPointInPolygon(LatLng point, LatLng[] polygon) {
        int i, j;
        boolean result = false;
        for (i = 0, j = polygon.length - 1; i < polygon.length; j = i++) {
            if ((polygon[i].latitude > point.latitude) != (polygon[j].latitude > point.latitude) &&
                    (point.longitude < (polygon[j].longitude - polygon[i].longitude) *
                            (point.latitude - polygon[i].latitude) / (polygon[j].latitude - polygon[i].latitude) +
                            polygon[i].longitude)) {
                result = !result;
            }
        }
        return result;
    }

    private PathDataManager() {
        // Utility class
    }

    private static void addEdge(int from, int to) {
        double distance = calculateDistance(NODES.get(from), NODES.get(to));
        EDGES.computeIfAbsent(from, k -> new ArrayList<>()).add(new Edge(to, distance));
        EDGES.computeIfAbsent(to, k -> new ArrayList<>()).add(new Edge(from, distance));
    }

    private static double calculateDistance(LatLng pointA, LatLng pointB) {
        float[] results = new float[1];
        android.location.Location.distanceBetween(
                pointA.latitude, pointA.longitude,
                pointB.latitude, pointB.longitude,
                results
        );
        return results[0];
    }

    public static List<LatLng> getNodes() {
        return new ArrayList<>(NODES);
    }

    public static Map<Integer, List<Edge>> getEdges() {
        return new HashMap<>(EDGES);
    }

    public static boolean isPointOnPath(LatLng point, double toleranceMeters) {
        for (int i = 0; i < NODES.size(); i++) {
            LatLng node = NODES.get(i);
            double distance = calculateDistance(point, node);
            if (distance <= toleranceMeters) {
                return true;
            }
        }
        return false;
    }

    public static int findNearestNodeIndex(LatLng point) {
        double minDistance = Double.MAX_VALUE;
        int nearestNodeIndex = -1;

        for (int i = 0; i < NODES.size(); i++) {
            LatLng node = NODES.get(i);
            double distance = calculateDistance(point, node);
            if (distance < minDistance) {
                minDistance = distance;
                nearestNodeIndex = i;
            }
        }
        return nearestNodeIndex;
    }

    public static List<LatLng> calculatePathBetweenPoints(LatLng start, LatLng end) {
        int startNodeIndex = findNearestNodeIndex(start);
        int endNodeIndex = findNearestNodeIndex(end);

        List<Integer> pathNodeIndices = DijkstraAlgorithm.findShortestPath(
                EDGES, startNodeIndex, endNodeIndex);

        List<LatLng> pathPoints = new ArrayList<>();
        pathPoints.add(start); // 시작점 추가

        for (int nodeIndex : pathNodeIndices) {
            pathPoints.add(NODES.get(nodeIndex));
        }

        pathPoints.add(end); // 끝점 추가
        return pathPoints;
    }

    // 내부 클래스 정의
    public static class Edge {
        public final int to;
        public final double weight;

        public Edge(int to, double weight) {
            this.to = to;
            this.weight = weight;
        }
    }
}
