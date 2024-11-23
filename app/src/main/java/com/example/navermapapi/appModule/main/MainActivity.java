package com.example.navermapapi.appModule.main;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.NavDestination;
import androidx.navigation.fragment.NavHostFragment;
import androidx.preference.PreferenceManager;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;

import com.example.navermapapi.R;
import com.example.navermapapi.appModule.accessibility.VoiceGuideManager;
import com.example.navermapapi.coreModule.api.environment.model.EnvironmentType;
import com.example.navermapapi.coreModule.api.location.model.LocationData;
import com.example.navermapapi.databinding.ActivityMainBinding;

import dagger.hilt.android.AndroidEntryPoint;
import javax.inject.Inject;
import java.util.Map;

@AndroidEntryPoint
public class MainActivity extends AppCompatActivity implements DefaultLifecycleObserver {
    private static final String TAG = "MainActivity";
    private static final int PERMISSION_REQUEST_CODE = 1000;
    private static final int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;
    private static final String PREF_KEY_PERMISSION_REQUEST_COUNT = "permission_request_count";
    private static final int MAX_PERMISSION_REQUESTS = 2;

    private static final String[] REQUIRED_PERMISSIONS = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.HIGH_SAMPLING_RATE_SENSORS,
            Manifest.permission.ACTIVITY_RECOGNITION
    };

    private ActivityMainBinding binding;
    private MainViewModel viewModel;
    private NavController navController;
    private SharedPreferences preferences;
    private boolean isNavigating = false;

    @Inject
    VoiceGuideManager voiceGuideManager;

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),
                    this::handlePermissionResult);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            if (!checkGooglePlayServices()) {
                return;
            }
            initializeBasicComponents();
            initializeNavigation();
            setupPermissionHandling();
            setupUI();
            setupObservers();
        } catch (Exception e) {
            Log.e(TAG, "Error during initialization", e);
            showFatalErrorDialog(e);
        }
    }

    private boolean checkGooglePlayServices() {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        int resultCode = apiAvailability.isGooglePlayServicesAvailable(this);
        if (resultCode != ConnectionResult.SUCCESS) {
            if (apiAvailability.isUserResolvableError(resultCode)) {
                apiAvailability.getErrorDialog(this, resultCode,
                                PLAY_SERVICES_RESOLUTION_REQUEST)
                        .show();
            } else {
                new AlertDialog.Builder(this)
                        .setTitle(R.string.error_title)
                        .setMessage("This device does not support Google Play Services")
                        .setPositiveButton(android.R.string.ok, (dialog, which) -> finish())
                        .setCancelable(false)
                        .show();
            }
            return false;
        }
        return true;
    }

    private void initializeBasicComponents() {
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        viewModel = new ViewModelProvider(this).get(MainViewModel.class);
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        getLifecycle().addObserver(this);
    }

    private void initializeNavigation() {
        try {
            NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                    .findFragmentById(R.id.nav_host_fragment);
            if (navHostFragment == null) {
                throw new IllegalStateException("NavHostFragment not found");
            }
            navController = navHostFragment.getNavController();
            setupNavigationCallbacks();
        } catch (Exception e) {
            Log.e(TAG, "Error setting up navigation", e);
            throw e;
        }
    }

    private void setupNavigationCallbacks() {
        navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
            isNavigating = false;
            logNavigationChange(destination);
        });
    }

    private void setupPermissionHandling() {
        if (checkPermissions()) {
            onPermissionsGranted();
        } else {
            int requestCount = preferences.getInt(PREF_KEY_PERMISSION_REQUEST_COUNT, 0);
            if (requestCount >= MAX_PERMISSION_REQUESTS) {
                showPermissionsPermanentlyDeniedDialog();
            } else { requestPermissions(requestCount + 1);
            }
        }
    }

    private void setupUI() {
        binding.voiceGuideButton.setOnClickListener(v -> {
            LocationData currentLocation = viewModel.getCurrentLocation().getValue();
            EnvironmentType environment = viewModel.getCurrentEnvironment().getValue();
            if (currentLocation != null && environment != null) {
                voiceGuideManager.announceLocation(currentLocation, environment);
            }
        });

        // TalkBack 접근성 설정
        binding.voiceGuideButton.setContentDescription(
                getString(R.string.announce_current_location)
        );
    }

    private void setupObservers() {
        viewModel.getCurrentLocation().observe(this, this::updateLocationUI);
        viewModel.getCurrentEnvironment().observe(this, this::handleEnvironmentUpdate);
        viewModel.getErrorState().observe(this, this::handleError);
    }

    private void updateLocationUI(@Nullable LocationData location) {
        if (location == null) return;
        try {
            binding.locationStatus.setText(getString(R.string.location_format,
                    location.getLatitude(), location.getLongitude()));
            binding.locationStatus.setContentDescription(
                    String.format("현재 위치는 위도 %.2f도, 경도 %.2f도 입니다.",
                            location.getLatitude(), location.getLongitude())
            );
        } catch (Exception e) {
            Log.e(TAG, "Error updating location UI", e);
        }
    }

    private void handleEnvironmentUpdate(@Nullable EnvironmentType environment) {
        if (environment == null) return;
        updateEnvironmentUI(environment);
        handleEnvironmentChange(environment);
    }

    private void updateEnvironmentUI(EnvironmentType environment) {
        binding.environmentStatus.setText(environment.getDescription());
        binding.environmentStatus.setContentDescription(
                String.format("현재 환경은 %s입니다.", environment.getDescription())
        );
    }

    private void handleEnvironmentChange(EnvironmentType environment) {
        if (isNavigating) return;

        try {
            isNavigating = true;
            switch (environment) {
                case INDOOR:
                    navController.navigate(R.id.action_outdoorMap_to_indoorMap);
                    break;
                case OUTDOOR:
                    navController.navigate(R.id.action_indoorMap_to_outdoorMap);
                    break;
                case TRANSITION:
                    // 전환 중에는 네비게이션 변경하지 않음
                    isNavigating = false;
                    break;
            }
        } catch (Exception e) {
            Log.e(TAG, "Navigation error", e);
            isNavigating = false;
            handleError(getString(R.string.navigation_error));
        }
    }

    private void handleError(String error) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.error_title)
                .setMessage(error)
                .setPositiveButton(android.R.string.ok, null)
                .show();
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

    private void requestPermissions(int requestCount) {
        preferences.edit()
                .putInt(PREF_KEY_PERMISSION_REQUEST_COUNT, requestCount)
                .apply();
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
            int requestCount = preferences.getInt(PREF_KEY_PERMISSION_REQUEST_COUNT, 0);
            if (requestCount >= MAX_PERMISSION_REQUESTS) {
                showPermissionsPermanentlyDeniedDialog();
            } else {
                showPermissionDeniedDialog();
            }
        }
    }

    private void onPermissionsGranted() {
        try {
            viewModel.initialize();
            binding.voiceGuideButton.setEnabled(true);
        } catch (Exception e) {
            Log.e(TAG, "Error initializing after permissions granted", e);
            handleError(getString(R.string.initialization_error));
        }
    }

    private void showPermissionDeniedDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.location_permission_required)
                .setMessage(R.string.permission_required_message)
                .setPositiveButton(R.string.try_again, (dialog, which) -> {
                    int requestCount = preferences.getInt(PREF_KEY_PERMISSION_REQUEST_COUNT, 0);
                    requestPermissions(requestCount + 1);
                })
                .setNegativeButton(R.string.exit, (dialog, which) -> finish())
                .setCancelable(false)
                .show();
    }

    private void showPermissionsPermanentlyDeniedDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.location_permission_required)
                .setMessage(R.string.permissions_permanently_denied_message)
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

    private void showFatalErrorDialog(Exception e) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.fatal_error)
                .setMessage(getString(R.string.fatal_error_message, e.getMessage()))
                .setPositiveButton(R.string.exit, (dialog, which) -> finishAndRemoveTask())
                .setCancelable(false)
                .show();
    }

    private void logNavigationChange(NavDestination destination) {
        Log.d(TAG, "Navigation changed to: " + destination.getLabel());
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PLAY_SERVICES_RESOLUTION_REQUEST) {
            if (resultCode == RESULT_OK) {
                initializeBasicComponents();
            } else {
                showFatalErrorDialog(
                        new Exception("Google Play Services is required but not available")
                );
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (checkPermissions()) {
            viewModel.startTracking();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        viewModel.stopTracking();
    }

    @Override
    public void onDestroy(@NonNull LifecycleOwner owner) {
        cleanupResources();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cleanupResources();
    }

    private void cleanupResources() {
        if (voiceGuideManager != null) {
            voiceGuideManager.destroy();
        }
        if (viewModel != null) {
            viewModel.stopTracking();
        }
        if (binding != null) {
            binding = null;
        }
        getLifecycle().removeObserver(this);
    }
}