package com.example.navermapapi.appModule.indoor;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.navermapapi.R;
import com.example.navermapapi.appModule.main.MainActivity;
import com.example.navermapapi.appModule.main.MainViewModel;
import com.example.navermapapi.appModule.outdoor.OutdoorMapFragment;
import com.example.navermapapi.coreModule.api.environment.model.EnvironmentType;
import com.example.navermapapi.coreModule.api.location.model.LocationData;
import com.example.navermapapi.databinding.FragmentIndoorMapBinding;
import com.naver.maps.geometry.LatLng;
import com.naver.maps.geometry.LatLngBounds;
import com.naver.maps.map.CameraPosition;
import com.naver.maps.map.CameraUpdate; // Import CameraUpdate here
import com.naver.maps.map.LocationTrackingMode;
import com.naver.maps.map.NaverMap;
import com.naver.maps.map.OnMapReadyCallback;
import com.naver.maps.map.overlay.LocationOverlay;
import com.naver.maps.map.util.FusedLocationSource;

import java.util.Objects;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class IndoorMapFragment extends Fragment implements OnMapReadyCallback {
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1000;
    private static final LatLng REFERENCE_POINT = new LatLng(37.5666102, 126.9783881); // 기준점 좌표
    private static final double FLOOR_PLAN_WIDTH_METERS = 100.0; // 도면의 실제 너비 (미터)
    private static final double FLOOR_PLAN_HEIGHT_METERS = 80.0; // 도면의 실제 높이 (미터)

    private FragmentIndoorMapBinding binding;
    private MainViewModel viewModel;
    private NaverMap naverMap;
    private LocationOverlay locationOverlay;
    private FusedLocationSource locationSource;
    private IndoorMapOverlay indoorMapOverlay;
    private int stepCount = 0;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
        locationSource = new FusedLocationSource(this, LOCATION_PERMISSION_REQUEST_CODE);
        indoorMapOverlay = new IndoorMapOverlay();
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
        // 도면 투명도 조절 SeekBar 설정
        binding.overlayOpacitySeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (indoorMapOverlay != null) {
                    float opacity = progress / 100.0f;
                    indoorMapOverlay.setOpacity(opacity);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // 도면 회전 SeekBar 설정
        binding.overlayRotationSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (indoorMapOverlay != null) {
                    double rotation = progress * 3.6; // 0-100을 0-360도로 변환
                    indoorMapOverlay.setRotation(rotation);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // 음성 안내 버튼 설정
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
        setupIndoorMapOverlay();

        // Geocoding 호출하여 REFERENCE_POINT 설정
        String targetAddress = "서울 성동구 살곶이길 200";
        LatLng coordinates = getCoordinatesFromAddress(targetAddress);

        if (coordinates != null) {
            // REFERENCE_POINT를 기준으로 카메라 이동
            naverMap.moveCamera(CameraUpdate.scrollTo(coordinates));
        }

        // 마지막 위치로 카메라 이동
        LocationData lastLocation = viewModel.getCurrentLocation().getValue();
        if (lastLocation != null) {
            updateLocationUI(lastLocation);
        }
    }

    // Geocoding API로 주소를 좌표로 변환하는 메서드
    public LatLng getCoordinatesFromAddress(String address) {
        final LatLng[] coordinates = new LatLng[1];

        // Retrofit 초기화
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://naveropenapi.apigw.ntruss.com/") // 네이버 API 기본 URL
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        GeocodingService service = retrofit.create(GeocodingService.class);

        // API 호출
        Call<GeocodingResponse> call = service.getCoordinates(address, "YOUR_CLIENT_ID", "YOUR_CLIENT_SECRET");
        call.enqueue(new Callback<GeocodingResponse>() {
            @Override
            public void onResponse(Call<GeocodingResponse> call, Response<GeocodingResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    GeocodingResponse result = response.body();
                    // 첫 번째 좌표값 추출
                    double latitude = result.getAddresses().get(0).getLat();
                    double longitude = result.getAddresses().get(0).getLng();
                    coordinates[0] = new LatLng(latitude, longitude);
                }
            }

            @Override
            public void onFailure(Call<GeocodingResponse> call, Throwable t) {
                t.printStackTrace();
            }
        });

        return coordinates[0];
    }

    private void setupMapSettings(@NonNull NaverMap naverMap) {
        naverMap.setLocationSource(locationSource);
        naverMap.setLocationTrackingMode(LocationTrackingMode.Follow);
        naverMap.setIndoorEnabled(true);

        naverMap.setMinZoom(17.0);
        naverMap.setMaxZoom(20.0);

        // 지도 UI 설정
        naverMap.getUiSettings().setIndoorLevelPickerEnabled(true);
        naverMap.getUiSettings().setZoomControlEnabled(true);
        naverMap.getUiSettings().setCompassEnabled(true);
    }

    private void setupLocationOverlay(@NonNull NaverMap naverMap) {
        locationOverlay = naverMap.getLocationOverlay();
        locationOverlay.setVisible(true);
    }

    private void setupIndoorMapOverlay() {
        try {
            // 도면 이미지 로드
            Bitmap floorPlanBitmap = BitmapFactory.decodeResource(
                    getResources(),
                    R.drawable.indoor_floor_plan_3f // 실제 도면 이미지 리소스
            );

            // 도면이 표시될 영역 계산
            LatLngBounds bounds = calculateFloorPlanBounds(REFERENCE_POINT,
                    FLOOR_PLAN_WIDTH_METERS,
                    FLOOR_PLAN_HEIGHT_METERS);

            // 오버레이 설정
            indoorMapOverlay.setFloorPlan(floorPlanBitmap);
            indoorMapOverlay.setBounds(bounds);
            indoorMapOverlay.setMap(naverMap);
            indoorMapOverlay.setOpacity(0.7f); // 초기 투명도 설정

        } catch (Exception e) {
            e.printStackTrace();
            // 에러 처리
        }
    }

    private LatLngBounds calculateFloorPlanBounds(LatLng center, double widthMeters, double heightMeters) {
        // 미터 단위를 위경도로 변환 (근사값)
        double metersPerLat = 111320.0; // 1도당 미터
        double metersPerLng = 111320.0 * Math.cos(Math.toRadians(center.latitude));

        double latOffset = (heightMeters / 2) / metersPerLat;
        double lngOffset = (widthMeters / 2) / metersPerLng;

        return new LatLngBounds(
                new LatLng(center.latitude - latOffset, center.longitude - lngOffset),
                new LatLng(center.latitude + latOffset, center.longitude + lngOffset)
        );
    }

    private void updateLocationUI(LocationData location) {
        if (location == null || naverMap == null || locationOverlay == null) return;

        LatLng position = new LatLng(location.getLatitude(), location.getLongitude());
        locationOverlay.setPosition(position);
        locationOverlay.setBearing(location.getBearing());

        // 지도 카메라 업데이트
        if (viewModel.isAutoTrackingEnabled()) {
            CameraPosition cameraPosition = new CameraPosition(
                    position,
                    18.0,  // 줌 레벨
                    0,     // 틸트
                    location.getBearing()  // 방향
            );
            naverMap.setCameraPosition(cameraPosition);
        }

        updateStepCount();
        updateDirectionInfo(location.getBearing());

        // 현재 위치가 도면 영역 내부인지 확인하고 처리
        if (indoorMapOverlay != null && indoorMapOverlay.getBounds() != null) {
            boolean isInsideFloorPlan = indoorMapOverlay.getBounds().contains(position);
            handleLocationInFloorPlan(isInsideFloorPlan, position);
        }
    }

    private void handleLocationInFloorPlan(boolean isInside, LatLng position) {
        if (isInside) {
            // 도면 내부에 있을 때의 처리
            indoorMapOverlay.setVisible(true);
            updateRelativePosition(position);
        } else {
            // 도면 외부에 있을 때의 처리
            binding.locationWarning.setVisibility(View.VISIBLE);
            binding.locationWarning.setText(R.string.outside_floor_plan_warning);
        }
    }

    private void updateRelativePosition(LatLng currentPosition) {
        if (indoorMapOverlay.getBounds() == null) return;

        // 도면 상의 상대 위치 계산 (0-1 범위)
        LatLngBounds bounds = indoorMapOverlay.getBounds();
        double relativeX = (currentPosition.longitude - bounds.getSouthWest().longitude) /
                (bounds.getNorthEast().longitude - bounds.getSouthWest().longitude);
        double relativeY = (currentPosition.latitude - bounds.getSouthWest().latitude) /
                (bounds.getNorthEast().latitude - bounds.getSouthWest().latitude);

        // UI 업데이트
        binding.relativePositionText.setText(String.format(
                getString(R.string.relative_position_format),
                Math.round(relativeX * 100),
                Math.round(relativeY * 100)
        ));
    }

    private void handleEnvironmentChange(EnvironmentType environment) {
        if (environment == EnvironmentType.OUTDOOR) {
            // 실외로 전환될 때 도면 숨김
            if (indoorMapOverlay != null) {
                indoorMapOverlay.setVisible(false);
            }
            // OutdoorMapFragment로 전환
            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.nav_host_fragment, new OutdoorMapFragment())
                    .commit();
        } else {
            // 실내로 전환될 때 도면 표시
            if (indoorMapOverlay != null) {
                indoorMapOverlay.setVisible(true);
            }
        }
    }

    private void updateStepCount() {
        stepCount++;
        binding.indoorStepCount.setText(getString(R.string.step_count_format, stepCount));
    }

    private void updateDirectionInfo(float bearing) {
        String direction = getDirectionText(bearing);
        binding.indoorDirection.setText(getString(R.string.direction_format, direction));
    }

    private String getDirectionText(float degrees) {
        // 8방향으로 방향 텍스트 변환
        String[] directions = {"북", "북동", "동", "남동", "남", "남서", "서", "북서"};
        int index = Math.round(degrees / 45f) % 8;
        return directions[index];
    }

    private void announceCurrentStatus() {
        LocationData location = viewModel.getCurrentLocation().getValue();
        if (location != null) {
            String direction = getDirectionText(location.getBearing());
            String announcement = String.format(
                    getString(R.string.current_status_announcement),
                    direction,
                    stepCount
            );
            // VoiceGuideManager를 통해 안내 (이미 구현되어 있음)
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).announceVoiceGuide(announcement);
            }
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        binding.indoorMapView.onSaveInstanceState(outState);
        // 현재 상태 저장
        outState.putInt("stepCount", stepCount);
        outState.putFloat("overlayOpacity",
                binding.overlayOpacitySeekBar.getProgress() / 100.0f);
        outState.putFloat("overlayRotation",
                binding.overlayRotationSeekBar.getProgress() * 3.6f);
    }

    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);
        if (savedInstanceState != null) {
            // 저장된 상태 복원
            stepCount = savedInstanceState.getInt("stepCount", 0);
            float opacity = savedInstanceState.getFloat("overlayOpacity", 0.7f);
            float rotation = savedInstanceState.getFloat("overlayRotation", 0f);

            binding.overlayOpacitySeekBar.setProgress((int)(opacity * 100));
            binding.overlayRotationSeekBar.setProgress((int)(rotation / 3.6f));

            if (indoorMapOverlay != null) {
                indoorMapOverlay.setOpacity(opacity);
                indoorMapOverlay.setRotation(rotation);
            }
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
        super.onDestroyView();
        if (indoorMapOverlay != null) {
            indoorMapOverlay.cleanup();
        }
        binding.indoorMapView.onDestroy();
        binding = null;
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
