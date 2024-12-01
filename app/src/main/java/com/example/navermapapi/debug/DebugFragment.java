package com.example.navermapapi.debug;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.example.navermapapi.R;
import com.example.navermapapi.appModule.location.manager.LocationIntegrationManager;
import com.example.navermapapi.coreModule.api.environment.model.EnvironmentType;
import com.example.navermapapi.coreModule.api.location.model.LocationData;
import com.google.android.material.button.MaterialButton;

import java.util.Locale;
import javax.inject.Inject;
import dagger.hilt.android.AndroidEntryPoint;
import static android.content.Context.SENSOR_SERVICE;

@AndroidEntryPoint
public class DebugFragment extends Fragment implements SensorEventListener {
    private static final String TAG = "DebugFragment";
    private static final int UPDATE_INTERVAL_MS = 500; // 0.5초마다 업데이트

    // UI 컴포넌트
    private Switch environmentSwitch;
    private TextView currentStatusText;
    private TextView pdrStatusText;
    private TextView beaconStatusText;
    private TextView sensorStatusText;
    private TextView gpsStatusText;
    private Button resetPdrButton;
    private Button copyStatusButton;

    // 센서 관련
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private Sensor gyroscope;
    private final float[] accelerometerValues = new float[3];
    private final float[] gyroscopeValues = new float[3];

    // 업데이트 핸들러
    private final Handler updateHandler = new Handler(Looper.getMainLooper());
    private final Runnable updateRunnable = new Runnable() {
        @Override
        public void run() {
            updateAllStatus();
            updateHandler.postDelayed(this, UPDATE_INTERVAL_MS);
        }
    };

