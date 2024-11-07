package com.example.navermapapi.beaconModule.internal.pdr.manager;

import android.content.Context;
import com.example.navermapapi.beaconModule.internal.pdr.step.StepDetector;
import com.example.navermapapi.beaconModule.internal.pdr.step.StepLengthEstimator;
import com.example.navermapapi.beaconModule.internal.pdr.orientation.OrientationCalculator;
import com.example.navermapapi.beaconModule.internal.pdr.orientation.HeadingProvider;
import java.util.ArrayList;
import java.util.List;

public class PdrManager {
    private final Context context;
    private final PdrSensorManager sensorManager;
    private final StepDetector stepDetector;
    private final StepLengthEstimator stepLengthEstimator;
    private final OrientationCalculator orientationCalculator;
    private final HeadingProvider headingProvider;
    private final List<PdrCallback> callbacks;

    private double currentX = 0;
    private double currentY = 0;
    private double currentHeading = 0;
    private boolean isRunning = false;

    public PdrManager(Context context) {
        this.context = context;
        this.sensorManager = new PdrSensorManager(context);
        this.callbacks = new ArrayList<>();

        this.stepDetector = new StepDetector(sensorManager);
        this.stepLengthEstimator = new StepLengthEstimator();
        this.orientationCalculator = new OrientationCalculator(sensorManager);
        this.headingProvider = new HeadingProvider(sensorManager);

        initializeListeners();
    }

    private void initializeListeners() {
        stepDetector.setStepCallback(this::onStepDetected);
        orientationCalculator.setOrientationCallback(this::onOrientationChanged);
        headingProvider.setHeadingCallback(this::onHeadingChanged);

        // 센서 이벤트 리스너 등록
        sensorManager.addCallback((type, values) -> {
            switch (type) {
                case ACCELEROMETER:
                    stepDetector.processAccelerometerData(values);
                    break;
                case GYROSCOPE:
                    orientationCalculator.processGyroscopeData(values);
                    break;
                case MAGNETOMETER:
                    headingProvider.processMagnetometerData(values);
                    break;
            }
        });
    }

    public void start() {
        if (!isRunning) {
            isRunning = true;
            sensorManager.start();
        }
    }

    public void stop() {
        if (isRunning) {
            isRunning = false;
            sensorManager.stop();
        }
    }

    private void onStepDetected(float stepAcceleration) {
        // 보폭 추정
        double stepLength = stepLengthEstimator.estimateStepLength(stepAcceleration);

        // 현재 위치 업데이트
        updatePosition(stepLength);

        // 콜백 알림
        notifyPositionUpdated();
    }

    private void onOrientationChanged(float[] orientation) {
        orientationCalculator.processOrientation(orientation);
    }

    private void onHeadingChanged(double heading) {
        this.currentHeading = heading;
        notifyHeadingUpdated();
    }

    private void updatePosition(double stepLength) {
        // 현재 방향을 기준으로 위치 업데이트
        double radians = Math.toRadians(currentHeading);
        currentX += stepLength * Math.sin(radians);
        currentY += stepLength * Math.cos(radians);
    }

    public void setInitialPosition(double x, double y) {
        this.currentX = x;
        this.currentY = y;
        notifyPositionUpdated();
    }

    public void addCallback(PdrCallback callback) {
        if (!callbacks.contains(callback)) {
            callbacks.add(callback);
        }
    }

    public void removeCallback(PdrCallback callback) {
        callbacks.remove(callback);
    }

    private void notifyPositionUpdated() {
        PdrPosition position = new PdrPosition(currentX, currentY, currentHeading);
        for (PdrCallback callback : callbacks) {
            callback.onPositionUpdated(position);
        }
    }

    private void notifyHeadingUpdated() {
        for (PdrCallback callback : callbacks) {
            callback.onHeadingUpdated(currentHeading);
        }
    }

    public static class PdrPosition {
        public final double x;
        public final double y;
        public final double heading;

        public PdrPosition(double x, double y, double heading) {
            this.x = x;
            this.y = y;
            this.heading = heading;
        }
    }

    public interface PdrCallback {
        void onPositionUpdated(PdrPosition position);
        void onHeadingUpdated(double heading);
    }
}