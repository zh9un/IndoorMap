package com.example.navermapapi.beaconModule.internal.positioning;

import com.example.navermapapi.beaconModule.model.BeaconData;
import java.util.List;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class PositionCalculator {
    private static final double PATH_LOSS_EXPONENT = 2.0;
    private static final int RSSI_AT_1M = -69;
    private static final int MIN_BEACONS_FOR_TRILATERATION = 3;

    private final TrilaterationCalculator trilaterationCalculator;

    public PositionCalculator() {
        this.trilaterationCalculator = new TrilaterationCalculator();
    }

    @Nullable
    public double[] calculatePosition(@NonNull List<BeaconData> beacons) {
        if (beacons.isEmpty()) {
            return null;
        }

        // 3개 이상의 비콘이 있으면 삼변측량 사용
        if (beacons.size() >= MIN_BEACONS_FOR_TRILATERATION) {
            double[][] beaconPositions = new double[beacons.size()][2];
            double[] distances = new double[beacons.size()];

            for (int i = 0; i < beacons.size(); i++) {
                BeaconData beacon = beacons.get(i);
                beaconPositions[i] = new double[]{beacon.getX(), beacon.getY()};
                distances[i] = calculateDistance(beacon.getRssi());
            }

            try {
                return trilaterationCalculator.calculatePosition(beaconPositions, distances);
            } catch (Exception e) {
                // 삼변측량 실패 시 가중 평균 방식으로 폴백
                return calculateWeightedAveragePosition(beacons);
            }
        }

        // 비콘이 3개 미만이면 가중 평균 방식 사용
        return calculateWeightedAveragePosition(beacons);
    }

    private double[] calculateWeightedAveragePosition(List<BeaconData> beacons) {
        double[] position = new double[2];
        double sumX = 0;
        double sumY = 0;
        double sumWeights = 0;

        for (BeaconData beacon : beacons) {
            double distance = calculateDistance(beacon.getRssi());
            double weight = 1.0 / (distance * distance);

            sumX += beacon.getX() * weight;
            sumY += beacon.getY() * weight;
            sumWeights += weight;
        }

        if (sumWeights > 0) {
            position[0] = sumX / sumWeights;
            position[1] = sumY / sumWeights;
            return position;
        }

        return null;
    }

    public double calculateDistance(int rssi) {
        return Math.pow(10, ((double)(RSSI_AT_1M - rssi)) / (10 * PATH_LOSS_EXPONENT));
    }

    public double calculateAccuracy(@NonNull List<BeaconData> beacons) {
        if (beacons.isEmpty()) {
            return Double.MAX_VALUE;
        }

        double[] position = calculatePosition(beacons);
        if (position == null) {
            return Double.MAX_VALUE;
        }

        if (beacons.size() >= MIN_BEACONS_FOR_TRILATERATION) {
            double[][] beaconPositions = new double[beacons.size()][2];
            double[] distances = new double[beacons.size()];

            for (int i = 0; i < beacons.size(); i++) {
                BeaconData beacon = beacons.get(i);
                beaconPositions[i] = new double[]{beacon.getX(), beacon.getY()};
                distances[i] = calculateDistance(beacon.getRssi());
            }

            return trilaterationCalculator.calculateAccuracy(position, beaconPositions, distances);
        }

        // 가중 평균 방식일 때의 정확도 계산
        double sumAccuracy = 0;
        for (BeaconData beacon : beacons) {
            double distance = calculateDistance(beacon.getRssi());
            sumAccuracy += 1.0 / distance;
        }

        return Math.min(10.0, 1.0 / (sumAccuracy / beacons.size()));
    }
}