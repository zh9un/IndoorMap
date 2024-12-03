package com.example.navermapapi.appModule.main;

import android.Manifest;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.graphics.PorterDuff;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import android.content.pm.PackageManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.example.navermapapi.R;
import com.example.navermapapi.appModule.accessibility.VoiceGuideManager;
import com.example.navermapapi.appModule.location.manager.LocationIntegrationManager;
import com.example.navermapapi.beaconModule.internal.pdr.OrientationCalculator;
import com.example.navermapapi.constants.ExhibitionConstants;
import com.example.navermapapi.coreModule.api.environment.model.EnvironmentType;
import com.example.navermapapi.coreModule.api.location.model.LocationData;
import com.example.navermapapi.databinding.ActivityMainBinding;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.naver.maps.geometry.LatLng;
import com.naver.maps.map.CameraAnimation;
import com.naver.maps.map.CameraUpdate;
import com.naver.maps.map.NaverMap;
import com.naver.maps.map.overlay.LocationOverlay;
import com.naver.maps.map.overlay.OverlayImage;
import android.graphics.Color;

import dagger.hilt.android.AndroidEntryPoint;
import javax.inject.Inject;
import java.util.Map;

@AndroidEntryPoint
public class MainActivity extends AppCompatActivity implements DefaultLifecycleObserver, OrientationCalculator.OrientationCallback {
    private static final String TAG = "MainActivity";
    private static final String[] REQUIRED_PERMISSIONS = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
    };

    private NaverMap naverMap;
    private LocationOverlay locationOverlay;
    private MainViewModel viewModel;
    private ActivityMainBinding binding;
    private NavController navController;
    private boolean isNavigating = false;
    private boolean isDemoMode = false;
    private int currentDemoPoint = 0;
    private final Handler demoHandler = new Handler(Looper.getMainLooper());

    // UI 컴포넌트
    private TextView locationInfoText;
    private TextView distanceText;

    @Inject
    LocationIntegrationManager locationManager;

    @Inject
    VoiceGuideManager voiceGuideManager;

    // 추가된 부분: OrientationCalculator 선언
    private OrientationCalculator orientationCalculator;

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),
                    this::handlePermissionResult);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initializeBasicComponents(); // 가장 먼저 실행되어야 함
    }

    private void initializeBasicComponents() {
        try {
            binding = ActivityMainBinding.inflate(getLayoutInflater());
            setContentView(binding.getRoot());
            viewModel = new ViewModelProvider(this).get(MainViewModel.class);

            if (checkGooglePlayServices()) {
                requestPermissions();
                setupNavigation();
                setupUI();
                setupObservers();
                setupLocationViews();

                // 추가된 부분: OrientationCalculator 초기화 및 보정
                initializeOrientationCalculator();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in initialization", e);
            showFatalErrorDialog(e);
        }
    }

    // 추가된 메서드: OrientationCalculator 초기화
    private void initializeOrientationCalculator() {
        orientationCalculator = new OrientationCalculator(this);

        // OrientationCallback 등록
        orientationCalculator.addOrientationCallback(this);

        // 방향 보정 수행
        orientationCalculator.calibrate();
    }

    private boolean checkGooglePlayServices() {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        int resultCode = apiAvailability.isGooglePlayServicesAvailable(this);
        if (resultCode != ConnectionResult.SUCCESS) {
            if (apiAvailability.isUserResolvableError(resultCode)) {
                apiAvailability.getErrorDialog(this, resultCode, 9000).show();
            } else {
                showServiceUnavailableDialog();
            }
            return false;
        }
        return true;
    }

    private void setupNavigation() {
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        if (navHostFragment == null) {
            throw new IllegalStateException("NavHostFragment not found");
        }
        navController = navHostFragment.getNavController();
    }

    private void setupLocationViews() {
        locationInfoText = binding.locationInfo;
        distanceText = binding.distanceInfo;
    }

    private void updateDistanceToExhibition(LatLng position) {
        if (position == null || ExhibitionConstants.EXHIBITION_POINT == null) {
            return;
        }

        float[] results = new float[1];

        // double -> float로 명시적 변환
        android.location.Location.distanceBetween(
                (float) position.latitude, (float) position.longitude, // 변환
                (float) ExhibitionConstants.EXHIBITION_POINT.latitude, // 변환
                (float) ExhibitionConstants.EXHIBITION_POINT.longitude, // 변환
                results
        );

        int distance = (int) results[0];
        String distanceText = getString(R.string.distance_to_exhibition_format, distance);
        binding.locationStatus.setText(distanceText);

        if (this.distanceText != null) {
            this.distanceText.setText(distanceText);
        }
    }

    private void setupUI() {
        setupNavigationButton();
        setupVoiceGuideButton();
        setupDemoButton();
        setupStatusViews();

        // NaverMap 초기화 및 설정
        initializeNaverMap();
    }

    // 추가된 메서드: NaverMap 초기화
    private void initializeNaverMap() {
        // NaverMap 객체를 초기화하고 설정합니다.
        // 예시로, NaverMapFragment에서 NaverMap 객체를 가져오는 코드를 작성합니다.
        // 실제 구현은 사용 중인 Naver Map API에 따라 다를 수 있습니다.
        // naverMap 객체가 초기화되면 locationOverlay도 초기화합니다.
        // 예:
        // naverMap = ... // NaverMap 객체 가져오기
        // locationOverlay = naverMap.getLocationOverlay();
        // locationOverlay.setVisible(true);
    }

    private void setupNavigationButton() {
        binding.navigationButton.setOnClickListener(v -> {
            if (!isDemoMode) {
                isNavigating = !isNavigating;
                updateNavigationState();
            }
        });
    }

    private void setupVoiceGuideButton() {
        binding.voiceGuideButton.setOnClickListener(v -> {
            LocationData location = locationManager.getCurrentLocation().getValue();
            if (location != null) {
                String description = getLocationDescription(location);
                voiceGuideManager.announce(description);
            }
        });
    }

    private void setupDemoButton() {
        binding.demoButton.setOnClickListener(v -> {
            if (!isDemoMode) {
                startDemoMode();
            } else {
                stopDemoMode();
            }
        });
    }

    private void setupStatusViews() {
        binding.environmentStatus.setContentDescription(getString(R.string.environment_status_description));
        binding.locationStatus.setContentDescription(getString(R.string.location_status_description));
    }

    private void setupObservers() {
        locationManager.getCurrentLocation().observe(this, this::updateLocationUI);
        locationManager.getCurrentEnvironment().observe(this, this::updateEnvironmentUI);
    }

    private void updateLocationUI(LocationData location) {
        if (location == null || naverMap == null || locationOverlay == null) {
            return;
        }

        try {
            // 현재 위치 정보
            LatLng position = new LatLng(location.getLatitude(), location.getLongitude());
            locationOverlay.setPosition(position);

            // 실제 기기 방향과 지도 방향 동기화
            float bearing = orientationCalculator != null ? orientationCalculator.getCurrentAzimuth() : location.getBearing();
            float mapRotation = (float) naverMap.getCameraPosition().bearing;
            float finalRotation = (bearing - mapRotation + 360) % 360;
            locationOverlay.setBearing(finalRotation);

            // 지도 카메라 이동 (Naver Maps API 활용)
            if (viewModel.isAutoTrackingEnabled()) {
                CameraUpdate cameraUpdate = CameraUpdate.scrollTo(position)
                        .animate(CameraAnimation.Easing, 300);
                naverMap.moveCamera(cameraUpdate);
            }

            // 위치 텍스트 업데이트
            String locationText = getString(R.string.current_location_format,
                    location.getLatitude(), location.getLongitude());
            if (locationInfoText != null) {
                locationInfoText.setText(locationText);
                locationInfoText.setContentDescription(locationText);
            }

            // 전시장까지 거리 계산 및 표시
            updateDistanceToExhibition(position);

            // 환경에 따라 마커 스타일 변경
            if (location.getEnvironment() == EnvironmentType.INDOOR) {
                locationOverlay.setIcon(OverlayImage.fromResource(R.drawable.ic_indoor_location));
                locationOverlay.setCircleColor(Color.GREEN);
            } else {
                locationOverlay.setIcon(OverlayImage.fromResource(R.drawable.ic_outdoor_location));
                locationOverlay.setCircleColor(Color.BLUE);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error updating location UI", e);
        }
    }

    private String getLocationDescription(LocationData location) {
        LatLng point = new LatLng(location.getLatitude(), location.getLongitude());
        String pointDesc = ExhibitionConstants.getPointDescription(point);

        if (location.getEnvironment() == EnvironmentType.INDOOR) {
            return String.format("실내 - %s", pointDesc);
        } else if (location.getEnvironment() == EnvironmentType.OUTDOOR) {
            return String.format("실외 - %s", pointDesc);
        } else {
            return String.format("전환 중 - %s", pointDesc);
        }
    }

    private void updateEnvironmentUI(EnvironmentType environment) {
        if (environment == null) return;

        String environmentText = getEnvironmentText(environment);
        int backgroundColor = getEnvironmentColor(environment);

        binding.environmentStatus.setText(environmentText);
        binding.environmentStatus.getBackground()
                .setColorFilter(backgroundColor, PorterDuff.Mode.SRC_IN);

        if (!isDemoMode) {
            voiceGuideManager.announce(String.format("%s 모드입니다", environmentText));
        }
    }

    private String getEnvironmentText(EnvironmentType environment) {
        switch (environment) {
            case INDOOR: return getString(R.string.environment_indoor);
            case OUTDOOR: return getString(R.string.environment_outdoor);
            case TRANSITION: return getString(R.string.environment_transition);
            default: return getString(R.string.environment_unknown);
        }
    }

    private int getEnvironmentColor(EnvironmentType environment) {
        switch (environment) {
            case INDOOR: return ContextCompat.getColor(this, R.color.environment_indoor);
            case OUTDOOR: return ContextCompat.getColor(this, R.color.environment_outdoor);
            case TRANSITION: return ContextCompat.getColor(this, R.color.environment_transition);
            default: return ContextCompat.getColor(this, R.color.environment_unknown);
        }
    }

    private void startDemoMode() {
        isDemoMode = true;
        currentDemoPoint = 0;
        binding.demoButton.setText(R.string.stop_demo);
        binding.navigationButton.setEnabled(false);
        updateDemoLocation();
        Toast.makeText(this, "데모 모드를 시작합니다", Toast.LENGTH_SHORT).show();
    }

    private void stopDemoMode() {
        isDemoMode = false;
        demoHandler.removeCallbacksAndMessages(null);
        binding.demoButton.setText(R.string.start_demo);
        binding.navigationButton.setEnabled(true);
        Toast.makeText(this, "데모 모드를 종료합니다", Toast.LENGTH_SHORT).show();
    }

    private void updateDemoLocation() {
        if (currentDemoPoint >= ExhibitionConstants.DEMO_PATH.length) {
            stopDemoMode();
            voiceGuideManager.announce("목적지에 도착했습니다. 데모를 종료합니다.");
            return;
        }

        LatLng currentPoint = ExhibitionConstants.DEMO_PATH[currentDemoPoint];
        LocationData demoLocation = new LocationData.Builder(
                currentPoint.latitude,
                currentPoint.longitude)
                .accuracy(3.0f)
                .environment(ExhibitionConstants.getEnvironmentType(currentPoint))
                .provider("DEMO")
                .build();

        locationManager.updateDemoLocation(demoLocation);
        locationManager.forceEnvironment(ExhibitionConstants.getEnvironmentType(currentPoint));
        voiceGuideManager.announce(ExhibitionConstants.getVoiceMessage(currentDemoPoint));

        currentDemoPoint++;
        if (currentDemoPoint < ExhibitionConstants.DEMO_PATH.length) {
            demoHandler.postDelayed(this::updateDemoLocation, 5000);
        }
    }

    private void updateNavigationState() {
        if (isNavigating) {
            binding.navigationButton.setText(R.string.stop_navigation);
            voiceGuideManager.announce(getString(R.string.navigation_started));
        } else {
            binding.navigationButton.setText(R.string.start_navigation);
            voiceGuideManager.announce(getString(R.string.navigation_stopped));
        }
    }

    private boolean checkPermissions() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private void requestPermissions() {
        permissionLauncher.launch(REQUIRED_PERMISSIONS);
    }

    private void handlePermissionResult(Map<String, Boolean> results) {
        boolean allGranted = true;
        for (Boolean isGranted : results.values()) {
            if (!isGranted) {
                allGranted = false;
                break;
            }
        }

        if (allGranted) {
            onPermissionsGranted();
        } else {
            showPermissionDeniedDialog();
        }
    }

    private void onPermissionsGranted() {
        try {
            locationManager.initialize();
            binding.voiceGuideButton.setEnabled(true);
            binding.navigationButton.setEnabled(true);
            binding.demoButton.setEnabled(true);
            startLocationTracking();

            // 추가된 부분: OrientationCalculator 다시 보정 (필요한 경우)
            if (orientationCalculator != null) {
                orientationCalculator.calibrate();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error initializing after permissions granted", e);
            showFatalErrorDialog(e);
        }
    }

    private void startLocationTracking() {
        if (checkPermissions()) {
            locationManager.startTracking();
        }
    }

    private void stopLocationTracking() {
        locationManager.stopTracking();
    }

    private void showPermissionDeniedDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.permission_required)
                .setMessage(R.string.permission_required_message)
                .setPositiveButton(R.string.settings, (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    Uri uri = Uri.fromParts("package", getPackageName(), null);
                    intent.setData(uri);
                    startActivity(intent);
                })
                .setNegativeButton(R.string.exit, (dialog, which) -> finish())
                .setCancelable(false)
                .show();
    }

    private void showServiceUnavailableDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.service_unavailable)
                .setMessage(R.string.google_play_services_required)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> finish())
                .setCancelable(false)
                .show();
    }

    private void showFatalErrorDialog(Exception e) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.fatal_error)
                .setMessage(getString(R.string.fatal_error_message, e.getMessage()))
                .setPositiveButton(R.string.exit, (dialog, which) -> finish())
                .setCancelable(false)
                .show();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!isDemoMode) {
            startLocationTracking();

            // OrientationCalculator가 있다면 시작
            if (orientationCalculator != null) {
                orientationCalculator.calibrate();
            }
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (!isDemoMode) {
            stopLocationTracking();

            // OrientationCalculator 리소스 해제
            if (orientationCalculator != null) {
                orientationCalculator.destroy();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cleanup();
    }

    private void cleanup() {
        stopDemoMode();
        if (voiceGuideManager != null) {
            voiceGuideManager.destroy();
        }
        if (locationManager != null) {
            locationManager.cleanup();
        }
        if (orientationCalculator != null) {
            orientationCalculator.destroy();
        }
        if (binding != null) {
            binding = null;
        }
    }

    // 추가된 부분: OrientationCalculator.OrientationCallback 인터페이스 구현
    @Override
    public void onOrientationChanged(float azimuth) {
        // 지도 상에 사용자의 방향을 업데이트하는 로직 구현
        if (naverMap != null && locationOverlay != null) {
            // 실제 기기 방향과 지도 방향 동기화
            float mapRotation = (float) naverMap.getCameraPosition().bearing;
            float finalRotation = (azimuth - mapRotation + 360) % 360;
            locationOverlay.setBearing(finalRotation);

            // 필요하다면 지도도 회전
            // CameraUpdate cameraUpdate = CameraUpdate.scrollAndRotateTo(
            //         naverMap.getCameraPosition().target,
            //         azimuth
            // ).animate(CameraAnimation.Easing, 300);
            // naverMap.moveCamera(cameraUpdate);
        }
    }

    @Override
    public void onCalibrationComplete(float initialAzimuth) {
        // 보정 완료 시 필요한 처리 구현
        Log.i(TAG, "Orientation calibration complete: " + initialAzimuth);
    }
}
