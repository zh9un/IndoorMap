package com.example.navermapapi;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ProjectBActivity extends AppCompatActivity implements SensorEventListener, BeaconLocationManager.LocationCallback {

    private static final String TAG = "ProjectBActivity";
    private float offsetAngle = (float) Math.PI;
    private AudioManager audioManager;

    private SensorManager sensorManager;
    private Sensor accelerometer, magnetometer, gyroscope, stepDetector;
    private float[] accelerometerReading = new float[3];
    private float[] magnetometerReading = new float[3];
    private float[] gyroscopeReading = new float[3];
    private float[] rotationMatrix = new float[9];
    private float[] orientationAngles = new float[3];

    private double positionX = 0.0;
    private double positionY = 0.0;
    private float totalDistance = 0f;
    private long lastStepTime = 0;
    private long lastUpdateTime = 0;
    private static final long UPDATE_INTERVAL = 50;

    private List<Double> trailX = new ArrayList<>();
    private List<Double> trailY = new ArrayList<>();
    private FrameLayout container;
    private TextView logView, totalDistanceView, movementLogView;
    private MapView mapView;

    private static final float ALPHA = 0.05f;
    private float[] filteredOrientation = new float[3];

    private double[][] kalmanState = new double[][]{{0}, {0}, {0}, {0}}; // [x, y, vx, vy]
    private double[][] kalmanCovariance = new double[4][4];
    private static final double PROCESS_NOISE = 0.001;
    private static final double MEASUREMENT_NOISE = 0.01;
    private double stepLength = 0.75; // 초기 보폭 값 (m)
    private DestinationManager destinationManager;

    private LinkedList<String> movementLogs = new LinkedList<>();
    private static final int MAX_LOG_LINES = 50;

    private SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

    private Button startStopButton;
    private Button resetInitialPositionButton;
    private TextView statusView;
    private boolean isTracking = false;
    private boolean isInitialPositionSet = false;
    private boolean isDataReliable = false;
    private int stepCount = 0;
    private static final int RELIABLE_STEP_THRESHOLD = 5;

    private BeaconLocationManager beaconLocationManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_project_b);
        destinationManager = new DestinationManager(this, 30.0); // 목적지까지 30m로 초기화
        audioManager = new AudioManager(this);

        initializeUI(); // UI 요소 초기화
        initializeSensors(); // 센서 초기화
        initializeBeaconManager(); // 비콘 매니저 초기화

        for (int i = 0; i < 4; i++) {
            kalmanCovariance[i][i] = 1000;
        }

        updateUI(); // UI 업데이트
        Log.d(TAG, "ProjectBActivity onCreate completed");
    }

    private void initializeUI() {
        container = findViewById(R.id.map_container);
        logView = findViewById(R.id.log_view);
        totalDistanceView = findViewById(R.id.total_distance_view);
        movementLogView = findViewById(R.id.movement_log_view);
        movementLogView.setMovementMethod(new ScrollingMovementMethod());
        mapView = new MapView(this);
        container.addView(mapView);

        startStopButton = findViewById(R.id.start_stop_button);
        resetInitialPositionButton = findViewById(R.id.reset_initial_position_button);
        statusView = findViewById(R.id.status_view);

        startStopButton.setOnClickListener(v -> {
            if (!isInitialPositionSet) {
                resetInitialPosition(); // 초기 위치 설정
            } else {
                toggleTracking(); // 추적 시작/중지 토글
            }
        });

        resetInitialPositionButton.setOnClickListener(v -> resetInitialPosition());
        Button backToMainButton = findViewById(R.id.back_to_main_button);
        backToMainButton.setOnClickListener(v -> finish()); // 메인 화면으로 돌아가기
    }

    private void initializeSensors() {
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        stepDetector = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);
    }

    private void initializeBeaconManager() {
        beaconLocationManager = new BeaconLocationManager(this, this);
        Log.d(TAG, "BeaconLocationManager initialized");
    }

    private void toggleTracking() {
        if (isTracking) {
            stopTracking(); // 추적 중지
        } else {
            startTracking(); // 추적 시작
        }
    }

    private void startTracking() {
        isTracking = true;
        isDataReliable = false;
        stepCount = 0;
        registerSensors();
        startStopButton.setText("중지");
        statusView.setText("이동 수집 중...");
        statusView.setVisibility(View.VISIBLE);
        Log.d(TAG, "Tracking started");
    }

    private void stopTracking() {
        isTracking = false;
        unregisterSensors();
        startStopButton.setText("시작");
        statusView.setVisibility(View.GONE);
        Log.d(TAG, "Tracking stopped");
    }

    private void resetInitialPosition() {
        isInitialPositionSet = false;
        statusView.setText("초기 위치 설정 중...");
        statusView.setVisibility(View.VISIBLE);
        beaconLocationManager.startBeaconScan();
        resetInitialPositionButton.setEnabled(false);

        stopTracking();
    }

    private void registerSensors() {
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
        sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_GAME);
        sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_GAME);
        sensorManager.registerListener(this, stepDetector, SensorManager.SENSOR_DELAY_NORMAL);
        Log.d(TAG, "Sensors registered");
    }

    private void unregisterSensors() {
        sensorManager.unregisterListener(this);
        Log.d(TAG, "Sensors unregistered");
    }

    @Override
    public void onLocationEstimated(double x, double y) {
        if (!isInitialPositionSet) {
            positionX = x;
            positionY = y;
            isInitialPositionSet = true;
            statusView.setText("초기 위치 설정 완료");
            statusView.setVisibility(View.GONE);
            beaconLocationManager.stopBeaconScan();
            resetInitialPositionButton.setEnabled(true);

            runOnUiThread(() -> {
                trailX.clear();
                trailY.clear();
                trailX.add(positionX);
                trailY.add(positionY);
                mapView.invalidate();
                startStopButton.setText("추적 시작");
                startStopButton.setEnabled(true);
            });
        }
    }

    @Override
    public void onLocationEstimationFailed() {
        runOnUiThread(() -> {
            statusView.setText("초기 위치 설정 실패. 기본 위치로 시작합니다.");
            resetInitialPositionButton.setEnabled(true);

            if (!isInitialPositionSet) {
                positionX = 0.0;
                positionY = 0.0;
                isInitialPositionSet = true;

                trailX.clear();
                trailY.clear();
                trailX.add(positionX);
                trailY.add(positionY);

                mapView.invalidate();

                startTracking();
            }
            statusView.setVisibility(View.GONE);
        });
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (!isTracking || !isInitialPositionSet) return;

        switch (event.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:
                System.arraycopy(event.values, 0, accelerometerReading, 0, accelerometerReading.length);
                break;
            case Sensor.TYPE_MAGNETIC_FIELD:
                System.arraycopy(event.values, 0, magnetometerReading, 0, magnetometerReading.length);
                break;
            case Sensor.TYPE_GYROSCOPE:
                System.arraycopy(event.values, 0, gyroscopeReading, 0, gyroscopeReading.length);
                break;
            case Sensor.TYPE_STEP_DETECTOR:
                updatePosition(); // 위치 업데이트
                break;
        }

        updateOrientationAngles(); // 방향 각도 업데이트

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastUpdateTime > UPDATE_INTERVAL) {
            updateUI(); // UI 업데이트
            lastUpdateTime = currentTime;
        }
    }

    private void updatePosition() {
        stepCount++;
        if (stepCount >= RELIABLE_STEP_THRESHOLD && !isDataReliable) {
            isDataReliable = true;
            statusView.setVisibility(View.GONE);
        }

        if (!isDataReliable) return;

        long currentTime = System.currentTimeMillis();
        float stepLength = calculateStepLength(); // 보폭 계산
        destinationManager.updateStepLength(stepLength);

        double stepX = stepLength * Math.sin(filteredOrientation[0]);
        double stepY = stepLength * Math.cos(filteredOrientation[0]);

        double timeDelta = (currentTime - lastStepTime) / 1000.0;

        double[][] F = {
                {1, 0, timeDelta, 0},
                {0, 1, 0, timeDelta},
                {0, 0, 1, 0},
                {0, 0, 0, 1}
        };
        kalmanState = matrixMultiply(F, kalmanState);
        kalmanCovariance = matrixAdd(matrixMultiply(matrixMultiply(F, kalmanCovariance), transposeMatrix(F)),
                scalarMultiply(PROCESS_NOISE, identityMatrix(4)));

        double[][] H = {
                {1, 0, 0, 0},
                {0, 1, 0, 0}
        };
        double[][] measurement = {{positionX + stepX}, {positionY + stepY}};
        double[][] y = matrixSubtract(measurement, matrixMultiply(H, kalmanState));
        double[][] S = matrixAdd(matrixMultiply(matrixMultiply(H, kalmanCovariance), transposeMatrix(H)),
                scalarMultiply(MEASUREMENT_NOISE, identityMatrix(2)));
        double[][] K = matrixMultiply(matrixMultiply(kalmanCovariance, transposeMatrix(H)), inverseMatrix(S));
        kalmanState = matrixAdd(kalmanState, matrixMultiply(K, y));
        kalmanCovariance = matrixMultiply(matrixSubtract(identityMatrix(4), matrixMultiply(K, H)), kalmanCovariance);

        double prevX = positionX;
        double prevY = positionY;
        positionX = kalmanState[0][0];
        positionY = kalmanState[1][0];

        totalDistance += stepLength;

        trailX.add(positionX);
        trailY.add(positionY);

        addMovementLog(prevX, prevY, positionX, positionY, currentTime);

        lastStepTime = currentTime;
        mapView.updatePosition(positionX, positionY);
        mapView.invalidate();
        Log.d(TAG, "Step detected, new position: (" + positionX + ", " + positionY + ")");
    }


    private float calculateStepLength() {
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

    private void updateOrientationAngles() {
        SensorManager.getRotationMatrix(rotationMatrix, null, accelerometerReading, magnetometerReading);
        float[] orientation = new float[3];
        SensorManager.getOrientation(rotationMatrix, orientation);

        float gyroRotationZ = gyroscopeReading[2];
        orientation[0] += gyroRotationZ * 0.02f;

        for (int i = 0; i < 3; i++) {
            filteredOrientation[i] = filteredOrientation[i] + ALPHA * (orientation[i] - filteredOrientation[i]);
        }

        mapView.updateOrientation(filteredOrientation);
    }

    private void updateUI() {
        totalDistanceView.setText(String.format("총 이동 거리: %.2f m", totalDistance));
        String direction = getCardinalDirection(filteredOrientation[0]);
        logView.setText(String.format("방향: %s (%.0f°)", direction, Math.toDegrees(filteredOrientation[0])));
        destinationManager.updateRemainingSteps(positionX, positionY);
        int remainingSteps = destinationManager.getRemainingSteps();
        if (remainingSteps % 10 == 0 || remainingSteps <= 5) {
            audioManager.speak(String.format("목적지까지 %d 걸음 남았습니다.", remainingSteps));
        }
    }

    private String getCardinalDirection(float azimuth) {
        float degrees = (float) Math.toDegrees(azimuth);
        if (degrees < 0) {
            degrees += 360;
        }
        String[] directions = {"북", "북동", "동", "남동", "남", "남서", "서", "북서"};
        return directions[(int) Math.round(degrees / 45) % 8];
    }

    private void addMovementLog(double fromX, double fromY, double toX, double toY, long time) {
        double dx = toX - fromX;
        double dy = toY - fromY;
        double distance = Math.sqrt(dx * dx + dy * dy);
        double angle = Math.toDegrees(Math.atan2(dx, dy));
        if (angle < 0) {
            angle += 360;
        }
        String timeString = sdf.format(new Date(time));
        String log = String.format("%s - 방향: %.0f°, 거리: %.2fm", timeString, angle, distance);
        movementLogs.addFirst(log);
        if (movementLogs.size() > MAX_LOG_LINES) {
            movementLogs.removeLast();
        }
        updateMovementLogView();
        Log.d(TAG, "Movement logged: " + log);
    }

    private void updateMovementLogView() {
        runOnUiThread(() -> {
            StringBuilder sb = new StringBuilder();
            for (String log : movementLogs) {
                sb.append(log).append("\n");
            }
            movementLogView.setText(sb.toString());
        });
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // 센서 정확도 변경 시 처리 (현재는 구현하지 않음)
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isTracking) {
            registerSensors();
        }
        Log.d(TAG, "Activity resumed");
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterSensors();
        beaconLocationManager.stopBeaconScan();
        Log.d(TAG, "Activity paused");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        beaconLocationManager.stopBeaconScan();
        audioManager.shutdown();
        Log.d(TAG, "Activity destroyed");
    }

    // 행렬 연산 메서드들
    private double[][] matrixMultiply(double[][] a, double[][] b) {
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

    private double[][] matrixAdd(double[][] a, double[][] b) {
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

    private double[][] matrixSubtract(double[][] a, double[][] b) {
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

    private double[][] transposeMatrix(double[][] matrix) {
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

    private double[][] identityMatrix(int size) {
        double[][] identity = new double[size][size];
        for (int i = 0; i < size; i++) {
            identity[i][i] = 1;
        }
        return identity;
    }

    private double[][] scalarMultiply(double scalar, double[][] matrix) {
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

    private double[][] inverseMatrix(double[][] matrix) {
        double det = matrix[0][0] * matrix[1][1] - matrix[0][1] * matrix[1][0];
        double[][] inverse = new double[2][2];
        inverse[0][0] = matrix[1][1] / det;
        inverse[0][1] = -matrix[0][1] / det;
        inverse[1][0] = -matrix[1][0] / det;
        inverse[1][1] = matrix[0][0] / det;
        return inverse;
    }
}