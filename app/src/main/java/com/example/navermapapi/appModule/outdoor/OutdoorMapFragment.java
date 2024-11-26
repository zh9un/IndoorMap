package com.example.navermapapi.appModule.outdoor;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

import com.example.navermapapi.R;
import com.example.navermapapi.appModule.main.MainViewModel;
import com.example.navermapapi.coreModule.api.environment.model.EnvironmentType;
import com.example.navermapapi.coreModule.api.location.model.LocationData;
import com.example.navermapapi.databinding.FragmentOutdoorMapBinding;
import com.naver.maps.geometry.LatLng;
import com.naver.maps.map.CameraPosition;
import com.naver.maps.map.CameraUpdate;
import com.naver.maps.map.LocationTrackingMode;
import com.naver.maps.map.MapFragment;
import com.naver.maps.map.NaverMap;
import com.naver.maps.map.OnMapReadyCallback;
import com.naver.maps.map.overlay.LocationOverlay;
import com.naver.maps.map.overlay.Marker;
import com.naver.maps.map.overlay.PathOverlay;
import com.naver.maps.map.util.FusedLocationSource;

import java.util.ArrayList;
import java.util.List;
import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class OutdoorMapFragment extends Fragment implements OnMapReadyCallback {
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1000;

    private FragmentOutdoorMapBinding binding;
    private MainViewModel viewModel;
    private NaverMap naverMap;
    private LocationOverlay locationOverlay;
    private FusedLocationSource locationSource;
    private List<Marker> destinationMarkers;
    private PathOverlay pathOverlay;

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
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
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
        MapFragment mapFragment = (MapFragment) getChildFragmentManager().findFragmentById(R.id.outdoor_map);
        if (mapFragment == null) {
            mapFragment = MapFragment.newInstance();
            getChildFragmentManager().beginTransaction()
                    .add(R.id.outdoor_map, mapFragment)
                    .commit();
        }
        mapFragment.getMapAsync(this);
    }

    @Override
    public void onMapReady(@NonNull NaverMap naverMap) {
        this.naverMap = naverMap;

        setupMapSettings(naverMap);
        setupLocationOverlay(naverMap);
        setupMapUI(naverMap);

        // 마지막 위치로 카메라 이동
        LocationData lastLocation = viewModel.getCurrentLocation().getValue();
        if (lastLocation != null) {
            updateLocationUI(lastLocation);
        }
    }

    private void setupMapSettings(@NonNull NaverMap naverMap) {
        naverMap.setLocationSource(locationSource);
        naverMap.setLocationTrackingMode(LocationTrackingMode.Follow);

        naverMap.setLayerGroupEnabled(NaverMap.LAYER_GROUP_BUILDING, true);
        naverMap.setIndoorEnabled(true);

        naverMap.setMinZoom(5.0);
        naverMap.setMaxZoom(20.0);

        // 초기 카메라 위치 설정
        naverMap.setCameraPosition(new CameraPosition(
                new LatLng(37.5666102, 126.9783881), // 서울시청 좌표
                16
        ));
    }

    private void setupLocationOverlay(@NonNull NaverMap naverMap) {
        locationOverlay = naverMap.getLocationOverlay();
        locationOverlay.setVisible(true);
        locationOverlay.setCircleRadius(0); // 정확도 원 숨김
    }

    private void setupMapUI(@NonNull NaverMap naverMap) {
        naverMap.getUiSettings().setZoomControlEnabled(true);
        naverMap.getUiSettings().setScaleBarEnabled(true);
        naverMap.getUiSettings().setLocationButtonEnabled(true);

        // 지도 클릭 이벤트 설정
        naverMap.setOnMapClickListener((point, coord) -> {
            if (viewModel.isDestinationSelectionEnabled()) {
                addDestinationMarker(coord);
            }
        });
    }

    private void setupObservers() {
        // 위치 업데이트 관찰
        viewModel.getCurrentLocation().observe(getViewLifecycleOwner(), this::updateLocationUI);

        // 환경 변화 관찰 - 실내로 전환 시 필요
        viewModel.getCurrentEnvironment().observe(getViewLifecycleOwner(), this::handleEnvironmentChange);

        // 목적지 관찰
        viewModel.getDestination().observe(getViewLifecycleOwner(), this::updateDestinationUI);

        // 경로 관찰
        viewModel.getPath().observe(getViewLifecycleOwner(), this::updatePathUI);
    }

    private void updateLocationUI(LocationData location) {
        if (location == null || naverMap == null || locationOverlay == null) return;

        LatLng position = new LatLng(location.getLatitude(), location.getLongitude());
        locationOverlay.setPosition(position);
        locationOverlay.setBearing(location.getBearing());

        if (viewModel.isAutoTrackingEnabled()) {
            naverMap.moveCamera(CameraUpdate.scrollTo(position));
        }

        // 현재 위치 텍스트 업데이트
        updateLocationText(position);
    }

    private void handleEnvironmentChange(EnvironmentType environment) {
        if (environment == EnvironmentType.INDOOR) {
            // IndoorMapFragment로 전환
            navigateToIndoorMap();
        }
    }

    private void updateLocationText(LatLng position) {
        binding.locationInfo.setText(String.format("위치: %.6f, %.6f",
                position.latitude, position.longitude));
    }

    private void addDestinationMarker(LatLng position) {
        // 기존 마커 제거
        clearDestinationMarkers();

        // 새 마커 추가
        Marker marker = new Marker();
        marker.setPosition(position);
        marker.setMap(naverMap);
        destinationMarkers.add(marker);

        // ViewModel에 목적지 업데이트
        viewModel.setDestination(position);
    }

    private void clearDestinationMarkers() {
        for (Marker marker : destinationMarkers) {
            marker.setMap(null);
        }
        destinationMarkers.clear();
    }

    private void updateDestinationUI(LatLng destination) {
        if (destination == null) {
            clearDestinationMarkers();
            return;
        }

        // 마커가 없으면 새로 추가
        if (destinationMarkers.isEmpty()) {
            addDestinationMarker(destination);
        }
    }

    private void updatePathUI(List<LatLng> path) {
        if (path == null || path.isEmpty()) {
            pathOverlay.setMap(null);
            return;
        }

        pathOverlay.setCoords(path);
        pathOverlay.setMap(naverMap);
    }

    private void navigateToIndoorMap() {
        NavController navController = NavHostFragment.findNavController(this);
        navController.navigate(R.id.action_outdoorMap_to_indoorMap);
   }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
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