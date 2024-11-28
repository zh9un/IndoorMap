package com.example.navermapapi.debug;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Switch;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.example.navermapapi.R;
import com.example.navermapapi.appModule.location.manager.LocationIntegrationManager;
import com.example.navermapapi.coreModule.api.environment.model.EnvironmentType;
import com.example.navermapapi.coreModule.api.location.model.LocationData;

import dagger.hilt.android.AndroidEntryPoint;
import javax.inject.Inject;

@AndroidEntryPoint
public class DebugFragment extends Fragment {

    private Switch environmentSwitch;
    private TextView currentStatusText;
    private TextView pdrStatusText;
    private TextView beaconStatusText;

    // LocationIntegrationManager 인스턴스 주입
    @Inject
    LocationIntegrationManager locationManager;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_debug, container, false);

        // UI 요소 초기화
        environmentSwitch = view.findViewById(R.id.environment_switch);
        currentStatusText = view.findViewById(R.id.current_status_text);
        pdrStatusText = view.findViewById(R.id.pdr_status_text);
        beaconStatusText = view.findViewById(R.id.beacon_status_text);

        setupUI();

        return view;
    }

    private void setupUI() {
        // 환경 스위치 초기 설정
        EnvironmentType currentEnvironment = locationManager.getCurrentEnvironment().getValue();
        if (currentEnvironment != null) {
            environmentSwitch.setChecked(currentEnvironment == EnvironmentType.INDOOR);
        }

        // 환경 스위치 리스너 설정
        environmentSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            EnvironmentType newEnvironment = isChecked ? EnvironmentType.INDOOR : EnvironmentType.OUTDOOR;
            // 환경 타입을 수동으로 설정
            locationManager.forceEnvironment(newEnvironment);
        });

        // LiveData 관찰 설정
        locationManager.getCurrentLocation().observe(getViewLifecycleOwner(), locationData -> {
            updateStatus();
        });

        locationManager.getCurrentEnvironment().observe(getViewLifecycleOwner(), environmentType -> {
            // 환경 스위치 상태 업데이트
            environmentSwitch.setOnCheckedChangeListener(null);
            environmentSwitch.setChecked(environmentType == EnvironmentType.INDOOR);
            environmentSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                EnvironmentType newEnvironment = isChecked ? EnvironmentType.INDOOR : EnvironmentType.OUTDOOR;
                locationManager.forceEnvironment(newEnvironment);
            });
            updateStatus();
        });

        updateStatus();
    }

    private void updateStatus() {
        // 현재 위치 정보 업데이트
        LocationData currentLocation = locationManager.getCurrentLocation().getValue();
        if (currentLocation != null) {
            String status = String.format("위도: %.6f, 경도: %.6f, 제공자: %s",
                    currentLocation.getLatitude(),
                    currentLocation.getLongitude(),
                    currentLocation.getProvider());
            currentStatusText.setText(status);
        } else {
            currentStatusText.setText("위치 데이터 없음");
        }

        // PDR 상태 업데이트
        boolean isPdrOperating = locationManager.isPdrOperating();
        pdrStatusText.setText(isPdrOperating ? "PDR 작동 중" : "PDR 정지");

        // 비콘 스캔 상태 업데이트
        boolean isBeaconScanning = locationManager.isBeaconScanning();
        beaconStatusText.setText(isBeaconScanning ? "비콘 스캔 중" : "비콘 스캔 정지");
    }
}
