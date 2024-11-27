package com.example.navermapapi.appModule.main;

import android.Manifest;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import java.util.Map;
import android.content.pm.PackageManager;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

import com.example.navermapapi.R;
import com.example.navermapapi.appModule.accessibility.VoiceGuideManager;
import com.example.navermapapi.appModule.location.manager.LocationIntegrationManager;
import com.example.navermapapi.coreModule.api.environment.model.EnvironmentType;
import com.example.navermapapi.coreModule.api.location.model.LocationData;
import com.example.navermapapi.databinding.ActivityMainBinding;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;

import dagger.hilt.android.AndroidEntryPoint;
import javax.inject.Inject;

@AndroidEntryPoint
public class MainActivity extends AppCompatActivity implements DefaultLifecycleObserver {
    private static final String TAG = "MainActivity";
    private static final int PERMISSION_REQUEST_CODE = 1000;

    private static final String[] REQUIRED_PERMISSIONS = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
    };

    private ActivityMainBinding binding;
    private NavController navController;
    private boolean isNavigating = false;

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
                apiAvailability.getErrorDialog(this, resultCode, 9000)
                        .show();
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
        setupNavigationCallbacks();
    }

    private void setupNavigationCallbacks() {
        navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
            isNavigating = false;
            Log.d(TAG, "Navigation changed to: " + destination.getLabel());
        });
    }

    private void setupUI() {
        setupVoiceGuideButton();
        setupStatusInfo();
    }

    private void setupVoiceGuideButton() {
        binding.voiceGuideButton.setOnClickListener(v -> {
            LocationData currentLocation = locationManager.getCurrentLocation().getValue();
            EnvironmentType environment = locationManager.getCurrentEnvironment().getValue();
            if (currentLocation != null && environment != null) {
                voiceGuideManager.announceLocation(currentLocation, environment);
            }
        });
    }

    private void setupStatusInfo() {
        binding.environmentStatus.setContentDescription(
                getString(R.string.environment_status_description));
        binding.locationStatus.setContentDescription(
                getString(R.string.location_status_description));
    }

    private void setupObservers() {
        locationManager.getCurrentLocation().observe(this, this::updateLocationUI);
        locationManager.getCurrentEnvironment().observe(this, this::handleEnvironmentUpdate);
    }

    private void updateLocationUI(LocationData location) {
        if (location == null) return;

        try {
            String locationText = String.format(
                    getString(R.string.location_format),
                    location.getLatitude(),
                    location.getLongitude()
            );
            binding.locationStatus.setText(locationText);
            binding.locationStatus.setContentDescription(locationText);

        } catch (Exception e) {
            Log.e(TAG, "Error updating location UI", e);
        }
    }

    private void handleEnvironmentUpdate(EnvironmentType environment) {
        if (environment == null || isNavigating) return;

        try {
            isNavigating = true;
            updateEnvironmentUI(environment);

            switch (environment) {
                case INDOOR:
                    navigateToIndoor();
                    break;
                case OUTDOOR:
                    navigateToOutdoor();
                    break;
                case TRANSITION:
                    isNavigating = false;
                    break;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling environment update", e);
            isNavigating = false;
        }
    }

    private void updateEnvironmentUI(EnvironmentType environment) {
        String environmentText = getEnvironmentDescription(environment);
        binding.environmentStatus.setText(environmentText);
        binding.environmentStatus.setContentDescription(environmentText);
    }

    private void navigateToIndoor() {
        if (navController.getCurrentDestination().getId() != R.id.indoorMapFragment) {
            navController.navigate(R.id.action_outdoorMap_to_indoorMap);
        }
    }

    private void navigateToOutdoor() {
        if (navController.getCurrentDestination().getId() != R.id.outdoorMapFragment) {
            navController.navigate(R.id.action_indoorMap_to_outdoorMap);
        }
    }

    private String getEnvironmentDescription(EnvironmentType environment) {
        switch (environment) {
            case INDOOR:
                return getString(R.string.environment_indoor);
            case OUTDOOR:
                return getString(R.string.environment_outdoor);
            case TRANSITION:
                return getString(R.string.environment_transition);
            default:
                return getString(R.string.environment_unknown);
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
        startLocationTracking();
    }

    @Override
    protected void onStop() {
        super.onStop();
        stopLocationTracking();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cleanup();
    }

    private void cleanup() {
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

    public void announceVoiceGuide(String message) {
        if (voiceGuideManager != null) {
            voiceGuideManager.announce(message);
        } else {
            Log.w(TAG, "VoiceGuideManager is not initialized.");
        }
    }


}