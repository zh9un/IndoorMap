package com.example.navermapapi;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
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

    private Paint paint;
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

        initializeUI(); // UI 요소 초기화
        initializeSensors(); // 센서 초기화
        initializeBeaconManager(); // 비콘 매니저 초기화

        paint = new Paint();
        paint.setAntiAlias(true);
        paint.setStrokeWidth(5f);

        for (int i = 0; i < 4; i++) {
            kalmanCovariance[i][i] = 1000;
        }

        updateUI(); // UI 업데이트
        Log.d(TAG, "ProjectBActivity onCreate completed");
    }

    // UI 초기화 메서드 - UI 요소를 초기화하고 리스너를 설정합니다.
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

    // 센서 초기화 메서드 - 필요한 센서들을 초기화합니다.
    private void initializeSensors() {
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        stepDetector = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);
    }

    // 비콘 매니저 초기화 메서드 - 비콘을 이용한 위치 추적을 위해 비콘 매니저를 초기화합니다.
    private void initializeBeaconManager() {
        beaconLocationManager = new BeaconLocationManager(this, this);
        Log.d(TAG, "BeaconLocationManager initialized");
    }

    // 추적 시작/중지 토글 메서드
    private void toggleTracking() {
        if (isTracking) {
            stopTracking(); // 추적 중지
        } else {
            startTracking(); // 추적 시작
        }
    }

    // 추적 시작 메서드 - 센서 리스너를 등록하고 추적을 시작합니다.
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

    // 추적 중지 메서드 - 센서 리스너를 해제하고 추적을 중지합니다.
    private void stopTracking() {
        isTracking = false;
        unregisterSensors();
        startStopButton.setText("시작");
        statusView.setVisibility(View.GONE);
        Log.d(TAG, "Tracking stopped");
    }

    // 초기 위치 설정 메서드 - 비콘 스캔을 통해 초기 위치를 설정합니다.
    private void resetInitialPosition() {
        isInitialPositionSet = false;
        statusView.setText("초기 위치 설정 중...");
        statusView.setVisibility(View.VISIBLE);
        beaconLocationManager.startBeaconScan();
        resetInitialPositionButton.setEnabled(false);

        stopTracking();
    }

    // 센서 리스너 등록 메서드 - 센서 데이터를 수집하기 위해 리스너를 등록합니다.
    private void registerSensors() {
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
        sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_GAME);
        sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_GAME);
        sensorManager.registerListener(this, stepDetector, SensorManager.SENSOR_DELAY_NORMAL);
        Log.d(TAG, "Sensors registered");
    }

    // 센서 리스너 해제 메서드 - 센서 데이터를 수집 중단하기 위해 리스너를 해제합니다.
    private void unregisterSensors() {
        sensorManager.unregisterListener(this);
        Log.d(TAG, "Sensors unregistered");
    }
    @Override
    public void onLocationEstimated(double x, double y) {
        // 초기 위치 설정 완료 시 호출되는 메서드
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
        // 초기 위치 설정 실패 시 호출되는 메서드
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

                startTracking();
            }
            statusView.setVisibility(View.GONE);
        });
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        // 센서 값이 변경될 때 호출되는 메서드
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

    // 위치 업데이트 메서드 - 걸음 감지 후 위치를 업데이트합니다.
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
        mapView.invalidate();
        Log.d(TAG, "Step detected, new position: (" + positionX + ", " + positionY + ")");
    }

    // 보폭 계산 메서드 - 가속도계 데이터를 기반으로 동적으로 보폭을 계산합니다.
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

    // 방향 각도 업데이트 메서드 - 센서 데이터를 통해 방향 각도를 계산하고 필터링합니다.
    private void updateOrientationAngles() {
        SensorManager.getRotationMatrix(rotationMatrix, null, accelerometerReading, magnetometerReading);
        float[] orientation = new float[3];
        SensorManager.getOrientation(rotationMatrix, orientation);

        float gyroRotationZ = gyroscopeReading[2];
        orientation[0] += gyroRotationZ * 0.02f;

        for (int i = 0; i < 3; i++) {
            filteredOrientation[i] = filteredOrientation[i] + ALPHA * (orientation[i] - filteredOrientation[i]);
        }
    }
    // UI 업데이트 메서드 - 총 이동 거리와 방향을 UI에 반영합니다.
    private void updateUI() {
        totalDistanceView.setText(String.format("총 이동 거리: %.2f m", totalDistance));
        String direction = getCardinalDirection(filteredOrientation[0]);
        logView.setText(String.format("방향: %s (%.0f°)", direction, Math.toDegrees(filteredOrientation[0])));
        destinationManager.updateRemainingSteps(positionX, positionY);
    }

    // 방위각을 이용해 방향을 문자열로 반환하는 메서드
    private String getCardinalDirection(float azimuth) {
        float degrees = (float) Math.toDegrees(azimuth);
        if (degrees < 0) {
            degrees += 360;
        }
        String[] directions = {"북", "북동", "동", "남동", "남", "남서", "서", "북서"};
        return directions[(int) Math.round(degrees / 45) % 8];
    }

    // 이동 로그 추가 메서드 - 이전 위치와 현재 위치를 기반으로 이동 로그를 추가합니다.
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

    // 이동 로그 뷰 업데이트 메서드 - 이동 로그를 UI에 업데이트합니다.
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
        Log.d(TAG, "Activity destroyed");
    }

    // 지도 뷰 클래스 - 사용자의 위치와 이동 경로를 시각화합니다.
    private class MapView extends View {
        private static final float ARROW_SIZE = 60f;
        private float scaleFactor = 200f;
        private float offsetX = 0f;
        private float offsetY = 0f;
        private ScaleGestureDetector scaleDetector;
        private float lastTouchX;
        private float lastTouchY;
        private static final int INVALID_POINTER_ID = -1;
        private int activePointerId = INVALID_POINTER_ID;

        public MapView(Context context) {
            super(context);
            scaleDetector = new ScaleGestureDetector(context, new ScaleListener());
        }

        @Override
        public boolean onTouchEvent(MotionEvent ev) {
            scaleDetector.onTouchEvent(ev);

            final int action = ev.getAction();
            switch (action & MotionEvent.ACTION_MASK) {
                case MotionEvent.ACTION_DOWN: {
                    final float x = ev.getX();
                    final float y = ev.getY();
                    lastTouchX = x;
                    lastTouchY = y;
                    activePointerId = ev.getPointerId(0);
                    break;
                }
                case MotionEvent.ACTION_MOVE: {
                    final int pointerIndex = ev.findPointerIndex(activePointerId);
                    final float x = ev.getX(pointerIndex);
                    final float y = ev.getY(pointerIndex);
                    if (!scaleDetector.isInProgress()) {
                        final float dx = x - lastTouchX;
                        final float dy = y - lastTouchY;
                        offsetX += dx;
                        offsetY += dy;
                        invalidate();
                    }
                    lastTouchX = x;
                    lastTouchY = y;
                    break;
                }
                case MotionEvent.ACTION_UP: {
                    activePointerId = INVALID_POINTER_ID;
                    break;
                }
                case MotionEvent.ACTION_CANCEL: {
                    activePointerId = INVALID_POINTER_ID;
                    break;
                }
                case MotionEvent.ACTION_POINTER_UP: {
                    final int pointerIndex = (ev.getAction() & MotionEvent.ACTION_POINTER_INDEX_MASK)
                            >> MotionEvent.ACTION_POINTER_INDEX_SHIFT;
                    final int pointerId = ev.getPointerId(pointerIndex);
                    if (pointerId == activePointerId) {
                        final int newPointerIndex = pointerIndex == 0 ? 1 : 0;
                        lastTouchX = ev.getX(newPointerIndex);
                        lastTouchY = ev.getY(newPointerIndex);
                        activePointerId = ev.getPointerId(newPointerIndex);
                    }
                    break;
                }
            }
            return true;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int width = getWidth();
            int height = getHeight();
            int centerX = width / 2;
            int centerY = height / 2;

            canvas.save();
            canvas.translate(offsetX, offsetY);
            canvas.scale(scaleFactor, scaleFactor, centerX, centerY);

            canvas.drawColor(Color.WHITE);

            // 비콘 위치 그리기
            paint.setColor(Color.GREEN);
            paint.setStyle(Paint.Style.FILL);
            for (BeaconLocationManager.Beacon beacon : beaconLocationManager.getBeacons()) {
                canvas.drawCircle(centerX + (float) beacon.getX(), centerY - (float) beacon.getY(), 30f / scaleFactor, paint);
            }

            // 이동 경로 그리기
            paint.setColor(Color.argb(76, 0, 255, 0));
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(20f / scaleFactor);
            if (!trailX.isEmpty()) {
                Path path = new Path();
                path.moveTo(centerX + (float) trailX.get(0).doubleValue(), centerY - (float) trailY.get(0).doubleValue());
                for (int i = 1; i < trailX.size(); i++) {
                    path.lineTo(centerX + (float) trailX.get(i).doubleValue(), centerY - (float) trailY.get(i).doubleValue());
                }
                canvas.drawPath(path, paint);
            }

            // 현재 위치 그리기
            paint.setColor(Color.RED);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(centerX + (float) positionX, centerY - (float) positionY, 20f / scaleFactor, paint);

            // 방향 화살표 그리기
            paint.setColor(Color.BLUE);
            float arrowX = centerX + (float) positionX;
            float arrowY = centerY - (float) positionY;
            float angle = filteredOrientation[0];

            Path arrowHead = new Path();
            arrowHead.moveTo(arrowX + ARROW_SIZE / scaleFactor * (float) Math.sin(angle),
                    arrowY - ARROW_SIZE / scaleFactor * (float) Math.cos(angle));
            arrowHead.lineTo(arrowX + ARROW_SIZE * 0.7f / scaleFactor * (float) Math.sin(angle + Math.PI * 0.875),
                    arrowY - ARROW_SIZE * 0.7f / scaleFactor * (float) Math.cos(angle + Math.PI * 0.875));
            arrowHead.lineTo(arrowX + ARROW_SIZE * 0.7f / scaleFactor * (float) Math.sin(angle - Math.PI * 0.875),
                    arrowY - ARROW_SIZE * 0.7f / scaleFactor * (float) Math.cos(angle - Math.PI * 0.875));
            arrowHead.close();
            canvas.drawPath(arrowHead, paint);

            canvas.restore();

            drawCompass(canvas, width - 140, 140, 120);
        }

        // 나침반 그리기 메서드 - 나침반을 그려 사용자의 방향을 시각적으로 보여줍니다.
        private void drawCompass(Canvas canvas, float centerX, float centerY, float radius) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(Color.BLACK);
            canvas.drawCircle(centerX, centerY, radius, paint);

            paint.setStyle(Paint.Style.FILL);
            paint.setTextSize(24f);
            paint.setTextAlign(Paint.Align.CENTER);

            String[] directions = {"N", "E", "S", "W"};
            float[] angles = {0, 90, 180, 270};

            for (int i = 0; i < 4; i++) {
                float angle = (float) Math.toRadians(angles[i] - Math.toDegrees(filteredOrientation[0]));
                float x = centerX + (radius - 25) * (float) Math.sin(angle);
                float y = centerY - (radius - 25) * (float) Math.cos(angle);
                canvas.drawText(directions[i], x, y + 9, paint);
            }

            paint.setColor(Color.RED);
            canvas.drawLine(centerX, centerY,
                    centerX + radius * (float) Math.sin(-filteredOrientation[0]),
                    centerY - radius * (float) Math.cos(-filteredOrientation[0]), paint);
        }

        // 확대/축소 제스처 리스너 클래스
        private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                scaleFactor *= detector.getScaleFactor();
                scaleFactor = Math.max(0.1f, Math.min(scaleFactor, 10.0f));
                invalidate();
                return true;
            }
        }
    }

    // 행렬 연산 메서드들 - 칼만 필터 계산에 필요한 행렬 연산을 수행합니다.
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