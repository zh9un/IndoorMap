package com.example.navermapapi.beaconModule.internal.pdr;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import com.example.navermapapi.coreModule.utils.filter.NoiseFilter;
import android.util.Log;
import androidx.annotation.NonNull;
import java.util.ArrayList;
import java.util.List;


/**
 * StepDetector
 *
 * 가속도 센서 기반 걸음 감지 및 걸음 길이 추정
 * 시각 장애인의 다양한 보행 패턴을 고려하여 최적화됨
 */
public class StepDetector implements SensorEventListener {
    private static final String TAG = "StepDetector";

    // 걸음 감지 관련 상수
    private static final float STEP_THRESHOLD = 10.0f;        // 걸음 감지 임계값
    private static final float MIN_STEP_LENGTH = 0.5f;        // 최소 보폭 (미터)
    private static final float MAX_STEP_LENGTH = 0.8f;        // 최대 보폭 (미터)
    private static final int STABLE_PERIOD = 200;             // 안정화 기간 (ms)
    private static final float GRAVITY = 9.81f;               // 중력 가속도

    private final SensorManager sensorManager;
    private final NoiseFilter accelerometerFilter;
    private final List<StepCallback> callbacks;

    private float lastAcceleration = 0;
    private long lastStepTime = 0;
    private int stepCount = 0;
    private float currentStepLength = MIN_STEP_LENGTH;

    // 보행 상태
    private boolean isWalking = false;
    private float walkingFrequency = 0;
    private final List<Float> recentStepPeriods;

    /**
     * 생성자
     * @param context 애플리케이션 컨텍스트
     */
    public StepDetector(@NonNull Context context) {
        this.sensorManager = (SensorManager)
                context.getSystemService(Context.SENSOR_SERVICE);
        this.accelerometerFilter = new NoiseFilter(10, 2.0);
        this.callbacks = new ArrayList<>();
        this.recentStepPeriods = new ArrayList<>();

        initializeSensors();
    }

    /**
     * 센서 초기화
     */
    private void initializeSensors() {
        try {
            Sensor accelerometer =
                    sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            if (accelerometer != null) {
                sensorManager.registerListener(
                        this,
                        accelerometer,
                        SensorManager.SENSOR_DELAY_GAME
                );
                Log.i(TAG, "Accelerometer sensor initialized");
            } else {
                Log.e(TAG, "No accelerometer sensor available");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error initializing sensors", e);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) {
            return;
        }

        try {
            // 3축 가속도 벡터의 크기 계산
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];
            float acceleration = (float) Math.sqrt(x*x + y*y + z*z);

            // 중력 가속도 제거
            acceleration = Math.abs(acceleration - GRAVITY);

            // 노이즈 필터링 적용
            acceleration = (float) accelerometerFilter.filter(acceleration);

            // 걸음 감지 로직 수행
            detectStep(acceleration);
        } catch (Exception e) {
            Log.e(TAG, "Error processing accelerometer data", e);
        }
    }

    /**
     * 걸음 감지 및 분석
     * @param acceleration 필터링된 가속도 값
     */
    private void detectStep(float acceleration) {
        long currentTime = System.currentTimeMillis();

        // 이전 걸음과의 시간 간격이 너무 짧으면 무시 (흔들림 방지)
        if (currentTime - lastStepTime < STABLE_PERIOD) {
            return;
        }

        // 가속도 변화가 임계값을 넘으면 걸음으로 인식
        if (Math.abs(acceleration - lastAcceleration) > STEP_THRESHOLD) {
            stepCount++;

            // 걸음 간격으로 보행 주파수 계산 및 업데이트
            float timeDiff = (currentTime - lastStepTime) / 1000f;
            if (timeDiff > 0) {
                updateWalkingFrequency(timeDiff);
                updateStepLength();
            }

            lastStepTime = currentTime;
            isWalking = true;

            // 콜백 알림
            notifyStepDetected();
        }

        // 일정 시간 동안 걸음이 감지되지 않으면 정지 상태로 판단
        if (currentTime - lastStepTime > 2000) {  // 2초
            isWalking = false;
            clearRecentStepPeriods();
        }

        lastAcceleration = acceleration;
    }

    /**
     * 보행 주파수 업데이트
     * @param stepPeriod 걸음 간격 (초)
     */
    private void updateWalkingFrequency(float stepPeriod) {
        // 최근 걸음 주기 기록 (최대 5개까지)
        recentStepPeriods.add(stepPeriod);
        if (recentStepPeriods.size() > 5) {
            recentStepPeriods.remove(0);
        }

        // 평균 보행 주기 계산
        float avgPeriod = 0;
        for (float period : recentStepPeriods) {
            avgPeriod += period;
        }
        avgPeriod /= recentStepPeriods.size();

        // 보행 주파수 업데이트 (Hz)
        walkingFrequency = 1f / avgPeriod;
    }

    /**
     * 보행 주파수에 따른 보폭 추정
     */
    private void updateStepLength() {
        // 보행 주파수에 따른 보폭 추정
        // 일반적으로 빠른 걸음일수록 보폭이 큼
        float normalizedFreq = Math.min(Math.max(walkingFrequency, 0.5f), 2.5f);
        currentStepLength = MIN_STEP_LENGTH +
                (MAX_STEP_LENGTH - MIN_STEP_LENGTH) *
                        (normalizedFreq - 0.5f) / 2.0f;
    }

    /**
     * 최근 걸음 주기 기록 초기화
     */
    private void clearRecentStepPeriods() {
        recentStepPeriods.clear();
        walkingFrequency = 0;
    }

    /**
     * 걸음 감지 콜백 등록
     */
    public void addStepCallback(@NonNull StepCallback callback) {
        if (!callbacks.contains(callback)) {
            callbacks.add(callback);
        }
    }

    /**
     * 걸음 감지 이벤트 알림
     */
    private void notifyStepDetected() {
        for (StepCallback callback : callbacks) {
            callback.onStepDetected(currentStepLength, stepCount);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // 정확도 변경 처리 (필요한 경우 구현)
    }

    /**
     * 걸음 감지 콜백 인터페이스
     */
    public interface StepCallback {
        void onStepDetected(float stepLength, int totalSteps);
    }

    // Getter 메서드들
    public boolean isWalking() {
        return isWalking;
    }

    public float getCurrentStepLength() {
        return currentStepLength;
    }

    public int getStepCount() {
        return stepCount;
    }

    /**
     * 리소스 정리
     */
    public void destroy() {
        sensorManager.unregisterListener(this);
        callbacks.clear();
        clearRecentStepPeriods();
    }
}