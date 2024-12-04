package com.example.navermapapi.navigation;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.navermapapi.R;
import com.example.navermapapi.appModule.accessibility.VoiceGuideManager;
import com.example.navermapapi.appModule.main.MainViewModel;
import com.example.navermapapi.coreModule.api.environment.model.EnvironmentType;
import com.example.navermapapi.coreModule.api.location.model.LocationData;
import com.example.navermapapi.databinding.FragmentCustomNavigationBinding;
import com.example.navermapapi.path.calculator.PathCalculator;
import com.example.navermapapi.path.drawer.PathDrawer;
import com.example.navermapapi.path.manager.PathDataManager;
import com.example.navermapapi.utils.FloorPlanConfig;
import com.example.navermapapi.utils.FloorPlanManager;
import com.naver.maps.geometry.LatLng;
import com.naver.maps.map.LocationTrackingMode;
import com.naver.maps.map.MapFragment;
import com.naver.maps.map.NaverMap;
import com.naver.maps.map.OnMapReadyCallback;
import com.naver.maps.map.overlay.LocationOverlay;
import com.naver.maps.map.overlay.Marker;
import com.naver.maps.map.overlay.OverlayImage;
import com.naver.maps.map.CameraPosition;
import com.naver.maps.map.CameraUpdate;
import com.naver.maps.map.util.FusedLocationSource;

import java.util.Locale;