    @Inject
    LocationIntegrationManager locationManager;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate called");
        initializeSensors();
    }

    private void initializeSensors() {
        if (getActivity() == null) return;

        sensorManager = (SensorManager) getActivity().getSystemService(SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Log.d(TAG, "onCreateView called");
        View view = inflater.inflate(R.layout.fragment_debug, container, false);
        initializeViews(view);
        setupUI();
        return view;
    }

    private void initializeViews(View view) {
        environmentSwitch = view.findViewById(R.id.environment_switch);
        currentStatusText = view.findViewById(R.id.current_status_text);
        pdrStatusText = view.findViewById(R.id.pdr_status_text);
        beaconStatusText = view.findViewById(R.id.beacon_status_text);
        sensorStatusText = view.findViewById(R.id.sensor_status_text);
        gpsStatusText = view.findViewById(R.id.gps_status_text);
        resetPdrButton = view.findViewById(R.id.reset_pdr_button);
        copyStatusButton = view.findViewById(R.id.copy_status_button);

        // 각 뷰가 null인지 확인하는 로그 추가
        Log.d(TAG, "initializeViews: environmentSwitch=" + (environmentSwitch != null));
        Log.d(TAG, "initializeViews: currentStatusText=" + (currentStatusText != null));
        Log.d(TAG, "initializeViews: pdrStatusText=" + (pdrStatusText != null));
        Log.d(TAG, "initializeViews: beaconStatusText=" + (beaconStatusText != null));
        Log.d(TAG, "initializeViews: sensorStatusText=" + (sensorStatusText != null));
        Log.d(TAG, "initializeViews: gpsStatusText=" + (gpsStatusText != null));
        Log.d(TAG, "initializeViews: resetPdrButton=" + (resetPdrButton != null));
        Log.d(TAG, "initializeViews: copyStatusButton=" + (copyStatusButton != null));
    }


    private void setupUI() {
        // 환경 전환 스위치 초기 상태 설정
        EnvironmentType currentEnvironment = locationManager.getCurrentEnvironment().getValue();
        if (currentEnvironment != null) {
            environmentSwitch.setChecked(currentEnvironment == EnvironmentType.INDOOR);
        }

        // 환경 전환 리스너
        environmentSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            try {
                EnvironmentType newEnvironment = isChecked ? EnvironmentType.INDOOR : EnvironmentType.OUTDOOR;
                locationManager.forceEnvironment(newEnvironment);
                showToast(String.format("환경을 %s로 전환했습니다.", isChecked ? "실내" : "실외"));
            } catch (Exception e) {
                Log.e(TAG, "Error switching environment", e);
                showToast("환경 전환 중 오류가 발생했습니다.");
            }
        });

        // PDR 리셋 버튼
        resetPdrButton.setOnClickListener(v -> {
            try {
                locationManager.resetPdrSystem();
                showToast("PDR 시스템을 리셋했습니다.");
            } catch (Exception e) {
                Log.e(TAG, "Error resetting PDR", e);
                showToast("PDR 리셋 중 오류가 발생했습니다.");
            }
        });

        // 상태 복사 버튼
        copyStatusButton.setOnClickListener(v -> {
            copyStatusToClipboard();
        });

        // 초기 상태 업데이트
        updateAllStatus();
    }

    private void updateAllStatus() {
        Log.d(TAG, "Updating all status...");
        updateLocationStatus();
        updatePdrStatus();
        updateBeaconStatus();
        updateSensorStatus();
        sensorStatusText.setText(String.format("Accelerometer: [%.2f, %.2f, %.2f]\nGyroscope: [%.2f, %.2f, %.2f]",
                accelerometerValues[0], accelerometerValues[1], accelerometerValues[2],
                gyroscopeValues[0], gyroscopeValues[1], gyroscopeValues[2]));
        updateGpsStatus();
        Log.d(TAG, "Status update completed.");
    }

    private void updateLocationStatus() {
        LocationData location = locationManager.getCurrentLocation().getValue();
        if (location != null) {
            String status = String.format(Locale.getDefault(),
                    "현재 위치:\n" +
                            "위도: %.6f\n" +
                            "경도: %.6f\n" +
                            "정확도: %.2fm\n" +
                            "환경: %s\n" +
                            "제공자: %s",
                    location.getLatitude(),
                    location.getLongitude(),
                    location.getAccuracy(),
                    location.getEnvironment(),
                    location.getProvider());
            currentStatusText.setText(status);
        } else {
            currentStatusText.setText("위치 정보 없음");
        }
    }

    private void updatePdrStatus() {
        if (locationManager.isPdrOperating()) {
            String pdrStatus = String.format(Locale.getDefault(),
                    "PDR 상태:\n" +
                            "걸음 수: %d\n" +
                            "이동 거리: %.2fm\n" +
                            "방향: %.1f°",
                    locationManager.getStepCount(),
                    locationManager.getDistanceTraveled(),
                    locationManager.getCurrentHeading());
            pdrStatusText.setText(pdrStatus);
            resetPdrButton.setEnabled(true);
        } else {
            pdrStatusText.setText("PDR 비활성");
            resetPdrButton.setEnabled(false);
        }
    }

    private void updateBeaconStatus() {
        String beaconStatus = String.format(Locale.getDefault(),
                "비콘 상태:\n" +
                        "스캔 중: %s\n" +
                        "감지된 비콘: %d개",
                locationManager.isPdrOperating() ? "예" : "아니오",
                locationManager.getDetectedBeaconCount());
        beaconStatusText.setText(beaconStatus);
    }

    private void updateSensorStatus() {
        String sensorStatus = String.format(Locale.getDefault(),
                "센서 데이터:\n" +
                        "가속도(m/s²):\n" +
                        "  X=%.2f, Y=%.2f, Z=%.2f\n" +
                        "자이로(rad/s):\n" +
                        "  X=%.2f, Y=%.2f, Z=%.2f",
                accelerometerValues[0], accelerometerValues[1], accelerometerValues[2],
                gyroscopeValues[0], gyroscopeValues[1], gyroscopeValues[2]);
        sensorStatusText.setText(sensorStatus);
    }

    private void updateGpsStatus() {
        String gpsStatus = String.format(Locale.getDefault(),
                "GPS 상태:\n" +
                        "신호 강도: %.1f dBm\n" +
                        "위성 수: %d개\n" +
                        "사용 가능: %s",
                locationManager.getCurrentSignalStrength(),
                locationManager.getVisibleSatellites(),
                locationManager.isGpsAvailable() ? "예" : "아니오");
        gpsStatusText.setText(gpsStatus);
    }

    private void copyStatusToClipboard() {
        if (getActivity() == null) return;

        StringBuilder status = new StringBuilder();
        status.append("=== 디버그 정보 ===\n\n");
        status.append(currentStatusText.getText()).append("\n\n");
        status.append(pdrStatusText.getText()).append("\n\n");
        status.append(beaconStatusText.getText()).append("\n\n");
        status.append(sensorStatusText.getText()).append("\n\n");
        status.append(gpsStatusText.getText());

        android.content.ClipboardManager clipboard =
                (android.content.ClipboardManager) requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
        android.content.ClipData clip =
                android.content.ClipData.newPlainText("Debug Status", status.toString());
        clipboard.setPrimaryClip(clip);

        showToast("디버그 정보가 클립보드에 복사되었습니다.");
    }

    private void showToast(String message) {
        if (getActivity() != null) {
            Toast.makeText(getActivity(), message, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        switch (event.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:
                System.arraycopy(event.values, 0, accelerometerValues, 0, 3);
                break;
            case Sensor.TYPE_GYROSCOPE:
                System.arraycopy(event.values, 0, gyroscopeValues, 0, 3);
                break;
        }
        Log.d(TAG, String.format("Sensor data updated: Accel=[%.2f, %.2f, %.2f], Gyro=[%.2f, %.2f, %.2f]",
                accelerometerValues[0], accelerometerValues[1], accelerometerValues[2],
                gyroscopeValues[0], gyroscopeValues[1], gyroscopeValues[2]));
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // 정확도 변경 처리 (필요한 경우 구현)
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume called");
        // 센서 리스너 등록
        if (sensorManager != null) {
            if (accelerometer != null) {
                sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
            }
            if (gyroscope != null) {
                sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_NORMAL);
            }
        }
        updateHandler.post(updateRunnable);
    }

    @Override
    public void onPause() {
        super.onPause();
        // 센서 리스너 해제
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
        updateHandler.removeCallbacks(updateRunnable);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
        updateHandler.removeCallbacksAndMessages(null);
    }
}