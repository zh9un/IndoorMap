package com.example.navermapapi.beaconModule.internal.positioning;

public class TrilaterationCalculator {
    private static final int MAX_ITERATIONS = 100;
    private static final double CONVERGENCE_THRESHOLD = 0.001;

    public static double[] calculatePosition(double[][] beaconPositions, double[] distances) {
        if (beaconPositions.length < 3 || distances.length < 3) {
            throw new IllegalArgumentException("At least 3 beacons are required for trilateration");
        }

        // 초기 추정 위치 (비콘들의 중심점)
        double[] initialGuess = calculateInitialGuess(beaconPositions);
        double[] position = initialGuess.clone();

        // Gauss-Newton 알고리즘을 사용한 위치 최적화
        for (int i = 0; i < MAX_ITERATIONS; i++) {
            // Jacobian 행렬과 잔차 벡터 계산
            double[][] jacobian = new double[beaconPositions.length][2];
            double[] residuals = new double[beaconPositions.length];

            for (int j = 0; j < beaconPositions.length; j++) {
                double dx = position[0] - beaconPositions[j][0];
                double dy = position[1] - beaconPositions[j][1];
                double distance = Math.sqrt(dx * dx + dy * dy);

                // Jacobian 행렬 업데이트
                jacobian[j][0] = dx / distance;
                jacobian[j][1] = dy / distance;

                // 잔차 벡터 업데이트
                residuals[j] = distance - distances[j];
            }

            // 정규 방정식 해결
            double[] delta = solveNormalEquation(jacobian, residuals);

            // 위치 업데이트
            position[0] -= delta[0];
            position[1] -= delta[1];

            // 수렴 확인
            if (Math.abs(delta[0]) < CONVERGENCE_THRESHOLD &&
                    Math.abs(delta[1]) < CONVERGENCE_THRESHOLD) {
                break;
            }
        }

        return position;
    }

    private static double[] calculateInitialGuess(double[][] beaconPositions) {
        double sumX = 0;
        double sumY = 0;
        for (double[] position : beaconPositions) {
            sumX += position[0];
            sumY += position[1];
        }
        return new double[] {
                sumX / beaconPositions.length,
                sumY / beaconPositions.length
        };
    }

    private static double[] solveNormalEquation(double[][] jacobian, double[] residuals) {
        double[][] JTJ = new double[2][2];
        double[] JTr = new double[2];

        // JᵀJ 계산
        for (int i = 0; i < jacobian.length; i++) {
            for (int j = 0; j < 2; j++) {
                for (int k = 0; k < 2; k++) {
                    JTJ[j][k] += jacobian[i][j] * jacobian[i][k];
                }
                JTr[j] += jacobian[i][j] * residuals[i];
            }
        }

        // (JᵀJ)⁻¹JᵀR 계산
        double det = JTJ[0][0] * JTJ[1][1] - JTJ[0][1] * JTJ[1][0];

        return new double[] {
                (JTJ[1][1] * JTr[0] - JTJ[0][1] * JTr[1]) / det,
                (-JTJ[1][0] * JTr[0] + JTJ[0][0] * JTr[1]) / det
        };
    }

    // 위치 추정의 정확도 계산
    public static double calculateAccuracy(double[] position,
                                           double[][] beaconPositions,
                                           double[] distances) {
        double sumSquaredError = 0;

        for (int i = 0; i < beaconPositions.length; i++) {
            double dx = position[0] - beaconPositions[i][0];
            double dy = position[1] - beaconPositions[i][1];
            double calculatedDistance = Math.sqrt(dx * dx + dy * dy);
            double error = calculatedDistance - distances[i];
            sumSquaredError += error * error;
        }

        return Math.sqrt(sumSquaredError / beaconPositions.length);
    }
}