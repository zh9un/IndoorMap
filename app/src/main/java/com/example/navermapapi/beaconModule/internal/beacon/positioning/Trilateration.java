package com.example.navermapapi.beaconModule.internal.beacon.positioning;

import java.util.List;
import static com.example.navermapapi.beaconModule.internal.beacon.positioning.PositionCalculator.Position;
import static com.example.navermapapi.beaconModule.internal.beacon.positioning.PositionCalculator.BeaconDistance;

public class Trilateration {
    private static final double WEIGHT_POWER = 2.0; // 가중치 계수

    public Position calculatePosition(List<BeaconDistance> beacons) {
        if (beacons.size() < 3) {
            throw new IllegalArgumentException("삼변측량에는 최소 3개의 비콘이 필요합니다.");
        }

        // 가중 최소제곱법을 사용한 위치 계산
        double sumX = 0;
        double sumY = 0;
        double sumWeight = 0;

        for (int i = 0; i < beacons.size() - 1; i++) {
            for (int j = i + 1; j < beacons.size(); j++) {
                BeaconDistance b1 = beacons.get(i);
                BeaconDistance b2 = beacons.get(j);

                // 두 원의 교점 계산
                double[] intersection = calculateIntersection(b1, b2);
                if (intersection != null) {
                    // 교점의 가중치 계산
                    double weight = calculateWeight(intersection[0], intersection[1], beacons);
                    sumX += intersection[0] * weight;
                    sumY += intersection[1] * weight;
                    sumWeight += weight;
                }
            }
        }

        if (sumWeight == 0) {
            // 교점이 없는 경우 가장 가까운 비콘의 위치 반환
            BeaconDistance closest = findClosestBeacon(beacons);
            return new Position(closest.x, closest.y, closest.distance);
        }

        double finalX = sumX / sumWeight;
        double finalY = sumY / sumWeight;
        double accuracy = calculateAccuracy(finalX, finalY, beacons);

        return new Position(finalX, finalY, accuracy);
    }

    private double[] calculateIntersection(BeaconDistance b1, BeaconDistance b2) {
        double d = Math.sqrt(Math.pow(b2.x - b1.x, 2) + Math.pow(b2.y - b1.y, 2));

        // 두 원이 만나지 않는 경우
        if (d > b1.distance + b2.distance) return null;
        if (d < Math.abs(b1.distance - b2.distance)) return null;

        double a = (Math.pow(b1.distance, 2) - Math.pow(b2.distance, 2) + Math.pow(d, 2)) / (2 * d);
        double h = Math.sqrt(Math.pow(b1.distance, 2) - Math.pow(a, 2));

        double x2 = b1.x + (a * (b2.x - b1.x)) / d;
        double y2 = b1.y + (a * (b2.y - b1.y)) / d;

        double x = x2 + (h * (b2.y - b1.y)) / d;
        double y = y2 - (h * (b2.x - b1.x)) / d;

        return new double[]{x, y};
    }

    private double calculateWeight(double x, double y, List<BeaconDistance> beacons) {
        double totalError = 0;
        for (BeaconDistance beacon : beacons) {
            double calculatedDistance = Math.sqrt(
                    Math.pow(x - beacon.x, 2) + Math.pow(y - beacon.y, 2)
            );
            totalError += Math.abs(calculatedDistance - beacon.distance);
        }
        return Math.pow(1.0 / (totalError + 1), WEIGHT_POWER);
    }

    private BeaconDistance findClosestBeacon(List<BeaconDistance> beacons) {
        return beacons.stream()
                .min((b1, b2) -> Double.compare(b1.distance, b2.distance))
                .orElse(beacons.get(0));
    }

    private double calculateAccuracy(double x, double y, List<BeaconDistance> beacons) {
        double maxError = 0;
        for (BeaconDistance beacon : beacons) {
            double calculatedDistance = Math.sqrt(
                    Math.pow(x - beacon.x, 2) + Math.pow(y - beacon.y, 2)
            );
            maxError = Math.max(maxError, Math.abs(calculatedDistance - beacon.distance));
        }
        return maxError;
    }
}