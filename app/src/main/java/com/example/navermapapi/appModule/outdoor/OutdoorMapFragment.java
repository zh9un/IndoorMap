package com.example.navermapapi.appModule.outdoor;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.navermapapi.R;
import com.example.navermapapi.appModule.accessibility.VoiceGuideManager;
import com.example.navermapapi.appModule.main.MainViewModel;
import com.example.navermapapi.coreModule.api.environment.model.EnvironmentType;
import com.example.navermapapi.coreModule.api.location.model.LocationData;
import com.example.navermapapi.databinding.FragmentOutdoorMapBinding;
import com.example.navermapapi.utils.FloorPlanConfig;
import com.example.navermapapi.utils.FloorPlanManager;
import com.naver.maps.geometry.LatLng;
import com.naver.maps.map.CameraAnimation;
import com.naver.maps.map.CameraPosition;
import com.naver.maps.map.CameraUpdate;
import com.naver.maps.map.LocationTrackingMode;
import com.naver.maps.map.MapFragment;
import com.naver.maps.map.NaverMap;
import com.naver.maps.map.OnMapReadyCallback;
import com.naver.maps.map.overlay.LocationOverlay;
import com.naver.maps.map.overlay.OverlayImage;
import com.naver.maps.map.util.FusedLocationSource;

import javax.inject.Inject;
import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class OutdoorMapFragment extends Fragment implements OnMapReadyCallback {
    private static final String TAG = "OutdoorMapFragment";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1000;

    private FragmentOutdoorMapBinding binding;
    private MainViewModel viewModel;
    private NaverMap naverMap;
    private LocationOverlay locationOverlay;
    private FusedLocationSource locationSource;
    private FloorPlanManager floorPlanManager;

    @Inject
    VoiceGuideManager voiceGuideManager;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
        locationSource = new FusedLocationSource(this, LOCATION_PERMISSION_REQUEST_CODE);
        floorPlanManager = FloorPlanManager.getInstance(requireContext());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentOutdoorMapBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initializeMap();
        setupObservers();
    }

    private void initializeMap() {
        MapFragment mapFragment = (MapFragment) getChildFragmentManager()
                .findFragmentById(R.id.outdoor_map);
        if (mapFragment == null) {
            mapFragment = MapFragment.newInstance();
            getChildFragmentManager().beginTransaction()
                    .add(R.id.outdoor_map, mapFragment)
                    .commit();
        }
        mapFragment.getMapAsync(this);
    }

    private void setupObservers() {
        viewModel.getCurrentLocation().observe(getViewLifecycleOwner(), this::updateLocationUI);
        viewModel.getCurrentEnvironment().observe(getViewLifecycleOwner(), this::updateEnvironmentUI);
    }

    @Override
    public void onMapReady(@NonNull NaverMap naverMap) {
        this.naverMap = naverMap;
        setupMapSettings(naverMap);
        setupLocationOverlay(naverMap);
        setupFloorPlan();

        // 전시장 위치로 초기 카메라 이동
        naverMap.setCameraPosition(new CameraPosition(FloorPlanConfig.CENTER, 17));

        LocationData lastLocation = viewModel.getCurrentLocation().getValue();
        if (lastLocation != null) {
            updateLocationUI(lastLocation);
        }
    }

    private void setupMapSettings(@NonNull NaverMap naverMap) {
        naverMap.setLocationSource(locationSource);
        naverMap.setLocationTrackingMode(LocationTrackingMode.Follow);

        naverMap.setMinZoom(10.0);
        naverMap.setMaxZoom(20.0);

        naverMap.getUiSettings().setZoomControlEnabled(true);
        naverMap.getUiSettings().setLocationButtonEnabled(true);
        naverMap.getUiSettings().setCompassEnabled(true);
        naverMap.getUiSettings().setIndoorLevelPickerEnabled(false);
    }

    private void setupLocationOverlay(@NonNull NaverMap naverMap) {
        locationOverlay = naverMap.getLocationOverlay();
        locationOverlay.setVisible(true);
        locationOverlay.setCircleRadius(0);

        EnvironmentType currentEnv = viewModel.getCurrentEnvironment().getValue();
        updateEnvironmentIcon(currentEnv);
    }

    private void setupFloorPlan() {
        try {
            floorPlanManager.initialize(
                    FloorPlanConfig.RESOURCE_ID,
                    FloorPlanConfig.CENTER,
                    FloorPlanConfig.OVERLAY_WIDTH_METERS,
                    FloorPlanConfig.ROTATION,
                    0.3f // 실외에서는 더 투명하게 설정
            );
            floorPlanManager.setMap(naverMap);
        } catch (Exception e) {
            Log.e(TAG, "Error setting up floor plan", e);
        }
    }

    private void updateLocationUI(LocationData location) {
        if (location == null || naverMap == null || locationOverlay == null) return;

        try {
            LatLng position = new LatLng(location.getLatitude(), location.getLongitude());
            locationOverlay.setPosition(position);
            locationOverlay.setBearing(location.getBearing());

            if (viewModel.isAutoTrackingEnabled()) {
                CameraUpdate cameraUpdate = CameraUpdate.scrollTo(position)
                        .animate(CameraAnimation.Easing);
                naverMap.moveCamera(cameraUpdate);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating location UI", e);
        }
    }

    private void updateEnvironmentUI(EnvironmentType environment) {
        if (environment == null) return;

        updateEnvironmentIcon(environment);
        updateFloorPlanVisibility(environment);
    }

    private void updateEnvironmentIcon(EnvironmentType environment) {
        if (locationOverlay == null) return;

        int iconResource = environment == EnvironmentType.INDOOR ?
                R.drawable.ic_indoor_location : R.drawable.ic_outdoor_location;
        locationOverlay.setIcon(OverlayImage.fromResource(iconResource));
    }

    private void updateFloorPlanVisibility(EnvironmentType environment) {
        if (floorPlanManager != null) {
            float opacity = environment == EnvironmentType.INDOOR ? 0.7f : 0.3f;
            floorPlanManager.setOpacity(opacity);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        cleanup();
    }

    private void cleanup() {
        if (floorPlanManager != null) {
            floorPlanManager.cleanup();
        }
        if (binding != null) {
            binding = null;
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