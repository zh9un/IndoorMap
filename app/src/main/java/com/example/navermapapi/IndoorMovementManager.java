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

    // IndoorMovementManager 생성자 - 센서 매니저와 센서들을 초기화하고, NaverMapManager와 연결합니다.
    public IndoorMovementManager(Context context, NaverMapManager naverMapManager) {
        this.naverMapManager = naverMapManager;
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
    }

    // 실내 이동 프로세스 시작 - 가속도계와 자이로스코프 센서를 등록하여 센서 데이터 수신을 시작합니다.
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

    // 실내 이동 프로세스 중지 - 센서 리스너를 해제하여 센서 데이터 수신을 중단합니다.
    public void stopIndoorMovementProcess() {
        sensorManager.unregisterListener(this);
    }

    // 센서 데이터가 변경될 때마다 호출되는 메서드 - 가속도계와 자이로스코프 데이터를 처리합니다.
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            // 가속도계 데이터 수신 및 처리
            gravity = event.values.clone();
            detectStep(); // 걸음 감지 메서드 호출
            Log.d("IndoorMovementManager", "가속도계 값: " + java.util.Arrays.toString(gravity));
        }

        if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            // 자이로스코프 데이터 수신 및 처리
            gyroData = event.values.clone();
            Log.d("IndoorMovementManager", "자이로스코프 값: " + java.util.Arrays.toString(gyroData));
        }
    }

    // 걸음 감지 메서드 - 일정 시간이 지나면 걸음이 감지되었다고 판단하고 실내 위치를 업데이트합니다.
    private void detectStep() {
        float currentTime = System.currentTimeMillis();
        if (currentTime - lastStepTime > 500) { // 500ms 이상의 간격이 있는 경우에만 걸음으로 판단
            stepCount++;
            lastStepTime = currentTime;
            updateIndoorPositionOnMap(); // 걸음이 감지되면 지도상의 위치를 업데이트
        }
    }

    // 지도상의 실내 위치 업데이트 메서드 - 사용자가 걸음을 걸을 때마다 지도상의 위치를 업데이트합니다.
    private void updateIndoorPositionOnMap() {
        float xOffset = stepCount * 0.000008f; // 위도 방향 이동량 (약 1m)
        float yOffset = stepCount * 0.000010f; // 경도 방향 이동량 (약 1m)
        naverMapManager.updateIndoorPositionOnMap(xOffset, yOffset);
    }

    // 센서 정확도 변경 시 호출되는 메서드 - 현재 구현에서는 사용되지 않습니다.
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // 필요 시 처리
    }
}