package com.example.navermapapi;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {

    private GPSManager gpsManager;
    private IndoorMovementManager indoorMovementManager;
    private NaverMapManager naverMapManager;

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1000;
    private static final int INDOOR_MAP_REQUEST_CODE = 2000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        checkAndRequestPermissions();

        Button currentLocationBtn = findViewById(R.id.current_location_btn);
        currentLocationBtn.setOnClickListener(v -> {
            if (checkLocationPermission()) {
                gpsManager.startLocationUpdates();
                naverMapManager.updateMapWithGPSLocation();
            }
        });

        Button switchToIndoorBtn = findViewById(R.id.switch_to_indoor_btn);
        switchToIndoorBtn.setOnClickListener(v -> startIndoorMapActivity());
    }

    private void checkAndRequestPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
        } else {
            initializeApp();
        }
    }

    private boolean checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
            return false;
        }
        return true;
    }

    private void initializeApp() {
        Log.d("MainActivity", "initializeApp 시작");
        try {
            gpsManager = new GPSManager(this);
            naverMapManager = new NaverMapManager(this, gpsManager);
            indoorMovementManager = new IndoorMovementManager(this, naverMapManager);

            gpsManager.setOnIndoorStatusChangeListener(new GPSManager.OnIndoorStatusChangeListener() {
                @Override
                public void onIndoorStatusChanged(boolean isIndoor) {
                    if (isIndoor) {
                        startIndoorMapActivity();
                    }
                }
            });

            gpsManager.startLocationUpdates();
            indoorMovementManager.startIndoorMovementProcess();
        } catch (Exception e) {
            Log.e("MainActivity", "앱 초기화 중 오류 발생", e);
            Toast.makeText(this, "앱 초기화 중 오류가 발생했습니다.", Toast.LENGTH_LONG).show();
        }
        Log.d("MainActivity", "initializeApp 종료");
    }

    private void startIndoorMapActivity() {
        Intent intent = new Intent(this, IndoorMapActivity.class);
        startActivityForResult(intent, INDOOR_MAP_REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == INDOOR_MAP_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                // IndoorMapActivity에서 지도로 전환 요청이 있을 경우
                naverMapManager.updateMapWithGPSLocation();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initializeApp();
            } else {
                Toast.makeText(this, "위치 권한이 필요합니다. 앱을 사용할 수 없습니다.", Toast.LENGTH_LONG).show();
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