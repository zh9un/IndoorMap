package com.example.navermapapi.appModule.main;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.cardview.widget.CardView;

import com.example.navermapapi.R;
import com.example.navermapapi.beaconModule.api.BeaconModuleApi;
import com.example.navermapapi.beaconModule.location.BeaconLocationProvider;
import com.example.navermapapi.beaconModule.location.BeaconLocationProvider.LocationData;
import com.example.navermapapi.beaconModule.location.BeaconLocationProvider.MovementState;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int PERMISSION_REQUEST_CODE = 1234;

    private BeaconModuleApi beaconModule;
    private TextView locationText;
    private TextView stateText;
    private TextView sensorStatus;
    private Button startTrackingButton;
    private Button stopTrackingButton;

    // 필요한 권한 배열
    private final String[] REQUIRED_PERMISSIONS = {
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACTIVITY_RECOGNITION,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // UI 초기화
        initializeUI();

        // 권한 체크 및 요청
        if (checkPermissions()) {
            initializeBeaconModule();
        } else {
            requestPermissions();
        }
    }

    private void initializeUI() {
        locationText = findViewById(R.id.location_text);
        stateText = findViewById(R.id.state_text);
        sensorStatus = findViewById(R.id.sensor_status);
        startTrackingButton = findViewById(R.id.start_tracking);
        stopTrackingButton = findViewById(R.id.stop_tracking);

        startTrackingButton.setOnClickListener(v -> startTracking());
        stopTrackingButton.setOnClickListener(v -> stopTracking());
    }

    private void initializeBeaconModule() {
        try {
            // BeaconModule 초기화
            beaconModule = BeaconModuleApi.getInstance(this);
            beaconModule.initialize();

            // 초기 층 설정 (예: 1층)
            beaconModule.setInitialFloor(1);

            // 위치 업데이트 콜백 설정
            beaconModule.setLocationCallback(new BeaconLocationProvider.LocationCallback() {
                @Override
                public void onLocationChanged(LocationData location) {
                    updateLocationDisplay(location);
                }
            });

            // 위치 추적 시작
            beaconModule.start();

            Log.i(TAG, "Beacon module initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize beacon module", e);
        }
    }

    private void startTracking() {
        if (beaconModule != null) {
            beaconModule.start();
            startTrackingButton.setEnabled(false);
            stopTrackingButton.setEnabled(true);
            updateSensorStatus("추적 활성화");
        }
    }

    private void stopTracking() {
        if (beaconModule != null) {
            beaconModule.stop();
            startTrackingButton.setEnabled(true);
            stopTrackingButton.setEnabled(false);
            updateSensorStatus("추적 비활성화");
        }
    }

    private void updateLocationDisplay(LocationData location) {
        runOnUiThread(() -> {
            // 위치 정보 표시
            String locationInfo = String.format(
                    "위치: (%.2f, %.2f)\n층: %d층\n방향: %.1f°\n정확도: %.2fm",
                    location.getX(),
                    location.getY(),
                    location.getFloor(),
                    location.getHeading(),
                    location.getAccuracy()
            );
            locationText.setText(locationInfo);

            // 이동 상태 표시
            String stateInfo = getMovementStateText(location.getMovementState());
            stateText.setText("상태: " + stateInfo);
        });
    }

    private String getMovementStateText(MovementState state) {
        switch (state) {
            case WALKING:
                return "보행 중";
            case STAIRS:
                return "계단 이동 중";
            case ELEVATOR:
                return "엘리베이터 이동 중";
            case STATIONARY:
                return "정지";
            default:
                return "알 수 없음";
        }
    }

    private void updateSensorStatus(String status) {
        runOnUiThread(() -> {
            String timeStamp = new SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                    .format(new Date());
            sensorStatus.setText(String.format("상태: %s\n마지막 업데이트: %s", status, timeStamp));
        });
    }

    private boolean checkPermissions() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                initializeBeaconModule();
            } else {
                Log.e(TAG, "Required permissions not granted");
                // 사용자에게 권한이 필요하다는 메시지 표시
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (beaconModule != null && beaconModule.isInitialized()) {
            beaconModule.start();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (beaconModule != null && beaconModule.isInitialized()) {
            beaconModule.stop();
        }
    }
}
