package com.example.navermapapi.beaconModule.internal.pdr;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;
import androidx.annotation.NonNull;
import java.util.ArrayList;
import java.util.List;
import com.example.navermapapi.coreModule.utils.filter.NoiseFilter;

/**
 * OrientationCalculator
 *
 * 사용자의 이동 방향을 계산하는 클래스
 * 자이로스코프, 가속도계, 지자기 센서 융합을 통한 정확한 방향 계산
 */
public class OrientationCalculator implements SensorEventListener {
    private static final String TAG = "OrientationCalculator";

    // 필터링 관련 상수
    private static final float ALPHA = 0.05f;  // 저주파 통과 필터 계수
    private static final int SENSOR_DELAY = SensorManager.SENSOR_DELAY_GAME;  // ~20ms
    private static final float RAD_TO_DEG = (float) (180.0f / Math.PI);

    // 자기장 간섭 임계값
    private static final float MIN_MAGNETIC_FIELD = 25.0f;  // μT
    private static final float MAX_MAGNETIC_FIELD = 65.0f;  // μT

    private final SensorManager sensorManager;
    private final NoiseFilter orientationFilter;
    private final List<OrientationCallback> callbacks;

    // 센서 데이터 배열
    private final float[] accelerometerReading = new float[3];
    private final float[] magnetometerReading = new float[3];
    private final float[] rotationMatrix = new float[9];
    private final float[] orientationAngles = new float[3];

    // 이전 방향각
    private float previousAzimuth = 0f;
    private boolean isCalibrated = false;

    /**
     * 생성자
     * @param context 애플리케이션 컨텍스트
     */
    public OrientationCalculator(@NonNull Context context) {
        this.sensorManager = (SensorManager)
                context.getSystemService(Context.SENSOR_SERVICE);
        this.orientationFilter = new NoiseFilter(5, 1.5);
        this.callbacks = new ArrayList<>();

        initializeSensors();
    }

    /**
     * 센서 초기화
     */
    private void initializeSensors() {
        try {
            // 가속도계 등록
            Sensor accelerometer =
                    sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            if (accelerometer != null) {
                sensorManager.registerListener(
                        this,
                        accelerometer,
                        SENSOR_DELAY,
                        SensorManager.SENSOR_STATUS_ACCURACY_HIGH
                );
            }

            // 지자기 센서 등록
            Sensor magnetometer =
                    sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
            if (magnetometer != null) {
                sensorManager.registerListener(
                        this,
                        magnetometer,
                        SENSOR_DELAY,
                        SensorManager.SENSOR_STATUS_ACCURACY_HIGH
                );
            }


            Log.i(TAG, "Orientation sensors initialized");
        } catch (Exception e) {
            Log.e(TAG, "Error initializing sensors", e);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        try {
            switch (event.sensor.getType()) {
                case Sensor.TYPE_ACCELEROMETER:
                    System.arraycopy(
                            event.values, 0,
                            accelerometerReading, 0,
                            accelerometerReading.length
                    );
                    updateOrientation();  // 가속도계 데이터가 갱신될 때마다 방향 업데이트
                    break;

                case Sensor.TYPE_MAGNETIC_FIELD:
                    System.arraycopy(
                            event.values, 0,
                            magnetometerReading, 0,
                            magnetometerReading.length
                    );
                    updateOrientation();  // 지자기 데이터가 갱신될 때마다 방향 업데이트
                    break;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing sensor data", e);
        }
    }

    /**
     * 방향 업데이트
     */
    private void updateOrientation() {
        // 회전 행렬 계산
        boolean rotationOK = SensorManager.getRotationMatrix(
                rotationMatrix,
                null,
                accelerometerReading,
                magnetometerReading
        );

        if (!rotationOK) {
            return;
        }

        // 방향 각도 계산
        SensorManager.getOrientation(rotationMatrix, orientationAngles);

        // 방위각을 도 단위로 변환 (0-360)
        float azimuth = (float) Math.toDegrees(orientationAngles[0]);
        if (azimuth < 0) {
            azimuth += 360;
        }

        // 노이즈 필터링 적용
        azimuth = (float) orientationFilter.filter(azimuth);

        // 급격한 변화 감지 및 보정
        if (Math.abs(azimuth - previousAzimuth) > 180) {
            // 360도 넘어가는 지점 처리
            if (azimuth > previousAzimuth) {
                while (azimuth - previousAzimuth > 180) {
                    azimuth -= 360;
                }
            } else {
                while (previousAzimuth - azimuth > 180) {
                    azimuth += 360;
                }
            }
        }

        // 보정된 방향 저장
        previousAzimuth = azimuth;


        // 필터링 감도 조정
        azimuth = (float) orientationFilter.filter(azimuth);

        // 콜백 즉시 호출
        if (!isMagneticInterference()) {
            notifyOrientationChanged(azimuth);
        } else {
            handleMagneticInterference(azimuth);
        }
    }

    /**
     * 자기장 간섭 체크
     */
    private boolean isMagneticInterference() {
        float totalMagnetic = 0;
        for (float value : magnetometerReading) {
            totalMagnetic += value * value;
        }
        totalMagnetic = (float) Math.sqrt(totalMagnetic);

        return totalMagnetic < MIN_MAGNETIC_FIELD ||
                totalMagnetic > MAX_MAGNETIC_FIELD;
    }

    /**
     * 자기장 간섭 상황 처리
     */
    private void handleMagneticInterference(float currentAzimuth) {
        // 간섭 상황에서는 이전 방향을 더 많이 신뢰
        float filteredAzimuth = previousAzimuth +
                ALPHA * (currentAzimuth - previousAzimuth);
        notifyOrientationChanged(filteredAzimuth);
    }

    /**
     * 방향 보정
     */
    public void calibrate() {
        // 초기 방향 보정
        if (isValidOrientation()) {
            float initialAzimuth = orientationAngles[0] * RAD_TO_DEG;
            if (initialAzimuth < 0) {
                initialAzimuth += 360;
            }
            previousAzimuth = initialAzimuth;
            isCalibrated = true;
            notifyCalibrationComplete(initialAzimuth);
        }
    }

    /**
     * 유효한 방향값 여부 확인
     */
    private boolean isValidOrientation() {
        return Math.abs(accelerometerReading[0]) < 3 &&
                Math.abs(accelerometerReading[1]) < 3 &&
                Math.abs(accelerometerReading[2]) < 11;
    }

    /**
     * 방향 변화 콜백 등록
     */
    public void addOrientationCallback(@NonNull OrientationCallback callback) {
        if (!callbacks.contains(callback)) {
            callbacks.add(callback);
        }
    }

    /**
     * 방향 변화 알림
     */
    private void notifyOrientationChanged(float azimuth) {
        for (OrientationCallback callback : callbacks) {
            callback.onOrientationChanged(azimuth);
        }
    }

    /**
     * 보정 완료 알림
     */
    private void notifyCalibrationComplete(float initialAzimuth) {
        for (OrientationCallback callback : callbacks) {
            callback.onCalibrationComplete(initialAzimuth);
        }
    }

    /**
     * 방향 변화 콜백 인터페이스
     */
    public interface OrientationCallback {
        void onOrientationChanged(float azimuth);
        void onCalibrationComplete(float initialAzimuth);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // 정확도 변경 처리 (필요한 경우 구현)
    }

    public float getCurrentAzimuth() {
        return previousAzimuth;
    }

    public boolean isCalibrated() {
        return isCalibrated;
    }

    /**
     * 리소스 정리
     */
    public void destroy() {
        sensorManager.unregisterListener(this);
        callbacks.clear();
        isCalibrated = false;
    }
}