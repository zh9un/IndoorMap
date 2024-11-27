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
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import com.example.navermapapi.R;
import com.example.navermapapi.appModule.accessibility.VoiceGuideManager;
import com.example.navermapapi.appModule.main.MainViewModel;
import com.example.navermapapi.coreModule.api.environment.model.EnvironmentType;
import com.example.navermapapi.coreModule.api.location.model.LocationData;
import com.example.navermapapi.databinding.FragmentOutdoorMapBinding;
import com.naver.maps.geometry.LatLng;
import com.naver.maps.geometry.LatLngBounds;
import com.naver.maps.map.CameraPosition;
import com.naver.maps.map.CameraUpdate;
import com.naver.maps.map.LocationTrackingMode;
import com.naver.maps.map.MapFragment;
import com.naver.maps.map.NaverMap;
import com.naver.maps.map.OnMapReadyCallback;
import com.naver.maps.map.overlay.Marker;
import com.naver.maps.map.overlay.LocationOverlay;
import com.naver.maps.map.overlay.PathOverlay;
import com.naver.maps.map.overlay.OverlayImage;
import com.naver.maps.map.util.FusedLocationSource;

import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class OutdoorMapFragment extends Fragment implements OnMapReadyCallback {
    private static final String TAG = "OutdoorMapFragment";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1000;

    // 전시장 위치 정의
    private static final LatLng EXHIBITION_CENTER = new LatLng(37.5666102, 126.9783881);
    private static final double EXHIBITION_RADIUS = 100.0;  // 미터

    private FragmentOutdoorMapBinding binding;
    private MainViewModel viewModel;
    private NaverMap naverMap;
    private LocationOverlay locationOverlay;
    private FusedLocationSource locationSource;
    private List<Marker> destinationMarkers;
    private PathOverlay pathOverlay;
    private boolean isNavigating = false;

    @Inject
    VoiceGuideManager voiceGuideManager;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
        locationSource = new FusedLocationSource(this, LOCATION_PERMISSION_REQUEST_CODE);
        destinationMarkers = new ArrayList<>();
        pathOverlay = new PathOverlay();
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
        setupUI();
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

    private void setupUI() {
        binding.setDestinationButton.setOnClickListener(v -> startDestinationSelection());
        binding.startNavigationButton.setOnClickListener(v -> startNavigation());

        binding.setDestinationButton.setContentDescription(
                getString(R.string.set_destination_description));
        binding.startNavigationButton.setContentDescription(
                getString(R.string.start_navigation_description));
    }

    private void setupObservers() {
        viewModel.getCurrentLocation().observe(getViewLifecycleOwner(), this::updateLocationUI);
        viewModel.getCurrentEnvironment().observe(getViewLifecycleOwner(), this::handleEnvironmentChange);
        viewModel.getDestination().observe(getViewLifecycleOwner(), this::updateDestinationUI);
    }

    @Override
    public void onMapReady(@NonNull NaverMap naverMap) {
        this.naverMap = naverMap;
        setupMapSettings(naverMap);
        setupLocationOverlay(naverMap);

        // 전시장 위치로 초기 카메라 이동
        naverMap.moveCamera(CameraUpdate.scrollTo(EXHIBITION_CENTER));

        // 지도 클릭 이벤트 설정
        naverMap.setOnMapClickListener((point, coord) -> {
            if (viewModel.isDestinationSelectionEnabled()) {
                addDestinationMarker(coord);
            }
        });

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
    }

    private void setupLocationOverlay(@NonNull NaverMap naverMap) {
        locationOverlay = naverMap.getLocationOverlay();
        locationOverlay.setVisible(true);
        locationOverlay.setCircleRadius(0);  // 정확도 원 숨김
    }

    private void updateLocationUI(LocationData location) {
        if (location == null || naverMap == null || locationOverlay == null) return;

        try {
            LatLng position = new LatLng(location.getLatitude(), location.getLongitude());
            locationOverlay.setPosition(position);
            locationOverlay.setBearing(location.getBearing());

            if (viewModel.isAutoTrackingEnabled()) {
                naverMap.moveCamera(CameraUpdate.scrollTo(position));
            }

            // 위치 정보 텍스트 업데이트
            String locationText = getString(R.string.current_location_format,
                    location.getLatitude(), location.getLongitude());
            binding.locationInfo.setText(locationText);
            binding.locationInfo.setContentDescription(locationText);

            // 전시장까지 거리 계산 및 표시
            updateDistanceToExhibition(position);

        } catch (Exception e) {
            Log.e(TAG, "Error updating location UI", e);
        }
    }

    private void updateDistanceToExhibition(LatLng currentPosition) {
        double distance = calculateDistance(currentPosition, EXHIBITION_CENTER);
        String distanceText = getString(R.string.distance_to_exhibition_format, (int)distance);
        binding.destinationInfo.setText(distanceText);
        binding.destinationInfo.setContentDescription(distanceText);
    }

    private double calculateDistance(LatLng point1, LatLng point2) {
        float[] results = new float[1];
        android.location.Location.distanceBetween(
                point1.latitude, point1.longitude,
                point2.latitude, point2.longitude,
                results);
        return results[0];
    }

    private void handleEnvironmentChange(EnvironmentType environment) {
        if (environment == EnvironmentType.INDOOR) {
            navigateToIndoor();
        }
    }

    private void navigateToIndoor() {
        NavController navController = Navigation.findNavController(requireView());
        navController.navigate(R.id.action_outdoorMap_to_indoorMap);
    }

    private void startDestinationSelection() {
        viewModel.setDestinationSelectionEnabled(true);
        voiceGuideManager.announce(getString(R.string.select_destination_prompt));
    }

    private void startNavigation() {
        if (viewModel.getDestination().getValue() == null) {
            voiceGuideManager.announce(getString(R.string.no_destination_set));
            return;
        }

        isNavigating = true;
        binding.startNavigationButton.setText(R.string.stop_navigation);
        voiceGuideManager.announce(getString(R.string.navigation_started));
    }

    private void addDestinationMarker(LatLng position) {
        clearDestinationMarkers();

        Marker marker = new Marker();
        marker.setPosition(position);
        marker.setIcon(OverlayImage.fromResource(R.drawable.ic_destination));
        marker.setMap(naverMap);
        destinationMarkers.add(marker);

        viewModel.setDestination(position);
        viewModel.setDestinationSelectionEnabled(false);

        // 목적지까지의 경로 표시
        drawPathToDestination(position);

        // 음성 안내
        double distance = calculateDistance(
                locationOverlay.getPosition(),
                position);
        String announcement = getString(R.string.destination_set_format, (int)distance);
        voiceGuideManager.announce(announcement);
    }

    private void clearDestinationMarkers() {
        for (Marker marker : destinationMarkers) {
            marker.setMap(null);
        }
        destinationMarkers.clear();
        pathOverlay.setMap(null);
    }

    private void drawPathToDestination(LatLng destination) {
        List<LatLng> coords = new ArrayList<>();
        coords.add(locationOverlay.getPosition());
        coords.add(destination);

        pathOverlay.setCoords(coords);
        pathOverlay.setWidth(getResources().getDimensionPixelSize(R.dimen.path_width));
        pathOverlay.setColor(getResources().getColor(R.color.path_color, null));
        pathOverlay.setMap(naverMap);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
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

    private void updateDestinationUI(LatLng destination) {
        if (destination == null) {
            clearDestinationMarkers();
            return;
        }

        // 기존 마커가 없으면 새로 추가
        if (destinationMarkers.isEmpty()) {
            addDestinationMarker(destination);
        }

        // 목적지까지의 경로 표시
        drawPathToDestination(destination);

        // 현재 위치부터 목적지까지의 거리 계산
        if (locationOverlay != null && locationOverlay.getPosition() != null) {
            double distance = calculateDistance(
                    locationOverlay.getPosition(),
                    destination
            );
            String distanceText = getString(R.string.distance_to_destination_format, (int)distance);
            binding.destinationInfo.setText(distanceText);
            binding.destinationInfo.setContentDescription(distanceText);
        }
    }
}