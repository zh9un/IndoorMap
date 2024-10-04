package com.example.navermapapi;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

public class IndoorMovementManager implements SensorEventListener {

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private Sensor gyroscope;
    private float[] gravity = new float[3];
    private float[] gyroData = new float[3];
    private float stepCount = 0;
    private float lastStepTime = 0;
    private NaverMapManager naverMapManager;

    public IndoorMovementManager(Context context, NaverMapManager naverMapManager) {
        this.naverMapManager = naverMapManager;
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
    }

    public void startIndoorMovementProcess() {
        Log.d("IndoorMovementManager", "센서 데이터 수신 시작");
        try {
            if (accelerometer != null) {
                sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
            } else {
                Log.e("IndoorMovementManager", "가속도계 센서를 찾을 수 없습니다.");
            }
            if (gyroscope != null) {
                sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_NORMAL);
            } else {
                Log.e("IndoorMovementManager", "자이로스코프 센서를 찾을 수 없습니다.");
            }
        } catch (Exception e) {
            Log.e("IndoorMovementManager", "센서 등록 중 오류 발생", e);
        }
    }

    public void stopIndoorMovementProcess() {
        sensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            gravity = event.values.clone();
            detectStep();
            Log.d("IndoorMovementManager", "가속도계 값: " + java.util.Arrays.toString(gravity));
        }

        if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            gyroData = event.values.clone();
            Log.d("IndoorMovementManager", "자이로스코프 값: " + java.util.Arrays.toString(gyroData));
        }
    }

    private void detectStep() {
        float currentTime = System.currentTimeMillis();
        if (currentTime - lastStepTime > 500) {
            stepCount++;
            lastStepTime = currentTime;
            updateIndoorPositionOnMap();
        }
    }

    private void updateIndoorPositionOnMap() {
        float xOffset = stepCount * 0.000008f; // 위도 방향 이동량 (약 1m)
        float yOffset = stepCount * 0.000010f; // 경도 방향 이동량 (약 1m)
        naverMapManager.updateIndoorPositionOnMap(xOffset, yOffset);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // 필요 시 처리
    }
}