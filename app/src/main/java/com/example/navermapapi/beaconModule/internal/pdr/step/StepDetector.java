package com.example.navermapapi.beaconModule.internal.pdr.step;

import com.example.navermapapi.beaconModule.internal.pdr.manager.PdrSensorManager;
import java.util.concurrent.TimeUnit;

public class StepDetector {
    // 상수를 변수로 변경
    private float stepThreshold = 10.0f;         // 걸음 감지 임계값
    private long minStepInterval = 250;          // 최소 걸음 간격 (밀리초)
    private static final float GRAVITY_THRESHOLD = 0.1f;       // 중력 필터링 임계값
    private static final float NOISE_THRESHOLD = 0.5f;         // 노이즈 필터링 임계값

    private final PdrSensorManager sensorManager;
    private StepCallback callback;

    private float[] gravity = new float[3];
    private float lastMaxAcc = 0;
    private float lastMinAcc = 0;
    private boolean isAboveThreshold = false;
    private long lastStepTime = 0;

    // 걸음 검출 상태
    private enum StepState {
        WAITING,     // 다음 걸음 대기
        RISING,      // 가속도 상승
        FALLING      // 가속도 하강
    }
    private StepState currentState = StepState.WAITING;

    public StepDetector(PdrSensorManager sensorManager) {
        this.sensorManager = sensorManager;
    }

    public void setStepCallback(StepCallback callback) {
        this.callback = callback;
    }

    public void processAccelerometerData(float[] values) {
        // 중력 가속도 필터링
        filterGravity(values);

        // 선형 가속도 계산 (중력 제거)
        float[] linearAcc = new float[3];
        for (int i = 0; i < 3; i++) {
            linearAcc[i] = values[i] - gravity[i];
        }

        // 가속도 크기 계산
        float accMagnitude = calculateMagnitude(linearAcc);

        // 노이즈 필터링
        if (Math.abs(accMagnitude) < NOISE_THRESHOLD) {
            accMagnitude = 0;
        }

        detectStep(accMagnitude);
    }

    private void filterGravity(float[] values) {
        final float alpha = 0.8f;

        // 저주파 통과 필터로 중력 추출
        gravity[0] = alpha * gravity[0] + (1 - alpha) * values[0];
        gravity[1] = alpha * gravity[1] + (1 - alpha) * values[1];
        gravity[2] = alpha * gravity[2] + (1 - alpha) * values[2];
    }

    private float calculateMagnitude(float[] vector) {
        return (float) Math.sqrt(
                vector[0] * vector[0] +
                        vector[1] * vector[1] +
                        vector[2] * vector[2]
        );
    }

    private void detectStep(float accMagnitude) {
        long currentTime = System.currentTimeMillis();

        switch (currentState) {
            case WAITING:
                if (accMagnitude > stepThreshold) {
                    currentState = StepState.RISING;
                    lastMaxAcc = accMagnitude;
                }
                break;

            case RISING:
                if (accMagnitude > lastMaxAcc) {
                    lastMaxAcc = accMagnitude;
                } else if (accMagnitude < stepThreshold) {
                    currentState = StepState.FALLING;
                    lastMinAcc = accMagnitude;
                }
                break;

            case FALLING:
                if (accMagnitude < lastMinAcc) {
                    lastMinAcc = accMagnitude;
                } else if (accMagnitude > stepThreshold) {
                    // 걸음으로 인정되는 시점
                    long timeDiff = currentTime - lastStepTime;
                    if (timeDiff >= minStepInterval) {
                        onStepDetected(lastMaxAcc);
                        lastStepTime = currentTime;
                    }
                    currentState = StepState.RISING;
                    lastMaxAcc = accMagnitude;
                }
                break;
        }
    }

    private void onStepDetected(float stepAcceleration) {
        if (callback != null) {
            callback.onStepDetected(stepAcceleration);
        }
    }

    public interface StepCallback {
        void onStepDetected(float acceleration);
    }

    // 걸음 감지 알고리즘의 매개변수 조정을 위한 메서드
    public void setStepThreshold(float threshold) {
        if (threshold > 0) {
            this.stepThreshold = threshold;
        }
    }

    public void setMinStepInterval(long intervalMs) {
        if (intervalMs > 0) {
            this.minStepInterval = intervalMs;
        }
    }

    // 디버깅을 위한 현재 상태 정보 제공
    public StepState getCurrentState() {
        return currentState;
    }

    public float getLastMaxAcceleration() {
        return lastMaxAcc;
    }

    public long getLastStepTime() {
        return lastStepTime;
    }

    // 현재 임계값 조회를 위한 getter 메서드 추가
    public float getStepThreshold() {
        return stepThreshold;
    }

    public long getMinStepInterval() {
        return minStepInterval;
    }
}