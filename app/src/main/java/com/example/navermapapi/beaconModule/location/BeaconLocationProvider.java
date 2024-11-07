package com.example.navermapapi.beaconModule.location;

import android.content.Context;
import com.example.navermapapi.beaconModule.internal.beacon.manager.BeaconManager;
import com.example.navermapapi.beaconModule.internal.beacon.data.model.BeaconData;
import com.example.navermapapi.beaconModule.internal.pdr.manager.PdrManager;
import com.example.navermapapi.beaconModule.internal.floor.FloorManager;
import com.example.navermapapi.beaconModule.internal.floor.FloorTransitionEvent;
import com.example.navermapapi.beaconModule.internal.floor.FloorTransitionDetector;
import com.example.navermapapi.beaconModule.internal.floor.FloorTransitionDetector.TransitionType;
import java.util.List;
import java.util.ArrayList;

public class BeaconLocationProvider {
    private final Context context;
    private final BeaconManager beaconManager;
    private final PdrManager pdrManager;
    private final FloorManager floorManager;
    private final List<LocationCallback> callbacks;

    private LocationData currentLocation;
    private boolean isRunning = false;

    public BeaconLocationProvider(Context context) {
        this.context = context;
        this.beaconManager = new BeaconManager(context);
        this.pdrManager = new PdrManager(context);
        this.floorManager = new FloorManager(context);
        this.callbacks = new ArrayList<>();

        initializeListeners();
    }

    private void initializeListeners() {
        // 비콘 위치 업데이트 리스너
        beaconManager.addCallback(new BeaconManager.BeaconManagerCallback() {
            @Override
            public void onBeaconUpdated(BeaconData beacon) {
                updateLocationWithBeacon(beacon);
            }
        });

        // PDR 위치 업데이트 리스너
        pdrManager.addCallback(new PdrManager.PdrCallback() {
            @Override
            public void onPositionUpdated(PdrManager.PdrPosition position) {
                updateLocationWithPdr(position);
            }

            @Override
            public void onHeadingUpdated(double heading) {
                if (currentLocation != null) {
                    currentLocation.setHeading(heading);
                    notifyLocationChanged();
                }
            }
        });

        // 층 변경 리스너
        floorManager.addCallback(new FloorManager.FloorCallback() {
            @Override
            public void onFloorChanged(int floor, FloorManager.FloorChangeType type) {
                if (currentLocation != null) {
                    currentLocation.setFloor(floor);
                    notifyLocationChanged();
                }
            }

            @Override
            public void onFloorTransition(FloorTransitionEvent event) {
                handleFloorTransition(event);
            }
        });
    }

    public void start() {
        if (!isRunning) {
            isRunning = true;
            beaconManager.startScanning();
            pdrManager.start();
            floorManager.start();
        }
    }

    public void stop() {
        if (isRunning) {
            isRunning = false;
            beaconManager.stopScanning();
            pdrManager.stop();
            floorManager.stop();
        }
    }

    private void updateLocationWithBeacon(BeaconData beacon) {
        // PDR과 비콘 위치 융합
        if (currentLocation == null) {
            currentLocation = new LocationData(beacon.getX(), beacon.getY(),
                    floorManager.getCurrentFloor());
        } else {
            // 칼만 필터나 가중 평균을 사용한 위치 융합
            double weight = calculateBeaconWeight(beacon);
            currentLocation.setX(currentLocation.getX() * (1 - weight) + beacon.getX() * weight);
            currentLocation.setY(currentLocation.getY() * (1 - weight) + beacon.getY() * weight);
        }
        currentLocation.setAccuracy(calculateAccuracy(beacon));
        notifyLocationChanged();
    }

    private void updateLocationWithPdr(PdrManager.PdrPosition position) {
        if (currentLocation == null) {
            currentLocation = new LocationData(position.x, position.y,
                    floorManager.getCurrentFloor());
        } else {
            // PDR 데이터로 위치 업데이트
            currentLocation.setX(position.x);
            currentLocation.setY(position.y);
            currentLocation.setHeading(position.heading);
        }
        notifyLocationChanged();
    }

    private void handleFloorTransition(FloorTransitionEvent event) {
        if (event.isValid() && currentLocation != null) {
            currentLocation.setFloor(event.getTargetFloor());
            currentLocation.setMovementState(
                    event.getType() == TransitionType.ELEVATOR ?
                            MovementState.ELEVATOR : MovementState.STAIRS
            );
            notifyLocationChanged();
        }
    }

    private double calculateBeaconWeight(BeaconData beacon) {
        // RSSI 값을 기반으로 비콘 신뢰도 계산
        double rssiWeight = Math.min(Math.abs(beacon.getRssi()), 100) / 100.0;
        return Math.max(0.3, rssiWeight);  // 최소 30% 가중치 보장
    }

    private double calculateAccuracy(BeaconData beacon) {
        // RSSI를 기반으로 정확도 추정
        return Math.max(1.0, Math.abs(beacon.getRssi()) / 20.0);
    }

    public void addCallback(LocationCallback callback) {
        if (!callbacks.contains(callback)) {
            callbacks.add(callback);
        }
    }

    public void removeCallback(LocationCallback callback) {
        callbacks.remove(callback);
    }

    private void notifyLocationChanged() {
        if (currentLocation != null) {
            for (LocationCallback callback : callbacks) {
                callback.onLocationChanged(currentLocation);
            }
        }
    }

    public LocationData getCurrentLocation() {
        return currentLocation;
    }

    public FloorManager getFloorManager() {
        return floorManager;
    }

    public static class LocationData {
        private double x;
        private double y;
        private int floor;
        private double heading;
        private double accuracy;
        private MovementState movementState;
        private final long timestamp;

        public LocationData(double x, double y, int floor) {
            this.x = x;
            this.y = y;
            this.floor = floor;
            this.movementState = MovementState.WALKING;
            this.timestamp = System.currentTimeMillis();
        }

        // Getters
        public double getX() { return x; }
        public double getY() { return y; }
        public int getFloor() { return floor; }
        public double getHeading() { return heading; }
        public double getAccuracy() { return accuracy; }
        public MovementState getMovementState() { return movementState; }
        public long getTimestamp() { return timestamp; }

        // Setters
        public void setX(double x) { this.x = x; }
        public void setY(double y) { this.y = y; }
        public void setFloor(int floor) { this.floor = floor; }
        public void setHeading(double heading) { this.heading = heading; }
        public void setAccuracy(double accuracy) { this.accuracy = accuracy; }
        public void setMovementState(MovementState state) { this.movementState = state; }
    }

    public enum MovementState {
        WALKING,
        STAIRS,
        ELEVATOR,
        STATIONARY
    }

    public interface LocationCallback {
        void onLocationChanged(LocationData location);
    }
}