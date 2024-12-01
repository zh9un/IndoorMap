/*
 * 파일명: CustomNavigationFragment.java
 * 경로: com.example.navermapapi.navigation
 * 작성자: Claude
 * 작성일: 2024-01-01
 */

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
import com.naver.maps.map.util.FusedLocationSource;

import javax.inject.Inject;
import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class CustomNavigationFragment extends Fragment implements OnMapReadyCallback {
    private static final String TAG = "CustomNavigationFragment";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1000;

    // 전시장 중심 좌표
    private static final LatLng EXHIBITION_CENTER = new LatLng(37.558347, 127.048963);

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
        // 위치 업데이트 관찰
        viewModel.getCurrentLocation().observe(getViewLifecycleOwner(), this::handleLocationUpdate);

        // 환경 변화 관찰
        viewModel.getCurrentEnvironment().observe(getViewLifecycleOwner(), this::handleEnvironmentChange);
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
        naverMap.setCameraPosition(new CameraPosition(EXHIBITION_CENTER, 17));
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
        naverMap.setIndoorEnabled(true);

        naverMap.getUiSettings().setZoomControlEnabled(true);
        naverMap.getUiSettings().setLocationButtonEnabled(true);
        naverMap.getUiSettings().setCompassEnabled(true);
    }

    private void setupLocationOverlay(@NonNull NaverMap naverMap) {
        locationOverlay = naverMap.getLocationOverlay();
        locationOverlay.setVisible(true);
    }

    private void setupMapClickListener() {
        naverMap.setOnMapClickListener((point, coord) -> {
            if (isDestinationMode) {
                handleDestinationSelection(coord);
            }
        });
    }

    private void startDestinationMode() {
        isDestinationMode = true;
        String message = getString(R.string.select_destination_prompt);
        voiceGuideManager.announce(message);
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
    }

    private void handleLocationUpdate(LocationData location) {
        if (location == null || locationOverlay == null) return;

        try {
            LatLng position = new LatLng(location.getLatitude(), location.getLongitude());
            locationOverlay.setPosition(position);
            locationOverlay.setBearing(location.getBearing());

            if (viewModel.getDestination().getValue() != null) {
                calculateAndShowPath();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating location", e);
        }
    }

    // FloorPlanManager 메소드 호출 부분 수정
    private void handleEnvironmentChange(EnvironmentType environment) {
        try {
            if (environment == EnvironmentType.INDOOR) {
                locationOverlay.setIcon(OverlayImage.fromResource(R.drawable.ic_indoor_location));
                floorPlanManager.setVisible(true);
            } else {
                locationOverlay.setIcon(OverlayImage.fromResource(R.drawable.ic_outdoor_location));
                floorPlanManager.setVisible(false);
            }
            pathDrawer.setEnvironment(environment);
        } catch (Exception e) {
            Log.e(TAG, "Error handling environment change", e);
        }
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
            binding.navigationInfo.setText(announcement);
        } catch (Exception e) {
            Log.e(TAG, "Error calculating path", e);
            voiceGuideManager.announce(getString(R.string.path_calculation_error));
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
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (locationSource.onRequestPermissionsResult(requestCode, permissions, grantResults)) {
            if (!locationSource.isActivated()) {
                naverMap.setLocationTrackingMode(LocationTrackingMode.None);
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }
}