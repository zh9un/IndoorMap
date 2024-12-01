package com.example.navermapapi.path.manager;

import java.util.*;

public class DijkstraAlgorithm {
    public static List<Integer> findShortestPath(
            Map<Integer, List<PathDataManager.Edge>> graph,
            int startNode, int endNode) {

        int n = graph.size();
        double[] distances = new double[n];
        int[] previous = new int[n];
        Arrays.fill(distances, Double.MAX_VALUE);
        Arrays.fill(previous, -1);

        distances[startNode] = 0;

        PriorityQueue<Node> queue = new PriorityQueue<>(Comparator.comparingDouble(n1 -> n1.distance));
        queue.add(new Node(startNode, 0));

        while (!queue.isEmpty()) {
            Node current = queue.poll();
            int currentNode = current.index;

            if (currentNode == endNode) {
                break;
            }

            for (PathDataManager.Edge edge : graph.getOrDefault(currentNode, Collections.emptyList())) {
                double newDist = distances[currentNode] + edge.weight;
                if (newDist < distances[edge.to]) {
                    distances[edge.to] = newDist;
                    previous[edge.to] = currentNode;
                    queue.add(new Node(edge.to, newDist));
                }
            }
        }

        // 경로 복원
        List<Integer> path = new ArrayList<>();
        for (int at = endNode; at != -1; at = previous[at]) {
            path.add(at);
        }
        Collections.reverse(path);

        // 시작 노드에서 도달할 수 없는 경우 빈 리스트 반환
        if (path.get(0) != startNode) {
            return Collections.emptyList();
        }

        return path;
    }

    // 내부 클래스 정의
    private static class Node {
        public final int index;
        public final double distance;

        public Node(int index, double distance) {
            this.index = index;
            this.distance = distance;
        }
    }
}