import javax.inject.Inject;
import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class CustomNavigationFragment extends Fragment implements OnMapReadyCallback {
    private static final String TAG = "CustomNavigationFragment";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1000;

    private FragmentCustomNavigationBinding binding;
    private MainViewModel viewModel;
    private NaverMap naverMap;
    private LocationOverlay locationOverlay;
    private FusedLocationSource locationSource;
    private PathDrawer pathDrawer;
    private Marker destinationMarker;
    private boolean isDestinationMode = false;
    private FloorPlanManager floorPlanManager;

    @Inject
    VoiceGuideManager voiceGuideManager;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
        locationSource = new FusedLocationSource(this, LOCATION_PERMISSION_REQUEST_CODE);
        destinationMarker = new Marker();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentCustomNavigationBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initializeMap();
        setupUI();
        setupObservers();
    }

    private void setupObservers() {
        viewModel.getCurrentLocation().observe(getViewLifecycleOwner(), this::handleLocationUpdate);
        viewModel.getCurrentEnvironment().observe(getViewLifecycleOwner(), environment -> {
            updateNavigationInfo();  // 환경 변화 시에도 navigation_info 업데이트
        });
        viewModel.getDestination().observe(getViewLifecycleOwner(), this::updateDestinationUI);
    }

    private void initializeMap() {
        MapFragment mapFragment = (MapFragment) getChildFragmentManager()
                .findFragmentById(R.id.custom_navigation_map);
        if (mapFragment == null) {
            mapFragment = MapFragment.newInstance();
            getChildFragmentManager().beginTransaction()
                    .add(R.id.custom_navigation_map, mapFragment)
                    .commit();
        }
        mapFragment.getMapAsync(this);
    }

    private void setupUI() {
        binding.setDestinationButton.setOnClickListener(v -> startDestinationMode());
        binding.startNavigationButton.setOnClickListener(v -> calculateAndShowPath());
    }

    @Override
    public void onMapReady(@NonNull NaverMap naverMap) {
        this.naverMap = naverMap;
        this.pathDrawer = new PathDrawer(naverMap);

        setupMapSettings(naverMap);
        setupLocationOverlay(naverMap);
        setupMapClickListener();
        initializeFloorPlan();

        // 경로 영역 표시
        pathDrawer.showBoundary(true);

        // 초기 카메라 위치 설정
        naverMap.setCameraPosition(new CameraPosition(FloorPlanConfig.CENTER, 17));
    }

    private void initializeFloorPlan() {
        try {
            floorPlanManager = FloorPlanManager.getInstance(requireContext());
            floorPlanManager.initialize(
                    FloorPlanConfig.RESOURCE_ID,
                    FloorPlanConfig.CENTER,
                    FloorPlanConfig.OVERLAY_WIDTH_METERS,
                    FloorPlanConfig.ROTATION,
                    0.7f
            );
            floorPlanManager.setMap(naverMap);
        } catch (Exception e) {
            Log.e(TAG, "Error initializing floor plan", e);
            Toast.makeText(requireContext(), "도면 초기화 오류", Toast.LENGTH_SHORT).show();
        }
    }

    private void setupMapSettings(@NonNull NaverMap naverMap) {
        naverMap.setLocationSource(locationSource);
        naverMap.setLocationTrackingMode(LocationTrackingMode.Follow);

        naverMap.getUiSettings().setZoomControlEnabled(true);
        naverMap.getUiSettings().setLocationButtonEnabled(true);
        naverMap.getUiSettings().setCompassEnabled(true);
    }

    private void setupLocationOverlay(@NonNull NaverMap naverMap) {
        locationOverlay = naverMap.getLocationOverlay();
        locationOverlay.setVisible(true);
        locationOverlay.setCircleRadius(0);  // 정확도 원 숨김

        updateEnvironmentIcon(viewModel.getCurrentEnvironment().getValue());
    }

    private void updateEnvironmentIcon(EnvironmentType environment) {
        if (locationOverlay == null) return;

        int iconResource = environment == EnvironmentType.INDOOR ?
                R.drawable.ic_indoor_location : R.drawable.ic_outdoor_location;
        locationOverlay.setIcon(OverlayImage.fromResource(iconResource));
    }

    private void setupMapClickListener() {
        naverMap.setOnMapClickListener((point, coord) -> {
            if (isDestinationMode) {
                handleDestinationSelection(coord);
            }
        });
    }

    private void handleLocationUpdate(LocationData location) {
        if (location == null || locationOverlay == null) return;

        try {
            LatLng position = new LatLng(location.getLatitude(), location.getLongitude());
            locationOverlay.setPosition(position);
            locationOverlay.setBearing(location.getBearing());

            updateNavigationInfo();

            if (viewModel.isAutoTrackingEnabled()) {
                naverMap.moveCamera(CameraUpdate.scrollTo(position));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating location UI", e);
        }
    }

    private void updateNavigationInfo() {
        if (binding == null || binding.navigationInfo == null) return;

        StringBuilder info = new StringBuilder();

        // 현재 위치
        LocationData location = viewModel.getCurrentLocation().getValue();
        if (location != null) {
            info.append(String.format(Locale.getDefault(),
                    "현재 위치: %.6f, %.6f\n",
                    location.getLatitude(),
                    location.getLongitude()));
        }

        binding.navigationInfo.setText(info.toString());
        Log.d(TAG, "Navigation info updated: " + info.toString());
    }

    private String getEnvironmentText(EnvironmentType environment) {
        switch (environment) {
            case INDOOR:
                return "실내 모드";
            case OUTDOOR:
                return "실외 모드";
            case TRANSITION:
                return "전환 중";
            default:
                return "알 수 없음";
        }
    }

    private void startDestinationMode() {
        isDestinationMode = true;
        String message = getString(R.string.select_destination_prompt);
        voiceGuideManager.announce(message);
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
    }

    private void handleDestinationSelection(LatLng selectedPoint) {
        if (!PathDataManager.isPointInBoundary(selectedPoint)) {
            String message = getString(R.string.out_of_bounds_message);
            voiceGuideManager.announce(message);
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            return;
        }

        LatLng destinationOnPath = PathCalculator.findNearestPointOnPath(selectedPoint);
        destinationMarker.setPosition(destinationOnPath);
        destinationMarker.setMap(naverMap);

        isDestinationMode = false;
        viewModel.setDestination(destinationOnPath);
        calculateAndShowPath();
    }

    private void calculateAndShowPath() {
        if (locationOverlay == null || !locationOverlay.isVisible()) {
            voiceGuideManager.announce(getString(R.string.location_unavailable));
            return;
        }

        LatLng currentLocation = locationOverlay.getPosition();
        LatLng destination = viewModel.getDestination().getValue();

        if (destination == null) {
            voiceGuideManager.announce(getString(R.string.no_destination_set));
            return;
        }

        try {
            LatLng startOnPath = PathCalculator.findNearestPointOnPath(currentLocation);
            pathDrawer.drawPath(startOnPath, destination);

            double distance = PathCalculator.calculatePathDistance(startOnPath, destination);
            String announcement = getString(R.string.distance_announcement, (int) distance);
            voiceGuideManager.announce(announcement);

            updateDistanceTimeInfo(distance);
        } catch (Exception e) {
            Log.e(TAG, "Error calculating path", e);
            voiceGuideManager.announce(getString(R.string.path_calculation_error));
        }
    }

    private void updateDistanceTimeInfo(double distance) {
        // 평균 보행 속도를 기준으로 예상 시간 계산 (4km/h = 1.11m/s)
        int estimatedSeconds = (int)(distance / 1.11);
        String timeString = String.format("%d분 %d초", estimatedSeconds / 60, estimatedSeconds % 60);

//        String info = getString(R.string.distance_time_info_format,
//                (int)distance, timeString);
//        binding.distanceTimeInfo.setText(info);
    }

    private void updateDestinationUI(LatLng destination) {
        clearDestinationMarker();

        if (destination != null) {
            destinationMarker.setPosition(destination);
            destinationMarker.setMap(naverMap);
            calculateAndShowPath();
        }
    }

    private void clearDestinationMarker() {
        destinationMarker.setMap(null);
        if (pathDrawer != null) {
            pathDrawer.clearPath();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        cleanup();
    }

    private void cleanup() {
        if (binding != null) {
            binding = null;
        }
        if (pathDrawer != null) {
            pathDrawer.cleanup();
        }
        if (destinationMarker != null) {
            destinationMarker.setMap(null);
        }
        if (floorPlanManager != null) {
            floorPlanManager.cleanup();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (locationSource.onRequestPermissionsResult(requestCode, permissions, grantResults)) {
            if (!locationSource.isActivated()) {
                naverMap.setLocationTrackingMode(LocationTrackingMode.None);
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }
}