package com.example.navermapapi;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.naver.maps.geometry.LatLng;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

public class ProjectBActivity extends AppCompatActivity implements CustomSensorManager.SensorDataListener, BeaconLocationManager.LocationCallback {

    private static final String TAG = "ProjectBActivity";
    private float offsetAngle = (float) Math.PI;
    private AudioManager audioManager;

    private CustomSensorManager customSensorManager;
    private float[] rotationMatrix = new float[9];
    private float[] orientationAngles = new float[3];

    private PositionTracker positionTracker;

    private List<Double> trailX = new ArrayList<>();
    private List<Double> trailY = new ArrayList<>();
    private FrameLayout container;
    private TextView logView, totalDistanceView, movementLogView;
    private MapView mapView;

    private static final float ALPHA = 0.05f;
    private float[] filteredOrientation = new float[3];

    private long lastStepTime = 0;
    private long lastUpdateTime = 0;
    private static final long UPDATE_INTERVAL = 50;

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

    private double gpsLatitude = 0.0;
    private double gpsLongitude = 0.0;

    private GPSManager gpsManager;
    private LocationDataManager locationDataManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_project_b);
        destinationManager = new DestinationManager(this, 30.0);
        audioManager = new AudioManager(this);

        positionTracker = new PositionTracker();

        FrameLayout remainingStepsContainer = findViewById(R.id.remaining_steps_container);
        destinationManager.setRemainingStepsContainer(remainingStepsContainer);

        initializeUI();
        initializeSensors();
        initializeManagers();
        initializeBuildingOutlineManager();

        IntentFilter filter = new IntentFilter("com.example.navermapapi.LOCATION_UPDATE");
        registerReceiver(locationUpdateReceiver, filter);

        startLocationTrackingService();

        loadSavedLocationData();
        updateUI();
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
                resetInitialPosition();
            } else {
                toggleTracking();
            }
        });

        resetInitialPositionButton.setOnClickListener(v -> resetInitialPosition());
        Button backToMainButton = findViewById(R.id.back_to_main_button);
        backToMainButton.setOnClickListener(v -> finish());
    }

    private void initializeSensors() {
        customSensorManager = new CustomSensorManager(this);
        customSensorManager.setSensorDataListener(this);
    }

    private void initializeManagers() {
        beaconLocationManager = new BeaconLocationManager(this, this);
        gpsManager = new GPSManager(this);
        locationDataManager = new LocationDataManager(this);
        Log.d(TAG, "Managers initialized");
    }

    private void initializeBuildingOutlineManager() {
        BuildingOutlineManager buildingOutlineManager = new BuildingOutlineManager(null);
        List<LatLng> buildingCorners = buildingOutlineManager.getBuildingCorners();
        mapView.setBuildingCorners(buildingCorners);
        mapView.setShowBuildingOutline(true);
        Log.d(TAG, "Building outline initialized with " + buildingCorners.size() + " corners");
    }

    private void loadSavedLocationData() {
        LocationDataManager.LocationData savedData = locationDataManager.loadLocationData();
        if (savedData != null) {
            positionTracker.setInitialPosition(savedData.latitude, savedData.longitude);
            positionTracker.setTotalDistance((float) savedData.totalDistance);
            isInitialPositionSet = true;
            trailX.add(savedData.latitude);
            trailY.add(savedData.longitude);
            Log.d(TAG, "Loaded saved location data: (" + savedData.latitude + ", " + savedData.longitude + ")");
        }
    }

    private void toggleTracking() {
        if (isTracking) {
            stopTracking();
        } else {
            startTracking();
        }
    }

    private void startTracking() {
        isTracking = true;
        isDataReliable = false;
        stepCount = 0;
        customSensorManager.registerListeners();
        gpsManager.startLocationUpdates();
        startStopButton.setText("중지");
        statusView.setText("이동 수집 중...");
        statusView.setVisibility(View.VISIBLE);
        Log.d(TAG, "Tracking started");
    }

    private void stopTracking() {
        isTracking = false;
        customSensorManager.unregisterListeners();
        gpsManager.stopLocationUpdates();
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

    @Override
    public void onSensorDataChanged(float[] accelerometerData, float[] magnetometerData, float[] gyroscopeData) {
        if (!isTracking || !isInitialPositionSet) return;

        updateOrientationAngles(accelerometerData, magnetometerData, gyroscopeData);

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastUpdateTime > UPDATE_INTERVAL) {
            updateUI();
            lastUpdateTime = currentTime;
        }
    }

    @Override
    public void onStepDetected() {
        if (!isTracking || !isInitialPositionSet) return;
        updatePosition();
    }

    private void updatePosition() {
        stepCount++;
        if (stepCount >= RELIABLE_STEP_THRESHOLD && !isDataReliable) {
            isDataReliable = true;
            statusView.setVisibility(View.GONE);
        }

        if (!isDataReliable) return;

        long currentTime = System.currentTimeMillis();
        float[] accelerometerReading = customSensorManager.getAccelerometerReading();
        positionTracker.updateStepLength(accelerometerReading);

        positionTracker.updatePosition(filteredOrientation, currentTime - lastStepTime);

        double prevX = positionTracker.getPositionX();
        double prevY = positionTracker.getPositionY();

        trailX.add(prevX);
        trailY.add(prevY);

        addMovementLog(prevX, prevY, positionTracker.getPositionX(), positionTracker.getPositionY(), currentTime);

        lastStepTime = currentTime;
        mapView.updatePosition(positionTracker.getPositionX(), positionTracker.getPositionY());
        mapView.setShowBuildingOutline(true);
        mapView.invalidate();

        if (locationDataManager.shouldSaveData()) {
            locationDataManager.saveLocationData(positionTracker.getPositionX(), positionTracker.getPositionY(), positionTracker.getTotalDistance());
        }
    }

    private void updateOrientationAngles(float[] accelerometerReading, float[] magnetometerReading, float[] gyroscopeReading) {
        CustomSensorManager.getRotationMatrix(rotationMatrix, null, accelerometerReading, magnetometerReading);
        float[] orientation = new float[3];
        CustomSensorManager.getOrientation(rotationMatrix, orientation);

        float gyroRotationZ = gyroscopeReading[2];
        orientation[0] += gyroRotationZ * 0.02f;

        for (int i = 0; i < 3; i++) {
            filteredOrientation[i] = filteredOrientation[i] + ALPHA * (orientation[i] - filteredOrientation[i]);
        }

        mapView.updateOrientation(filteredOrientation);
    }

    private void updateUI() {
        totalDistanceView.setText(String.format("총 이동 거리: %.2f m", positionTracker.getTotalDistance()));
        String direction = PositionCalculator.getCardinalDirection(filteredOrientation[0]);
        logView.setText(String.format("방향: %s (%.0f°)", direction, Math.toDegrees(filteredOrientation[0])));
        destinationManager.updateRemainingSteps(positionTracker.getPositionX(), positionTracker.getPositionY());
        int remainingSteps = destinationManager.getRemainingSteps();
        if (remainingSteps % 10 == 0 || remainingSteps <= 5) {
            audioManager.speak(String.format("목적지까지 %d 걸음 남았습니다.", remainingSteps));
        }
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
    protected void onResume() {
        super.onResume();
        if (isTracking) {
            customSensorManager.registerListeners();
            gpsManager.startLocationUpdates();
        }
        Log.d(TAG, "Activity resumed");
    }

    @Override
    protected void onPause() {
        super.onPause();
        customSensorManager.unregisterListeners();
        gpsManager.stopLocationUpdates();
        beaconLocationManager.stopBeaconScan();
        Log.d(TAG, "Activity paused");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(locationUpdateReceiver);
        beaconLocationManager.stopBeaconScan();
        audioManager.shutdown();
        Log.d(TAG, "Activity destroyed");
    }

    private void startLocationTrackingService() {
        Intent serviceIntent = new Intent(this, LocationTrackingService.class);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    @Override
    public void onLocationEstimated(double x, double y) {
        if (!isInitialPositionSet) {
            positionTracker.setInitialPosition(x, y);
            isInitialPositionSet = true;
            statusView.setText("초기 위치 설정 완료");
            statusView.setVisibility(View.GONE);
            beaconLocationManager.stopBeaconScan();
            resetInitialPositionButton.setEnabled(true);

            runOnUiThread(() -> {
                trailX.clear();
                trailY.clear();
                trailX.add(x);
                trailY.add(y);
                mapView.updatePosition(x, y);
                mapView.setShowBuildingOutline(true);
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
                positionTracker.setInitialPosition(0.0, 0.0);
                isInitialPositionSet = true;

                trailX.clear();
                trailY.clear();
                trailX.add(0.0);
                trailY.add(0.0);

                mapView.invalidate();

                startTracking();
            }
            statusView.setVisibility(View.GONE);
        });
    }

    private BroadcastReceiver locationUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null && intent.hasExtra("latitude") && intent.hasExtra("longitude")) {
                gpsLatitude = intent.getDoubleExtra("latitude", 0.0);
                gpsLongitude = intent.getDoubleExtra("longitude", 0.0);
                handleLocationUpdate(gpsLatitude, gpsLongitude);
            }
        }
    };

    private void handleLocationUpdate(double latitude, double longitude) {
        Log.d(TAG, "Received location update: " + latitude + ", " + longitude);

        if (!isInitialPositionSet) {
            positionTracker.setInitialPosition(latitude, longitude);
            isInitialPositionSet = true;
        }

        mapView.updatePosition(latitude, longitude);
        mapView.invalidate();
    }
}