package com.example.navermapapi.appModule.indoor;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
public class IndoorMapFragment extends Fragment implements OnMapReadyCallback {
    private static final String TAG = "IndoorMapFragment";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1000;

    private FragmentIndoorMapBinding binding;
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
        binding = FragmentIndoorMapBinding.inflate(inflater, container, false);
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
                .findFragmentById(R.id.indoor_map_view);
        if (mapFragment == null) {
            mapFragment = MapFragment.newInstance();
            getChildFragmentManager().beginTransaction()
                    .add(R.id.indoor_map_view, mapFragment)
                    .commit();
        }
        mapFragment.getMapAsync(this);
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
        setupFloorPlan();

        LocationData lastLocation = viewModel.getCurrentLocation().getValue();
        if (lastLocation != null) {
            updateLocationUI(lastLocation);
        }
    }

    private void setupMapSettings(@NonNull NaverMap naverMap) {
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
        locationOverlay.setCircleRadius(0);

        EnvironmentType currentEnv = viewModel.getCurrentEnvironment().getValue();
        if (currentEnv == EnvironmentType.INDOOR) {
            locationOverlay.setIcon(OverlayImage.fromResource(R.drawable.ic_indoor_location));
        } else {
            locationOverlay.setIcon(OverlayImage.fromResource(R.drawable.ic_outdoor_location));
        }
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
        }
    }

    private void updateLocationUI(LocationData location) {
        if (location == null || naverMap == null || locationOverlay == null) return;

        try {
            double offsetX = location.getOffsetX();
            double offsetY = location.getOffsetY();

            LocationData initialLocation = viewModel.getCurrentLocation().getValue();
            if (initialLocation != null) {
                double metersPerLatitude = 111320;
                double metersPerLongitude = 111320 * Math.cos(Math.toRadians(initialLocation.getLatitude()));

                double newLat = initialLocation.getLatitude() + (offsetY / metersPerLatitude);
                double newLng = initialLocation.getLongitude() + (offsetX / metersPerLongitude);

                LatLng position = new LatLng(newLat, newLng);
                locationOverlay.setPosition(position);
                locationOverlay.setBearing(location.getBearing());

                if (viewModel.isAutoTrackingEnabled()) {
                    CameraUpdate cameraUpdate = CameraUpdate.scrollTo(position)
                            .animate(CameraAnimation.Easing);
                    naverMap.moveCamera(cameraUpdate);
                }

                checkLocationInFloorPlan(position);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating location UI", e);
        }
    }

    private void checkLocationInFloorPlan(LatLng position) {
        LatLngBounds bounds = floorPlanManager.getBounds();
        if (bounds != null) {
            if (!bounds.contains(position)) {
                handleEnvironmentChange(EnvironmentType.OUTDOOR);
            }
        }
    }

    private void handleEnvironmentChange(EnvironmentType environment) {
        if (environment == EnvironmentType.OUTDOOR) {
            NavController navController = Navigation.findNavController(requireView());
            navController.navigate(R.id.action_indoorMap_to_outdoorMap);
        }
    }

    @Override
    public void onDestroyView() {
        if (floorPlanManager != null) {
            floorPlanManager.cleanup();
        }
        if (binding != null) {
            binding = null;
        }
        super.onDestroyView();
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