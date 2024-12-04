/*
 * 파일명: IndoorMapFragment.java
 * 경로: com.example.navermapapi.appModule.indoor
 * 작성자: Claude
 * 작성일: 2024-01-04
 */

package com.example.navermapapi.appModule.indoor;

import android.annotation.SuppressLint;
import android.graphics.Color;
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
import com.example.navermapapi.utils.CompassManager;
import com.example.navermapapi.utils.CoordinateConverter;

import com.naver.maps.geometry.LatLng;
import com.naver.maps.geometry.LatLngBounds;
import com.naver.maps.map.CameraPosition;
import com.naver.maps.map.CameraUpdate;
import com.naver.maps.map.LocationTrackingMode;
import com.naver.maps.map.MapFragment;
import com.naver.maps.map.NaverMap;
import com.naver.maps.map.OnMapReadyCallback;
import com.naver.maps.map.overlay.LocationOverlay;
import com.naver.maps.map.overlay.PathOverlay;
import com.naver.maps.map.overlay.OverlayImage;
import com.naver.maps.map.util.FusedLocationSource;

import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class IndoorMapFragment extends Fragment implements OnMapReadyCallback, CompassManager.CompassListener {
    private static final String TAG = "IndoorMapFragment";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1000;
    private static final int MAX_PATH_POINTS = 1000; // 경로 포인트 최대 개수

    private FragmentIndoorMapBinding binding;
    private MainViewModel viewModel;
    private NaverMap naverMap;
    private LocationOverlay locationOverlay;
    private FusedLocationSource locationSource;
    private FloorPlanManager floorPlanManager;
    private CompassManager compassManager;
    private CoordinateConverter coordinateConverter;
    private int stepCount = 0;

    // 경로 추적 관련
    private PathOverlay pathOverlay;
    private List<LatLng> pathPoints;
    private boolean isPathVisible = true;

    @Inject
    VoiceGuideManager voiceGuideManager;

    // 상태 저장을 위한 키
    private static final String KEY_STEP_COUNT = "step_count";
    private static final String KEY_OVERLAY_OPACITY = "overlay_opacity";
    private static final String KEY_OVERLAY_ROTATION = "overlay_rotation";
    private static final String KEY_PATH_VISIBLE = "path_visible";

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
        locationSource = new FusedLocationSource(this, LOCATION_PERMISSION_REQUEST_CODE);

        // CompassManager 초기화
        compassManager = new CompassManager(requireContext());
        compassManager.setCompassListener(this);

        // CoordinateConverter 초기화
        coordinateConverter = new CoordinateConverter();

        // 경로 추적 초기화
        pathPoints = new ArrayList<>();
        pathOverlay = new PathOverlay();
        setupPathOverlay();

        // FloorPlanManager 인스턴스 가져오기
        floorPlanManager = FloorPlanManager.getInstance(requireContext());

        if (savedInstanceState != null) {
            restoreState(savedInstanceState);
        }
    }

    /**
     * 경로 오버레이 초기화 설정
     */
    private void setupPathOverlay() {
        int pathColor = Color.argb(127, 128, 0, 128);  // 반투명 보라색
        pathOverlay.setColor(pathColor);
        pathOverlay.setWidth(getResources().getDimensionPixelSize(R.dimen.path_width));
        pathOverlay.setOutlineWidth(0); // 외곽선 없음
        pathOverlay.setPatternImage(null); // 패턴 없음
        pathOverlay.setGlobalZIndex(100); // 다른 오버레이보다 위에 표시
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
        setupFloorPlan();

        // 경로 오버레이 설정
        if (isPathVisible) {
            pathOverlay.setMap(naverMap);
        }

        // 마지막 위치로 초기화
        LocationData lastLocation = viewModel.getCurrentLocation().getValue();
        if (lastLocation != null) {
            // 좌표 변환기 초기화
            coordinateConverter.setReferencePoint(
                    new LatLng(lastLocation.getLatitude(), lastLocation.getLongitude())
            );
            updateLocationUI(lastLocation);
        }
    }

    private void setupMapSettings(@NonNull NaverMap naverMap) {
        // GPS 위치 소스 비활성화
        naverMap.setLocationSource(null);
        naverMap.setLocationTrackingMode(LocationTrackingMode.None);

        naverMap.setMinZoom(17.0);
        naverMap.setMaxZoom(20.0);

        naverMap.getUiSettings().setIndoorLevelPickerEnabled(true);
        naverMap.getUiSettings().setZoomControlEnabled(true);
        naverMap.getUiSettings().setCompassEnabled(true);
    }

    private void setupLocationOverlay(@NonNull NaverMap naverMap) {
        locationOverlay = naverMap.getLocationOverlay();
        locationOverlay.setVisible(true);
        locationOverlay.setIcon(OverlayImage.fromResource(R.drawable.ic_indoor_location));
        locationOverlay.setIconWidth(getResources().getDimensionPixelSize(R.dimen.location_marker_size));
        locationOverlay.setIconHeight(getResources().getDimensionPixelSize(R.dimen.location_marker_size));
    }

    private void setupFloorPlan() {
        try {
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
        if (location == null || naverMap == null || locationOverlay == null) {
            return;
        }

        try {
            // PDR 데이터만 처리
            if (!"PDR".equals(location.getProvider())) {
                Log.d(TAG, "Ignoring non-PDR location update");
                return;
            }

            // 상대 좌표를 절대 좌표로 변환
            LatLng position = coordinateConverter.toLatLng(
                    location.getOffsetX(),
                    location.getOffsetY()
            );

            // 위치 오버레이 업데이트
            locationOverlay.setPosition(position);
            locationOverlay.setBearing(location.getBearing());

            // 경로점 추가
            updatePath(position);

            // 카메라 업데이트
            if (viewModel.isAutoTrackingEnabled()) {
                updateCamera(position, location.getBearing());
            }

            // 상태 정보 업데이트
            updateStepCount();
            updateDirectionInfo(location.getBearing());
            checkLocationInFloorPlan(position);

        } catch (Exception e) {
            Log.e(TAG, "Error updating location UI", e);
        }
    }

    private void updatePath(LatLng position) {
        // 경로 포인트 추가
        pathPoints.add(position);

        // 최대 포인트 수 제한
        if (pathPoints.size() > MAX_PATH_POINTS) {
            pathPoints.remove(0);
        }

        // 경로 오버레이 업데이트
        if (isPathVisible && pathOverlay != null) {
            pathOverlay.setCoords(new ArrayList<>(pathPoints));
        }
    }

    private void updateCamera(LatLng position, float bearing) {
        CameraPosition cameraPosition = new CameraPosition(
                position,
                18.0,  // 줌 레벨
                0,     // 틸트
                bearing
        );
        naverMap.moveCamera(CameraUpdate.toCameraPosition(cameraPosition));
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
            // 실외 전환 시 경로 초기화 및 화면 전환
            clearPath();
            navigateToOutdoor();
        }
    }

    private void navigateToOutdoor() {
        NavController navController = Navigation.findNavController(requireView());
        navController.navigate(R.id.action_indoorMap_to_outdoorMap);
    }

    /**
     * CompassListener 인터페이스 구현
     */
    @Override
    public void onCompassChanged(float azimuth, float pitch, float roll) {
        if (locationOverlay != null) {
            // 방위각 업데이트
            locationOverlay.setBearing(azimuth);

            // 방향 정보 업데이트
            updateDirectionInfo(azimuth);
        }
    }

    /**
     * 경로 관련 메서드들
     */
    public void setPathVisible(boolean visible) {
        isPathVisible = visible;
        if (naverMap != null) {
            pathOverlay.setMap(visible ? naverMap : null);
        }
    }

    public void clearPath() {
        pathPoints.clear();
        if (pathOverlay != null) {
            pathOverlay.setCoords(pathPoints);
        }
    }

    private void handleOverlayError(Exception e) {
        String errorMessage = getString(R.string.floor_plan_load_error);
        binding.locationWarning.setVisibility(View.VISIBLE);
        binding.locationWarning.setText(errorMessage);
        voiceGuideManager.announce(errorMessage);
        Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_SHORT).show();
        Log.e(TAG, "Floor plan error: " + e.getMessage());
    }

    @Override
    public void onStart() {
        super.onStart();
        if (binding != null) {
            binding.indoorMapView.onStart();
        }
        compassManager.start();
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
        compassManager.stop();
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        if (floorPlanManager != null) {
            floorPlanManager.cleanup();
        }
        if (pathOverlay != null) {
            pathOverlay.setMap(null);
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
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_STEP_COUNT, stepCount);
        outState.putFloat(KEY_OVERLAY_OPACITY,
                binding.overlayOpacitySeekBar.getProgress() / 100.0f);
        outState.putFloat(KEY_OVERLAY_ROTATION,
                binding.overlayRotationSeekBar.getProgress() * 3.6f);
        outState.putBoolean(KEY_PATH_VISIBLE, isPathVisible);
    }

    private void restoreState(@NonNull Bundle savedState) {
        stepCount = savedState.getInt(KEY_STEP_COUNT, 0);
        float opacity = savedState.getFloat(KEY_OVERLAY_OPACITY, FloorPlanConfig.OPACITY);
        float rotation = savedState.getFloat(KEY_OVERLAY_ROTATION, FloorPlanConfig.ROTATION);
        isPathVisible = savedState.getBoolean(KEY_PATH_VISIBLE, true);

        // UI 상태 복원
        if (binding != null) {
            binding.overlayOpacitySeekBar.setProgress((int)(opacity * 100));
            binding.overlayRotationSeekBar.setProgress((int)(rotation / 3.6f));
        }

        // 도면 상태 복원
        if (floorPlanManager != null) {
            floorPlanManager.setOpacity(opacity);
            floorPlanManager.setRotation(rotation);
        }

        // 경로 표시 상태 복원
        if (naverMap != null) {
            pathOverlay.setMap(isPathVisible ? naverMap : null);
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