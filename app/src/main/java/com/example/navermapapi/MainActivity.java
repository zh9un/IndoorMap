package com.example.navermapapi;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int PERMISSION_REQUEST_CODE = 1001;
    private static final int INDOOR_MAP_REQUEST_CODE = 2000;

    private GPSManager gpsManager;
    private IndoorMovementManager indoorMovementManager;
    private NaverMapManager naverMapManager;

    private Button currentLocationBtn;
    private Button switchToIndoorBtn;
    private Button startProjectBBtn;

    private static final String[] REQUIRED_PERMISSIONS = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACTIVITY_RECOGNITION
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeUI();
        checkAndRequestPermissions();
    }

    private void initializeUI() {
        currentLocationBtn = findViewById(R.id.current_location_btn);
        switchToIndoorBtn = findViewById(R.id.switch_to_indoor_btn);
        startProjectBBtn = findViewById(R.id.start_project_b_btn);

        currentLocationBtn.setOnClickListener(v -> {
            if (checkLocationPermission()) {
                gpsManager.startLocationUpdates();
                naverMapManager.updateMapWithGPSLocation();
            }
        });

        switchToIndoorBtn.setOnClickListener(v -> startIndoorMapActivity());

        startProjectBBtn.setOnClickListener(v -> startProjectBActivity());
    }

    private void checkAndRequestPermissions() {
        List<String> permissionsToRequest = new ArrayList<>();
        for (String permission : REQUIRED_PERMISSIONS) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    (permission.equals(Manifest.permission.BLUETOOTH_SCAN) ||
                            permission.equals(Manifest.permission.BLUETOOTH_CONNECT))) {
                if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                    permissionsToRequest.add(permission);
                }
            } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S &&
                    (permission.equals(Manifest.permission.BLUETOOTH) ||
                            permission.equals(Manifest.permission.BLUETOOTH_ADMIN))) {
                if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                    permissionsToRequest.add(permission);
                }
            } else if (!permission.equals(Manifest.permission.BLUETOOTH_SCAN) &&
                    !permission.equals(Manifest.permission.BLUETOOTH_CONNECT) &&
                    !permission.equals(Manifest.permission.BLUETOOTH) &&
                    !permission.equals(Manifest.permission.BLUETOOTH_ADMIN)) {
                if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                    permissionsToRequest.add(permission);
                }
            }
        }

        if (!permissionsToRequest.isEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toArray(new String[0]), PERMISSION_REQUEST_CODE);
        } else {
            initializeApp();
        }
    }

    private boolean checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSION_REQUEST_CODE);
            return false;
        }
        return true;
    }

    private void initializeApp() {
        Log.d(TAG, "initializeApp 시작");
        try {
            gpsManager = new GPSManager(this);
            naverMapManager = new NaverMapManager(this, gpsManager);
            indoorMovementManager = new IndoorMovementManager(this, naverMapManager);

            gpsManager.setOnIndoorStatusChangeListener(isIndoor -> {
                if (isIndoor) {
                    startIndoorMapActivity();
                }
            });

            gpsManager.startLocationUpdates();
            indoorMovementManager.startIndoorMovementProcess();
        } catch (Exception e) {
            Log.e(TAG, "앱 초기화 중 오류 발생", e);
            Toast.makeText(this, "앱 초기화 중 오류가 발생했습니다.", Toast.LENGTH_LONG).show();
        }
        Log.d(TAG, "initializeApp 종료");
    }

    private void startIndoorMapActivity() {
        Intent intent = new Intent(this, IndoorMapActivity.class);
        startActivityForResult(intent, INDOOR_MAP_REQUEST_CODE);
    }

    private void startProjectBActivity() {
        Intent intent = new Intent(this, ProjectBActivity.class);
        startActivity(intent);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == INDOOR_MAP_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                naverMapManager.updateMapWithGPSLocation();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allPermissionsGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allPermissionsGranted = false;
                    break;
                }
            }
            if (allPermissionsGranted) {
                initializeApp();
            } else {
                Toast.makeText(this, "필요한 권한이 거부되었습니다. 앱을 종료합니다.", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (indoorMovementManager != null) {
            indoorMovementManager.stopIndoorMovementProcess();
        }
        if (gpsManager != null) {
            gpsManager.stopLocationUpdates();
        }
    }
}