package com.example.navermapapi;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int PERMISSION_REQUEST_CODE = 1001;
    private static final int INDOOR_MAP_REQUEST_CODE = 2000;

    private GPSManager gpsManager;
    private NaverMapManager naverMapManager;
    private IndoorMovementManager indoorMovementManager;

    private Button currentLocationBtn;
    private Button switchToIndoorBtn;
    private Button startProjectBBtn;

    private boolean isOnProjectBPage = false;

    private static final String[] REQUIRED_PERMISSIONS = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACTIVITY_RECOGNITION
    };

    private TextView buildingCornerCoordinatesView;

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
        buildingCornerCoordinatesView = findViewById(R.id.building_corner_coordinates);

        currentLocationBtn.setOnClickListener(v -> {
            if (checkLocationPermission()) {
                gpsManager.startLocationUpdates();
                naverMapManager.updateMapWithGPSLocation();
            } else {
                requestLocationPermission();
            }
        });

        switchToIndoorBtn.setOnClickListener(v -> startIndoorMapActivity());

        startProjectBBtn.setOnClickListener(v -> startProjectBActivity());
    }

    private void checkAndRequestPermissions() {
        List<String> permissionsToRequest = new ArrayList<>();
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ActivityCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(permission);
            }
        }

        if (!permissionsToRequest.isEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toArray(new String[0]), PERMISSION_REQUEST_CODE);
        } else {
            initializeApp();
        }
    }

    private boolean checkLocationPermission() {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestLocationPermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
            new AlertDialog.Builder(this)
                    .setTitle("위치 권한 필요")
                    .setMessage("이 앱은 위치 기능을 사용하기 위해 위치 권한이 필요합니다. 권한을 허용해 주시겠습니까?")
                    .setPositiveButton("확인", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            ActivityCompat.requestPermissions(MainActivity.this,
                                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                                    PERMISSION_REQUEST_CODE);
                        }
                    })
                    .setNegativeButton("취소", null)
                    .create()
                    .show();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    PERMISSION_REQUEST_CODE);
        }
    }

    private void initializeApp() {
        Log.d(TAG, "initializeApp 시작");
        try {
            gpsManager = new GPSManager(this);
            naverMapManager = new NaverMapManager(this, gpsManager);
            indoorMovementManager = new IndoorMovementManager(this, naverMapManager);

            gpsManager.setOnIndoorStatusChangeListener((isIndoor, buildingCoordinates) -> {
                if (isIndoor && !isOnProjectBPage) {
                    if (buildingCoordinates != null) {
                        String message = "실내 진입 감지. 건물 좌표: " + buildingCoordinates;
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                        Log.d(TAG, message);

                        // 건물 외곽선 표시
                        naverMapManager.showBuildingOutline(true);
                    }
                } else if (!isIndoor) {
                    Toast.makeText(this, "실외로 나왔습니다.", Toast.LENGTH_SHORT).show();
                    naverMapManager.showBuildingOutline(false);
                }
            });

            if (checkLocationPermission()) {
                gpsManager.startLocationUpdates();
                indoorMovementManager.startIndoorMovementProcess();
            } else {
                Toast.makeText(this, "위치 권한이 없어 일부 기능이 제한됩니다.", Toast.LENGTH_LONG).show();
            }
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
        isOnProjectBPage = true;
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
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,@NonNull int[] grantResults) {
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
                showPermissionDeniedDialog();
            }
        }
    }

    private void showPermissionDeniedDialog() {
        new AlertDialog.Builder(this)
                .setTitle("권한 거부됨")
                .setMessage("앱의 주요 기능을 사용하기 위해서는 권한이 필요합니다. 설정에서 권한을 허용해주세요.")
                .setPositiveButton("설정으로 이동", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        Uri uri = Uri.fromParts("package", getPackageName(), null);
                        intent.setData(uri);
                        startActivity(intent);
                    }
                })
                .setNegativeButton("제한된 기능으로 사용", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Toast.makeText(MainActivity.this, "일부 기능이 제한됩니다.", Toast.LENGTH_SHORT).show();
                        initializeApp();
                    }
                })
                .create()
                .show();
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

    @Override
    protected void onResume() {
        super.onResume();
        isOnProjectBPage = false;
    }

    public void updateBuildingCornerCoordinates(final String coordinatesText) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                buildingCornerCoordinatesView.setText(coordinatesText);
            }
        });
    }
}
