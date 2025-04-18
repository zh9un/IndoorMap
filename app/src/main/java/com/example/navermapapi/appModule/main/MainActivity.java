package com.example.navermapapi.appModule.main;

import android.Manifest;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import android.content.pm.PackageManager;
import android.widget.Toast;

import com.example.navermapapi.R;
import com.example.navermapapi.appModule.accessibility.VoiceGuideManager;
import com.example.navermapapi.appModule.location.manager.LocationIntegrationManager;
import com.example.navermapapi.beaconModule.internal.pdr.OrientationCalculator;
import com.example.navermapapi.constants.ExhibitionConstants;
import com.example.navermapapi.coreModule.api.environment.model.EnvironmentType;
import com.example.navermapapi.coreModule.api.location.model.LocationData;
import com.example.navermapapi.databinding.ActivityMainBinding;
import com.example.navermapapi.navigation.CustomNavigationFragment;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.naver.maps.geometry.LatLng;
import com.naver.maps.map.CameraAnimation;
import com.naver.maps.map.CameraUpdate;
import com.naver.maps.map.LocationTrackingMode;
import com.naver.maps.map.NaverMap;

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

    private MainViewModel viewModel;
    private ActivityMainBinding binding;
    private NavController navController;
    private boolean isDemoMode = false;
    private int currentDemoPoint = 0;
    private final Handler demoHandler = new Handler(Looper.getMainLooper());
    private OrientationCalculator orientationCalculator;

    @Inject
    LocationIntegrationManager locationManager;

    @Inject
    VoiceGuideManager voiceGuideManager;

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),
                    this::handlePermissionResult);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initializeBasicComponents();
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
                initializeOrientationCalculator();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in initialization", e);
            showFatalErrorDialog(e);
        }
    }

    private void initializeOrientationCalculator() {
        orientationCalculator = new OrientationCalculator(this);
        orientationCalculator.addOrientationCallback(this);
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

    private void setupUI() {
//        binding.setDestinationButton.setOnClickListener(v -> {
//            viewModel.setDestinationSelectionEnabled(true);
//            voiceGuideManager.announce(getString(R.string.select_destination_prompt));
//        });
//
//        binding.startNavigationButton.setOnClickListener(v -> {
//            if (viewModel.getDestination().getValue() == null) {
//                voiceGuideManager.announce(getString(R.string.no_destination_set));
//                return;
//            }
//            startNavigation();
//        });

        binding.demoButton.setOnClickListener(v -> {
            if (!isDemoMode) {
                startDemoMode();
            } else {
                stopDemoMode();
            }
        });
    }

    private void setupObservers() {
        // LocationManager의 데이터를 ViewModel로 전달
        locationManager.getCurrentLocation().observe(this, location -> {
            viewModel.updateCurrentLocation(location);
            updateLocationInfo(location);
        });

        locationManager.getCurrentEnvironment().observe(this, environment -> {
            viewModel.updateEnvironment(environment);
            updateEnvironmentInfo(environment);
        });

        // ViewModel 관찰
        viewModel.getCurrentEnvironment().observe(this, this::updateEnvironmentInfo);
    }

    private void updateLocationInfo(LocationData location) {
        if (location == null) return;
        String locationText = getString(R.string.current_location_format,
                location.getLatitude(), location.getLongitude());
//        binding.locationInfo.setText(locationText);
    }

    private void updateEnvironmentInfo(EnvironmentType environment) {
        if (environment == null) return;

        String environmentText;
        int backgroundColor;

        switch (environment) {
            case INDOOR:
                environmentText = "실내";
                backgroundColor = ContextCompat.getColor(this, R.color.environment_indoor);
                break;
            case OUTDOOR:
                environmentText = "실외";
                backgroundColor = ContextCompat.getColor(this, R.color.environment_outdoor);
                break;
            case TRANSITION:
                environmentText = "전환 중";
                backgroundColor = ContextCompat.getColor(this, R.color.environment_transition);
                break;
            default:
                environmentText = "알 수 없음";
                backgroundColor = ContextCompat.getColor(this, R.color.environment_unknown);
        }

        if (binding != null && binding.environmentStatus != null) {
            binding.environmentStatus.setText(environmentText);
            binding.environmentStatus.getBackground()
                    .setColorFilter(backgroundColor, PorterDuff.Mode.SRC_IN);
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

//    private void startNavigation() {
//        voiceGuideManager.announce(getString(R.string.navigation_started));
//        binding.startNavigationButton.setText(R.string.stop_navigation);
//    }

    private void startDemoMode() {
        isDemoMode = true;
        currentDemoPoint = 0;
        binding.demoButton.setText(R.string.stop_demo);

        // NaverMap 위치 추적 비활성화
        if (navController.getCurrentDestination().getId() == R.id.customNavigationFragment) {
            CustomNavigationFragment currentFragment =
                    (CustomNavigationFragment) getSupportFragmentManager()
                            .findFragmentById(R.id.nav_host_fragment)
                            .getChildFragmentManager()
                            .getFragments()
                            .get(0);

            if (currentFragment != null && currentFragment.getNaverMap() != null) {
                NaverMap naverMap = currentFragment.getNaverMap();
                naverMap.setLocationSource(null);
                naverMap.setLocationTrackingMode(LocationTrackingMode.None);
            }
        }

        updateDemoLocation();
        voiceGuideManager.announce("데모 모드를 시작합니다");
    }

    private void stopDemoMode() {
        isDemoMode = false;
        demoHandler.removeCallbacksAndMessages(null);
        binding.demoButton.setText(R.string.start_demo);

        // 데모 모드 종료 시, Fragment를 다시 생성하여 초기 상태로 복원
        if (navController.getCurrentDestination().getId() == R.id.customNavigationFragment) {
            navController.navigate(R.id.customNavigationFragment);
        }

        voiceGuideManager.announce("데모 모드를 종료합니다");
    }

    private void updateDemoLocation() {
        if (currentDemoPoint >= ExhibitionConstants.DEMO_SCENARIOS.length) {
            stopDemoMode();
            voiceGuideManager.announce("데모가 종료되었습니다");
            return;
        }

        ExhibitionConstants.DemoPoint demoPoint = ExhibitionConstants.DEMO_SCENARIOS[currentDemoPoint];

        // PDR 오프셋 정보 추가 (실내일 때의 상대 좌표)
        double offsetX = 0;
        double offsetY = 0;
        float bearing = 0;

        if (demoPoint.getEnvironment() == EnvironmentType.INDOOR) {
            // 순차적으로 증가하는 오프셋 설정
            offsetX = 2.0 * currentDemoPoint;  // 매 포인트마다 2m씩 이동
            offsetY = 1.5 * currentDemoPoint;  // 매 포인트마다 1.5m씩 이동
            bearing = 45.0f * currentDemoPoint % 360;  // 45도씩 회전
        }

        LocationData demoLocation = new LocationData.Builder(
                demoPoint.getLocation().latitude,
                demoPoint.getLocation().longitude)
                .accuracy(3.0f)
                .environment(demoPoint.getEnvironment())
                .provider("DEMO")
                .bearing(bearing)
                .offsetX(offsetX)  // PDR 오프셋 추가
                .offsetY(offsetY)  // PDR 오프셋 추가
                .build();

        locationManager.updateDemoLocation(demoLocation);
        voiceGuideManager.announce(demoPoint.getAnnouncement());

        currentDemoPoint++;

        if (currentDemoPoint < ExhibitionConstants.DEMO_SCENARIOS.length) {
            demoHandler.postDelayed(this::updateDemoLocation, 5000);
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
//            binding.setDestinationButton.setEnabled(true);
//            binding.startNavigationButton.setEnabled(true);
            binding.demoButton.setEnabled(true);
            startLocationTracking();

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

    @Override
    public void onOrientationChanged(float azimuth) {
        viewModel.setCurrentAzimuth(azimuth);
    }

    @Override
    public void onCalibrationComplete(float initialAzimuth) {
        Log.i(TAG, "Orientation calibration complete: " + initialAzimuth);
    }
}