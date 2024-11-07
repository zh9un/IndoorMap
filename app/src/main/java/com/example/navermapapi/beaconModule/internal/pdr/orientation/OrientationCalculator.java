package com.example.navermapapi.beaconModule.internal.pdr.orientation;

import com.example.navermapapi.beaconModule.internal.pdr.manager.PdrSensorManager;
import com.example.navermapapi.beaconModule.internal.pdr.manager.PdrSensorManager.SensorType;

public class OrientationCalculator {
    private static final float NS2S = 1.0f / 1000000000.0f; // 나노초를 초로 변환
    private static final float EPSILON = 0.000000001f;      // 0에 가까운 값 처리용

    private final float[] rotationMatrix = new float[9];
    private final float[] orientationAngles = new float[3];
    private final float[] gyroMatrix = new float[9];
    private final float[] gyroOrientation = new float[3];
    private final float[] accMagOrientation = new float[3];
    private final float[] fusedOrientation = new float[3];

    private float timestamp;
    private boolean initState = true;
    private OrientationCallback callback;

    // 상보필터 계수
    private static final float ALPHA = 0.96f;

    public OrientationCalculator(PdrSensorManager sensorManager) {
        gyroOrientation[0] = 0.0f;
        gyroOrientation[1] = 0.0f;
        gyroOrientation[2] = 0.0f;

        // 초기 회전 행렬 설정
        gyroMatrix[0] = 1.0f;
        gyroMatrix[1] = 0.0f;
        gyroMatrix[2] = 0.0f;
        gyroMatrix[3] = 0.0f;
        gyroMatrix[4] = 1.0f;
        gyroMatrix[5] = 0.0f;
        gyroMatrix[6] = 0.0f;
        gyroMatrix[7] = 0.0f;
        gyroMatrix[8] = 1.0f;
    }

    public void setOrientationCallback(OrientationCallback callback) {
        this.callback = callback;
    }

    public void processGyroscopeData(float[] gyroValues) {
        if (timestamp != 0) {
            final float dT = (System.nanoTime() - timestamp) * NS2S;

            // 각속도를 적분하여 회전 각도 계산
            float axisX = gyroValues[0];
            float axisY = gyroValues[1];
            float axisZ = gyroValues[2];

            float omegaMagnitude = (float) Math.sqrt(axisX*axisX + axisY*axisY + axisZ*axisZ);

            if (omegaMagnitude > EPSILON) {
                axisX /= omegaMagnitude;
                axisY /= omegaMagnitude;
                axisZ /= omegaMagnitude;
            }

            float thetaOverTwo = omegaMagnitude * dT / 2.0f;
            float sinThetaOverTwo = (float) Math.sin(thetaOverTwo);
            float cosThetaOverTwo = (float) Math.cos(thetaOverTwo);

            // 회전 행렬 업데이트
            updateGyroMatrix(sinThetaOverTwo, cosThetaOverTwo, axisX, axisY, axisZ);
            // 자이로스코프 기반 방향 계산
            calculateGyroOrientation();
        }
        timestamp = System.nanoTime();
    }

    public void processOrientation(float[] orientation) {
        // 가속도계와 자기장계로부터의 방향
        System.arraycopy(orientation, 0, accMagOrientation, 0, 3);

        if (initState) {
            System.arraycopy(accMagOrientation, 0, gyroOrientation, 0, 3);
            initState = false;
        }

        // 상보필터를 사용한 센서 퓨전
        fusedOrientation[0] = ALPHA * gyroOrientation[0] + (1.0f - ALPHA) * accMagOrientation[0];
        fusedOrientation[1] = ALPHA * gyroOrientation[1] + (1.0f - ALPHA) * accMagOrientation[1];
        fusedOrientation[2] = ALPHA * gyroOrientation[2] + (1.0f - ALPHA) * accMagOrientation[2];

        // 방향 정보 콜백 전달
        if (callback != null) {
            callback.onOrientationChanged(fusedOrientation);
        }
    }

    private void updateGyroMatrix(float sinThetaOverTwo, float cosThetaOverTwo,
                                  float axisX, float axisY, float axisZ) {
        float[] deltaMatrix = new float[9];
        deltaMatrix[0] = 1.0f - 2.0f * (axisY * axisY + axisZ * axisZ);
        deltaMatrix[1] = 2.0f * (axisX * axisY - axisZ * cosThetaOverTwo);
        deltaMatrix[2] = 2.0f * (axisX * axisZ + axisY * cosThetaOverTwo);
        deltaMatrix[3] = 2.0f * (axisX * axisY + axisZ * cosThetaOverTwo);
        deltaMatrix[4] = 1.0f - 2.0f * (axisX * axisX + axisZ * axisZ);
        deltaMatrix[5] = 2.0f * (axisY * axisZ - axisX * cosThetaOverTwo);
        deltaMatrix[6] = 2.0f * (axisX * axisZ - axisY * cosThetaOverTwo);
        deltaMatrix[7] = 2.0f * (axisY * axisZ + axisX * cosThetaOverTwo);
        deltaMatrix[8] = 1.0f - 2.0f * (axisX * axisX + axisY * axisY);

        // 행렬 곱셈
        multiplyMatrices(gyroMatrix, deltaMatrix);
    }

    private void calculateGyroOrientation() {
        // 회전 행렬로부터 오일러 각도 추출
        gyroOrientation[0] = (float) Math.atan2(gyroMatrix[1], gyroMatrix[4]);
        gyroOrientation[1] = (float) Math.asin(-gyroMatrix[7]);
        gyroOrientation[2] = (float) Math.atan2(-gyroMatrix[6], gyroMatrix[8]);
    }

    private void multiplyMatrices(float[] A, float[] B) {
        float[] result = new float[9];
        for(int i = 0; i < 3; i++) {
            for(int j = 0; j < 3; j++) {
                result[i * 3 + j] = 0;
                for(int k = 0; k < 3; k++) {
                    result[i * 3 + j] += A[i * 3 + k] * B[k * 3 + j];
                }
            }
        }
        System.arraycopy(result, 0, gyroMatrix, 0, 9);
    }

    public interface OrientationCallback {
        void onOrientationChanged(float[] orientation);
    }
}