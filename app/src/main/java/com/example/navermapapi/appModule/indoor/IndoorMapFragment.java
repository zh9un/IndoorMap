package com.example.navermapapi.appModule.indoor;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import com.example.navermapapi.R;
import com.example.navermapapi.appModule.accessibility.VoiceGuideManager;
import com.example.navermapapi.appModule.main.MainViewModel;
import com.example.navermapapi.coreModule.api.environment.model.EnvironmentType;
import com.example.navermapapi.coreModule.api.location.model.LocationData;
import com.example.navermapapi.databinding.FragmentIndoorMapBinding;
import com.example.navermapapi.utils.FloorPlanConfig;
import com.example.navermapapi.utils.FloorPlanManager;
import com.naver.maps.geometry.LatLng;
import com.naver.maps.geometry.LatLngBounds;
import com.naver.maps.map.CameraPosition;
import com.naver.maps.map.LocationTrackingMode;
import com.naver.maps.map.MapFragment;
import com.naver.maps.map.NaverMap;
import com.naver.maps.map.OnMapReadyCallback;
import com.naver.maps.map.overlay.LocationOverlay;
import com.naver.maps.map.util.FusedLocationSource;

import javax.inject.Inject;
import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class IndoorMapFragment extends Fragment implements OnMapReadyCallback {
    private static final String TAG = "IndoorMapFragment";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1000;

    private FragmentIndoorMapBinding binding;
    private MainViewModel viewModel;
    private NaverMap naverMap;
    private LocationOverlay locationOverlay;
    private FusedLocationSource locationSource;
    private FloorPlanManager floorPlanManager;
    private int stepCount = 0;

    @Inject
    VoiceGuideManager voiceGuideManager;

    // 상태 저장을 위한 키
    private static final String KEY_STEP_COUNT = "step_count";
    private static final String KEY_OVERLAY_OPACITY = "overlay_opacity";
    private static final String KEY_OVERLAY_ROTATION = "overlay_rotation";

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
        locationSource = new FusedLocationSource(this, LOCATION_PERMISSION_REQUEST_CODE);

        // FloorPlanManager 인스턴스 가져오기
        floorPlanManager = FloorPlanManager.getInstance(requireContext());

        if (savedInstanceState != null) {
            restoreState(savedInstanceState);
        }
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
    public void onViewCreated(@NonNull View view,
                              @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initializeMap();
        setupUI();
        setupObservers();
    }

    private void initializeMap() {
        MapFragment mapFragment = (MapFragment) getChildFragmentManager()
                .findFragmentById(R.id.indoor_map_view);
        if (mapFragment == null) {
            mapFragment = MapFragment.newInstance();
            getChildFragmentManager().beginTransaction()
                    .add(R.id.indoor_map_view, mapFragment)
                    .commit();
        }
        mapFragment.getMapAsync(this);
    }

    private void setupUI() {
        setupOverlayControls();
        setupVoiceGuideButton();
        setupAccessibility();
    }

    private void setupOverlayControls() {
        binding.overlayOpacitySeekBar.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar,
                                                  int progress, boolean fromUser) {
                        float opacity = progress / 100.0f;
                        floorPlanManager.setOpacity(opacity);
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) { }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) { }
                });

        binding.overlayRotationSeekBar.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar,
                                                  int progress, boolean fromUser) {
                        float rotation = progress * 3.6f; // 0-100을 0-360도로 변환
                        floorPlanManager.setRotation(rotation);
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) { }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) { }
                });
    }

    private void setupVoiceGuideButton() {
        binding.indoorVoiceGuideButton.setOnClickListener(v -> {
            LocationData currentLocation = viewModel.getCurrentLocation().getValue();
            if (currentLocation != null) {
                announceCurrentStatus();
            }
        });
    }

    private void setupAccessibility() {
        binding.overlayOpacitySeekBar.setContentDescription(
                getString(R.string.overlay_opacity_description));
        binding.overlayRotationSeekBar.setContentDescription(
                getString(R.string.overlay_rotation_description));
    }

    private void setupObservers() {
        viewModel.getCurrentLocation().observe(
                getViewLifecycleOwner(), this::updateLocationUI);
        viewModel.getCurrentEnvironment().observe(
                getViewLifecycleOwner(), this::handleEnvironmentChange);
    }

    @Override
    public void onMapReady(@NonNull NaverMap naverMap) {
        this.naverMap = naverMap;
        setupMapSettings(naverMap);
        setupLocationOverlay(naverMap);

        // 도면 설정
        setupFloorPlan();

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

        naverMap.getUiSettings().setIndoorLevelPickerEnabled(true);
        naverMap.getUiSettings().setZoomControlEnabled(true);
        naverMap.getUiSettings().setCompassEnabled(true);
    }

    private void setupLocationOverlay(@NonNull NaverMap naverMap) {
        locationOverlay = naverMap.getLocationOverlay();
        locationOverlay.setVisible(true);
    }

    private void setupFloorPlan() {
        try {
            // FloorPlanManager 초기화
            floorPlanManager.initialize(
                    FloorPlanConfig.RESOURCE_ID,
                    FloorPlanConfig.CENTER,
                    FloorPlanConfig.OVERLAY_WIDTH_METERS,
                    FloorPlanConfig.ROTATION,
                    FloorPlanConfig.OPACITY
            );

            floorPlanManager.setMap(naverMap);

        } catch (Exception e) {
            Log.e(TAG, "Error setting up floor plan", e);
            handleOverlayError(e);
        }
    }



    private void updateLocationUI(LocationData location) {
        if (location == null || naverMap == null || locationOverlay == null)
            return;

        try {
            LatLng position = new LatLng(location.getLatitude(),
                    location.getLongitude());
            locationOverlay.setPosition(position);
            locationOverlay.setBearing(location.getBearing());

            if (viewModel.isAutoTrackingEnabled()) {
                updateCamera(position, location.getBearing());
            }

            updateStepCount();
            updateDirectionInfo(location.getBearing());
            checkLocationInFloorPlan(position);

        } catch (Exception e) {
            Log.e(TAG, "Error updating location UI", e);
        }
    }

    private void updateCamera(LatLng position, float bearing) {
        CameraPosition cameraPosition = new CameraPosition(
                position,
                18.0,  // 줌 레벨
                0,     // 틸트
                bearing
        );
        naverMap.setCameraPosition(cameraPosition);
    }

    private void checkLocationInFloorPlan(LatLng position) {
        LatLngBounds bounds = floorPlanManager.getBounds();
        if (bounds != null) {
            boolean isInside = bounds.contains(position);
            handleLocationInFloorPlan(isInside, position);
        }
    }

    private void handleLocationInFloorPlan(boolean isInside, LatLng position) {
        binding.locationWarning.setVisibility(isInside ? View.GONE : View.VISIBLE);

        if (isInside) {
            updateRelativePosition(position);
        } else {
            binding.locationWarning.setText(R.string.outside_floor_plan_warning);
            voiceGuideManager.announce(getString(R.string.outside_floor_plan_warning));
        }
    }

    private void updateRelativePosition(LatLng position) {
        LatLngBounds bounds = floorPlanManager.getBounds();
        if (bounds == null) return;

        double relativeX = (position.longitude - bounds.getSouthWest().longitude) /
                (bounds.getNorthEast().longitude - bounds.getSouthWest().longitude);
        double relativeY = (position.latitude - bounds.getSouthWest().latitude) /
                (bounds.getNorthEast().latitude - bounds.getSouthWest().latitude);

        String positionText = getString(R.string.relative_position_format,
                Math.round(relativeX * 100),
                Math.round(relativeY * 100));

        binding.relativePositionText.setText(positionText);
    }

    private void updateStepCount() {
        stepCount++;
        String stepText = getString(R.string.step_count_format, stepCount);
        binding.indoorStepCount.setText(stepText);
        binding.indoorStepCount.setContentDescription(stepText);
    }

    private void updateDirectionInfo(float bearing) {
        String direction = getDirectionText(bearing);
        String directionText = getString(R.string.direction_format, direction);
        binding.indoorDirection.setText(directionText);
        binding.indoorDirection.setContentDescription(directionText);
    }

    private String getDirectionText(float degrees) {
        String[] directions = {"북", "북동", "동", "남동", "남", "남서", "서", "북서"};
        int index = Math.round(degrees / 45f) % 8;
        return directions[index];
    }

    private void announceCurrentStatus() {
        LocationData location = viewModel.getCurrentLocation().getValue();
        if (location != null) {
            String direction = getDirectionText(location.getBearing());
            @SuppressLint({"StringFormatInvalid", "LocalSuppress"})
            String announcement = getString(R.string.current_status_format,
                    direction, stepCount);
            voiceGuideManager.announce(announcement);
        }
    }

    private void handleEnvironmentChange(EnvironmentType environment) {
        if (environment == EnvironmentType.OUTDOOR) {
            navigateToOutdoor();
        }
    }

    private void navigateToOutdoor() {
        NavController navController = Navigation.findNavController(requireView());
        navController.navigate(R.id.action_indoorMap_to_outdoorMap);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_STEP_COUNT, stepCount);
        outState.putFloat(KEY_OVERLAY_OPACITY,
                binding.overlayOpacitySeekBar.getProgress() / 100.0f);
        outState.putFloat(KEY_OVERLAY_ROTATION,
                binding.overlayRotationSeekBar.getProgress() * 3.6f);
    }

    private void restoreState(@NonNull Bundle savedState) {
        stepCount = savedState.getInt(KEY_STEP_COUNT, 0);
        float opacity = savedState.getFloat(KEY_OVERLAY_OPACITY, FloorPlanConfig.OPACITY);
        float rotation = savedState.getFloat(KEY_OVERLAY_ROTATION, FloorPlanConfig.ROTATION);

        if (binding != null) {
            binding.overlayOpacitySeekBar.setProgress((int)(opacity * 100));
            binding.overlayRotationSeekBar.setProgress((int)(rotation / 3.6f));
        }

        floorPlanManager.setOpacity(opacity);
        floorPlanManager.setRotation(rotation);
    }

    private void handleOverlayError(Exception e) {
        String errorMessage = getString(R.string.floor_plan_load_error);
        binding.locationWarning.setVisibility(View.VISIBLE);
        binding.locationWarning.setText(errorMessage);
        voiceGuideManager.announce(errorMessage);
        Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_SHORT).show();
        Log.e(TAG, "Floor plan error: " + e.getMessage());
    }

    // 생명주기 관리
    @Override
    public void onStart() {
        super.onStart();
        if (binding != null) {
            binding.indoorMapView.onStart();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (binding != null) {
            binding.indoorMapView.onResume();
        }
    }

    @Override
    public void onPause() {
        if (binding != null) {
            binding.indoorMapView.onPause();
        }
        super.onPause();
    }

    @Override
    public void onStop() {
        if (binding != null) {
            binding.indoorMapView.onStop();
        }
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        if (floorPlanManager != null) {
            floorPlanManager.cleanup();
        }
        if (binding != null) {
            binding.indoorMapView.onDestroy();
            binding = null;
        }
        super.onDestroyView();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        if (binding != null) {
            binding.indoorMapView.onLowMemory();
        }
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
