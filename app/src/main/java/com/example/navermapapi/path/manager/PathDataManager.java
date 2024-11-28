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

        // 노드 추가 (예시 좌표 사용)
        NODES.add(new LatLng(37.558347, 127.048963)); // 노드 0
        NODES.add(new LatLng(37.558347, 127.049163)); // 노드 1
        NODES.add(new LatLng(37.558547, 127.049163)); // 노드 2
        NODES.add(new LatLng(37.558547, 127.048963)); // 노드 3

        // 간선 추가 (양방향)
        addEdge(0, 1);
        addEdge(1, 2);
        addEdge(2, 3);
        addEdge(3, 0);
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
