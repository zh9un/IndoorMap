package com.example.navermapapi;

import android.util.Log;

public class PositionCalculator {
    private static final String TAG = "PositionCalculator";
    private static final double PROCESS_NOISE = 0.001;
    private static final double MEASUREMENT_NOISE = 0.01;

    public static float calculateStepLength(float[] accelerometerReading) {
        float accelerationMagnitude = (float) Math.sqrt(
                accelerometerReading[0] * accelerometerReading[0] +
                        accelerometerReading[1] * accelerometerReading[1] +
                        accelerometerReading[2] * accelerometerReading[2]);

        float baseStepLength = 0.75f;
        float dynamicFactor = (accelerationMagnitude - 9.8f) * 0.05f;
        float stepLength = Math.max(0.5f, Math.min(baseStepLength + dynamicFactor, 1.0f));
        Log.d(TAG, "Calculated step length: " + stepLength);
        return stepLength;
    }

    public static String getCardinalDirection(float azimuth) {
        float degrees = (float) Math.toDegrees(azimuth);
        if (degrees < 0) {
            degrees += 360;
        }
        String[] directions = {"북", "북동", "동", "남동", "남", "남서", "서", "북서"};
        return directions[(int) Math.round(degrees / 45) % 8];
    }

    public static double[][] matrixMultiply(double[][] a, double[][] b) {
        int m = a.length;
        int n = b[0].length;
        int o = b.length;
        double[][] result = new double[m][n];
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                for (int k = 0; k < o; k++) {
                    result[i][j] += a[i][k] * b[k][j];
                }
            }
        }
        return result;
    }

    public static double[][] matrixAdd(double[][] a, double[][] b) {
        int m = a.length;
        int n = a[0].length;
        double[][] result = new double[m][n];
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                result[i][j] = a[i][j] + b[i][j];
            }
        }
        return result;
    }

    public static double[][] matrixSubtract(double[][] a, double[][] b) {
        int m = a.length;
        int n = a[0].length;
        double[][] result = new double[m][n];
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                result[i][j] = a[i][j] - b[i][j];
            }
        }
        return result;
    }

    public static double[][] transposeMatrix(double[][] matrix) {
        int m = matrix.length;
        int n = matrix[0].length;
        double[][] transposed = new double[n][m];
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                transposed[j][i] = matrix[i][j];
            }
        }
        return transposed;
    }

    public static double[][] identityMatrix(int size) {
        double[][] identity = new double[size][size];
        for (int i = 0; i < size; i++) {
            identity[i][i] = 1;
        }
        return identity;
    }

    public static double[][] scalarMultiply(double scalar, double[][] matrix) {
        int m = matrix.length;
        int n = matrix[0].length;
        double[][] result = new double[m][n];
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                result[i][j] = scalar * matrix[i][j];
            }
        }
        return result;
    }

    public static double[][] inverseMatrix(double[][] matrix) {
        double det = matrix[0][0] * matrix[1][1] - matrix[0][1] * matrix[1][0];
        double[][] inverse = new double[2][2];
        inverse[0][0] = matrix[1][1] / det;
        inverse[0][1] = -matrix[0][1] / det;
        inverse[1][0] = -matrix[1][0] / det;
        inverse[1][1] = matrix[0][0] / det;
        return inverse;
    }
}