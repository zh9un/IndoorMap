package com.example.navermapapi.beaconModule.internal.beacon.positioning;

import com.example.navermapapi.beaconModule.internal.beacon.data.model.BeaconData;
import com.example.navermapapi.beaconModule.utils.SignalUtils;
import java.util.List;
import java.util.ArrayList;

public class PositionCalculator {
    private final SignalProcessor signalProcessor;
    private final Trilateration trilateration;

    public PositionCalculator() {
        this.signalProcessor = new SignalProcessor();
        this.trilateration = new Trilateration();
    }

    public Position calculatePosition(List<BeaconData> beacons) {
        if (beacons.size() < 3) {
            throw new IllegalArgumentException("최소 3개의 비콘이 필요합니다.");
        }

        List<BeaconDistance> beaconDistances = new ArrayList<>();

        // 각 비콘의 거리 계산
        for (BeaconData beacon : beacons) {
            double processedRssi = signalProcessor.processRssi(beacon.getRssi());
            double distance = signalProcessor.calculateDistance(processedRssi);
            beaconDistances.add(new BeaconDistance(
                    beacon.getX(),
                    beacon.getY(),
                    distance
            ));
        }

        // 삼변측량으로 위치 계산
        return trilateration.calculatePosition(beaconDistances);
    }

    public static class Position {
        public final double x;
        public final double y;
        public final double accuracy;

        public Position(double x, double y, double accuracy) {
            this.x = x;
            this.y = y;
            this.accuracy = accuracy;
        }
    }

    public static class BeaconDistance {
        public final double x;
        public final double y;
        public final double distance;

        public BeaconDistance(double x, double y, double distance) {
            this.x = x;
            this.y = y;
            this.distance = distance;
        }
    }
}