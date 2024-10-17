package com.example.navermapapi;

import android.util.Log;

public class PositionTracker {
    private static final String TAG = "PositionTracker";
    private static final double PROCESS_NOISE = 0.001;
    private static final double MEASUREMENT_NOISE = 0.01;

    private double positionX = 0.0;
    private double positionY = 0.0;
    private float totalDistance = 0f;
    private double stepLength = 0.75; // 초기 보폭 값 (m)

    private double[][] kalmanState = new double[][]{{0}, {0}, {0}, {0}}; // [x, y, vx, vy]
    private double[][] kalmanCovariance = new double[4][4];

    public PositionTracker() {
        for (int i = 0; i < 4; i++) {
            kalmanCovariance[i][i] = 1000;
        }
    }

    public void updatePosition(float[] orientationAngles, long timeDelta) {
        double stepX = stepLength * Math.sin(orientationAngles[0]);
        double stepY = stepLength * Math.cos(orientationAngles[0]);

        double[][] F = {
                {1, 0, timeDelta / 1000.0, 0},
                {0, 1, 0, timeDelta / 1000.0},
                {0, 0, 1, 0},
                {0, 0, 0, 1}
        };
        kalmanState = PositionCalculator.matrixMultiply(F, kalmanState);
        kalmanCovariance = PositionCalculator.matrixAdd(PositionCalculator.matrixMultiply(PositionCalculator.matrixMultiply(F, kalmanCovariance), PositionCalculator.transposeMatrix(F)),
                PositionCalculator.scalarMultiply(PROCESS_NOISE, PositionCalculator.identityMatrix(4)));

        double[][] H = {
                {1, 0, 0, 0},
                {0, 1, 0, 0}
        };
        double[][] measurement = {{positionX + stepX}, {positionY + stepY}};
        double[][] y = PositionCalculator.matrixSubtract(measurement, PositionCalculator.matrixMultiply(H, kalmanState));
        double[][] S = PositionCalculator.matrixAdd(PositionCalculator.matrixMultiply(PositionCalculator.matrixMultiply(H, kalmanCovariance), PositionCalculator.transposeMatrix(H)),
                PositionCalculator.scalarMultiply(MEASUREMENT_NOISE, PositionCalculator.identityMatrix(2)));
        double[][] K = PositionCalculator.matrixMultiply(PositionCalculator.matrixMultiply(kalmanCovariance, PositionCalculator.transposeMatrix(H)), PositionCalculator.inverseMatrix(S));
        kalmanState = PositionCalculator.matrixAdd(kalmanState, PositionCalculator.matrixMultiply(K, y));
        kalmanCovariance = PositionCalculator.matrixMultiply(PositionCalculator.matrixSubtract(PositionCalculator.identityMatrix(4), PositionCalculator.matrixMultiply(K, H)), kalmanCovariance);

        positionX = kalmanState[0][0];
        positionY = kalmanState[1][0];

        totalDistance += stepLength;

        Log.d(TAG, "Step detected, new position: (" + positionX + ", " + positionY + ")");
    }

    public void setInitialPosition(double x, double y) {
        positionX = x;
        positionY = y;
        kalmanState[0][0] = x;
        kalmanState[1][0] = y;
    }

    public void updateStepLength(float[] accelerometerReading) {
        stepLength = PositionCalculator.calculateStepLength(accelerometerReading);
    }

    public double getPositionX() {
        return positionX;
    }

    public double getPositionY() {
        return positionY;
    }

    public float getTotalDistance() {
        return totalDistance;
    }
}