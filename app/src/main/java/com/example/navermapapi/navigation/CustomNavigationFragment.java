package com.example.navermapapi.navigation;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.navermapapi.R;
import com.example.navermapapi.appModule.accessibility.VoiceGuideManager;
import com.example.navermapapi.appModule.main.MainViewModel;
import com.example.navermapapi.databinding.FragmentCustomNavigationBinding;
import com.example.navermapapi.path.calculator.PathCalculator;
import com.example.navermapapi.path.drawer.PathDrawer;
import com.example.navermapapi.path.manager.PathDataManager;
import com.naver.maps.geometry.LatLng;
import com.naver.maps.map.LocationTrackingMode;
import com.naver.maps.map.MapFragment;
import com.naver.maps.map.NaverMap;
import com.naver.maps.map.OnMapReadyCallback;
import com.naver.maps.map.CameraPosition;
import com.naver.maps.map.overlay.LocationOverlay;
import com.naver.maps.map.overlay.Marker;
import com.naver.maps.map.util.FusedLocationSource;

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

        // 초기 카메라 위치 설정 (전시장 중심)
        naverMap.setCameraPosition(new CameraPosition(
                new LatLng(37.558347, 127.048963), // 전시장 중심 좌표
                17 // 줌 레벨
        ));
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
        voiceGuideManager.announce("지도를 터치하여 목적지를 선택해주세요");
        Toast.makeText(requireContext(), "지도를 터치하여 목적지를 선택해주세요", Toast.LENGTH_SHORT).show();
    }

    private void handleDestinationSelection(LatLng selectedPoint) {
        // 선택된 지점에서 가장 가까운 경로 상의 점 찾기
        LatLng destinationOnPath = PathCalculator.findNearestPointOnPath(selectedPoint);

        // 마커 표시
        destinationMarker.setPosition(destinationOnPath);
        destinationMarker.setMap(naverMap);

        isDestinationMode = false;
        viewModel.setDestination(destinationOnPath);

        calculateAndShowPath();
    }

    private void calculateAndShowPath() {
        if (locationOverlay == null || !locationOverlay.isVisible()) {
            Log.e(TAG, "Location overlay is not available");
            voiceGuideManager.announce("현재 위치를 확인할 수 없습니다");
            return;
        }

        LatLng currentLocation = new LatLng(
                locationOverlay.getPosition().latitude,
                locationOverlay.getPosition().longitude
        );
        LatLng destination = viewModel.getDestination().getValue();

        if (destination == null) {
            Log.e(TAG, "Destination is null");
            voiceGuideManager.announce("목적지를 먼저 선택해주세요");
            return;
        }

        // 현재 위치에서 가장 가까운 경로 상의 점 찾기
        LatLng startOnPath = PathCalculator.findNearestPointOnPath(currentLocation);

        // 경로 그리기
        pathDrawer.drawPath(startOnPath, destination);

        // 거리 계산 및 안내
        double distance = PathCalculator.calculatePathDistance(startOnPath, destination);
        String announcement = String.format("목적지까지 약 %.0f미터입니다", distance);
        voiceGuideManager.announce(announcement);
        binding.navigationInfo.setText(announcement);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
        if (pathDrawer != null) {
            pathDrawer.clearPath();
        }
        if (destinationMarker != null) {
            destinationMarker.setMap(null);
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
