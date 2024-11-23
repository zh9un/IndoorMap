package com.example.navermapapi.appModule.indoor;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.navermapapi.R;
import com.example.navermapapi.appModule.main.MainViewModel;
import com.example.navermapapi.appModule.outdoor.OutdoorMapFragment;
import com.example.navermapapi.coreModule.api.environment.model.EnvironmentType;
import com.example.navermapapi.coreModule.api.location.model.LocationData;
import com.example.navermapapi.databinding.FragmentIndoorMapBinding;
import com.naver.maps.geometry.LatLng;
import com.naver.maps.map.CameraPosition;
import com.naver.maps.map.CameraUpdate;
import com.naver.maps.map.LocationTrackingMode;
import com.naver.maps.map.MapView;
import com.naver.maps.map.NaverMap;
import com.naver.maps.map.OnMapReadyCallback;
import com.naver.maps.map.overlay.LocationOverlay;
import com.naver.maps.map.util.FusedLocationSource;
import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class IndoorMapFragment extends Fragment implements OnMapReadyCallback {
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1000;

    private FragmentIndoorMapBinding binding;
    private MainViewModel viewModel;
    private NaverMap naverMap;
    private LocationOverlay locationOverlay;
    private FusedLocationSource locationSource;
    private int stepCount = 0;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
        locationSource = new FusedLocationSource(this, LOCATION_PERMISSION_REQUEST_CODE);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentIndoorMapBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding.indoorMapView.onCreate(savedInstanceState);
        binding.indoorMapView.getMapAsync(this);

        setupUI();
        setupObservers();
    }

    private void setupUI() {
        binding.indoorVoiceGuideButton.setOnClickListener(v -> {
            LocationData currentLocation = viewModel.getCurrentLocation().getValue();
            if (currentLocation != null) {
                announceCurrentStatus();
            }
        });
    }

    private void setupObservers() {
        viewModel.getCurrentLocation().observe(getViewLifecycleOwner(), this::updateLocationUI);
        viewModel.getCurrentEnvironment().observe(getViewLifecycleOwner(), this::handleEnvironmentChange);
    }

    @Override
    public void onMapReady(@NonNull NaverMap naverMap) {
        this.naverMap = naverMap;

        setupMapSettings(naverMap);
        setupLocationOverlay(naverMap);

        // 마지막 위치로 카메라 이동
        LocationData lastLocation = viewModel.getCurrentLocation().getValue();
        if (lastLocation != null) {
            updateLocationUI(lastLocation);
        }
    }

    private void setupMapSettings(@NonNull NaverMap naverMap) {
        naverMap.setLocationSource(locationSource);
        naverMap.setLocationTrackingMode(LocationTrackingMode.Follow);
        naverMap.setIndoorEnabled(true);

        naverMap.setMinZoom(17.0);
        naverMap.setMaxZoom(20.0);

        // 실내 UI 설정
        naverMap.getUiSettings().setIndoorLevelPickerEnabled(true);
        naverMap.getUiSettings().setZoomControlEnabled(true);
    }

    private void setupLocationOverlay(@NonNull NaverMap naverMap) {
        locationOverlay = naverMap.getLocationOverlay();
        locationOverlay.setVisible(true);
    }

    private void updateLocationUI(LocationData location) {
        if (location == null || naverMap == null || locationOverlay == null) return;

        LatLng position = new LatLng(location.getLatitude(), location.getLongitude());
        locationOverlay.setPosition(position);
        locationOverlay.setBearing(location.getBearing());

        if (viewModel.isAutoTrackingEnabled()) {
            naverMap.moveCamera(CameraUpdate.scrollTo(position));
        }

        updateStepCount();
        updateDirectionInfo(location.getBearing());
    }

    private void handleEnvironmentChange(EnvironmentType environment) {
        if (environment == EnvironmentType.OUTDOOR) {
            // OutdoorMapFragment로 전환
            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.nav_host_fragment, new OutdoorMapFragment())
                    .commit();
        }
    }

    private void updateStepCount() {
        stepCount++;
        binding.indoorStepCount.setText(String.format("걸음 수: %d 걸음", stepCount));
    }

    private void updateDirectionInfo(float bearing) {
        String direction = getDirectionText(bearing);
        binding.indoorDirection.setText(String.format("방향: %s", direction));
    }

    private String getDirectionText(float degrees) {
        if (degrees >= 337.5 || degrees < 22.5) return "북쪽";
        if (degrees >= 22.5 && degrees < 67.5) return "북동쪽";
        if (degrees >= 67.5 && degrees < 112.5) return "동쪽";
        if (degrees >= 112.5 && degrees < 157.5) return "남동쪽";
        if (degrees >= 157.5 && degrees < 202.5) return "남쪽";
        if (degrees >= 202.5 && degrees < 247.5) return "남서쪽";
        if (degrees >= 247.5 && degrees < 292.5) return "서쪽";
        return "북서쪽";
    }

    private void announceCurrentStatus() {
        LocationData location = viewModel.getCurrentLocation().getValue();
        if (location != null) {
            String direction = getDirectionText(location.getBearing());
            String announcement = String.format(
                    "현재 %s 방향으로 이동 중이며, 총 %d 걸음 이동했습니다.",
                    direction, stepCount
            );
            // VoiceGuide를 통해 안내 (구현 필요)
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        binding.indoorMapView.onStart();
    }

    @Override
    public void onResume() {
        super.onResume();
        binding.indoorMapView.onResume();
    }

    @Override
    public void onPause() {
        binding.indoorMapView.onPause();
        super.onPause();
    }

    @Override
    public void onStop() {
        binding.indoorMapView.onStop();
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        binding.indoorMapView.onDestroy();
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        binding.indoorMapView.onSaveInstanceState(outState);
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        binding.indoorMapView.onLowMemory();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (locationSource.onRequestPermissionsResult(
                requestCode, permissions, grantResults)) {
            if (!locationSource.isActivated()) {
                naverMap.setLocationTrackingMode(LocationTrackingMode.None);
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }
}