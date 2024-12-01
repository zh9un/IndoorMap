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
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import android.content.pm.PackageManager;
import android.widget.Button;
import android.widget.Toast;

import com.example.navermapapi.R;
import com.example.navermapapi.appModule.accessibility.VoiceGuideManager;
import com.example.navermapapi.appModule.location.manager.LocationIntegrationManager;
import com.example.navermapapi.constants.ExhibitionConstants;
import com.example.navermapapi.coreModule.api.environment.model.EnvironmentType;
import com.example.navermapapi.coreModule.api.location.model.LocationData;
import com.example.navermapapi.databinding.ActivityMainBinding;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.naver.maps.geometry.LatLng;

import dagger.hilt.android.AndroidEntryPoint;
import javax.inject.Inject;
import java.util.Map;

@AndroidEntryPoint
public class MainActivity extends AppCompatActivity implements DefaultLifecycleObserver {
    private static final String TAG = "MainActivity";
    private static final String[] REQUIRED_PERMISSIONS = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
    };

    private ActivityMainBinding binding;
    private NavController navController;
    private boolean isNavigating = false;
    private boolean isDemoMode = false;
    private int currentDemoPoint = 0;
    private final Handler demoHandler = new Handler(Looper.getMainLooper());

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
        try {
            initializeBasicComponents();
            if (!checkGooglePlayServices()) {
                return;
            }
            setupPermissions();
            setupNavigation();
            setupUI();
            setupObservers();
        } catch (Exception e) {
            Log.e(TAG, "Error during initialization", e);
            showFatalErrorDialog(e);
        }
    }

    private void initializeBasicComponents() {
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
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

    private void setupPermissions() {
        if (!checkPermissions()) {
            requestPermissions();
        } else {
            onPermissionsGranted();
        }
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
        setupNavigationButton();
        setupVoiceGuideButton();
        setupDemoButton();
        setupStatusViews();
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
        if (location == null) return;

        String locationText = getLocationDescription(location);
        binding.locationStatus.setText(locationText);
        binding.locationStatus.announceForAccessibility(locationText);
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
                .provider("DEMO")  // provider를 "DEMO"로 설정
                .build();

        // 새로 추가한 메서드 사용
        locationManager.updateDemoLocation(demoLocation);
        // 환경 설정도 함께 변경
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
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (!isDemoMode) {
            stopLocationTracking();
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
        if (binding != null) {
            binding = null;
        }
    }
}