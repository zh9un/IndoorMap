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

    private boolean isOnProjectBPage = false;

    // 필수 권한 목록 정의 - 앱의 기능을 위해 반드시 필요한 권한들을 정의합니다.
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

        // UI 요소 초기화 및 권한 체크/요청 메서드 호출
        initializeUI();
        checkAndRequestPermissions();
    }

    // UI 초기화 메서드: 버튼 초기화 및 클릭 리스너 설정
    // 이 메서드는 사용자 인터페이스 요소를 초기화하고 각 버튼의 동작을 정의합니다.
    private void initializeUI() {
        currentLocationBtn = findViewById(R.id.current_location_btn);
        switchToIndoorBtn = findViewById(R.id.switch_to_indoor_btn);
        startProjectBBtn = findViewById(R.id.start_project_b_btn);

        // 현재 위치 버튼 클릭 시 위치 업데이트 시작
        currentLocationBtn.setOnClickListener(v -> {
            // 위치 권한이 있는지 확인한 후 위치 업데이트 시작
            if (checkLocationPermission()) {
                gpsManager.startLocationUpdates();
                naverMapManager.updateMapWithGPSLocation();
            }
        });

        // 실내 지도 액티비티로 전환
        switchToIndoorBtn.setOnClickListener(v -> startIndoorMapActivity());

        // Project B 액티비티로 전환
        startProjectBBtn.setOnClickListener(v -> startProjectBActivity());
    }

    // 권한 요청 메서드: 필요한 권한을 체크하고 요청
    // 이 메서드는 앱의 정상적인 동작을 위해 필요한 권한들이 부여되었는지 확인하고, 부여되지 않았다면 요청합니다.
    private void checkAndRequestPermissions() {
        List<String> permissionsToRequest = new ArrayList<>();
        for (String permission : REQUIRED_PERMISSIONS) {
            // Android 버전에 따라 필요한 권한을 체크합니다.
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

        // 요청해야 할 권한이 있을 경우 권한 요청, 그렇지 않으면 앱 초기화
        if (!permissionsToRequest.isEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toArray(new String[0]), PERMISSION_REQUEST_CODE);
        } else {
            initializeApp();
        }
    }

    // 위치 권한 체크 메서드
    // 사용자가 위치 권한을 허용했는지 확인하고, 허용되지 않았다면 권한 요청을 수행합니다.
    private boolean checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSION_REQUEST_CODE);
            return false;
        }
        return true;
    }

    // 앱 초기화 메서드: GPSManager, NaverMapManager 등 초기화 및 위치 업데이트 시작
    // 이 메서드는 GPS, 네이버 지도, 실내 이동 관련 컴포넌트들을 초기화하고 위치 업데이트를 시작합니다.
    private void initializeApp() {
        Log.d(TAG, "initializeApp 시작");
        try {
            // GPS, 네이버 지도, 실내 이동 관리 매니저를 초기화합니다.
            gpsManager = new GPSManager(this);
            naverMapManager = new NaverMapManager(this, gpsManager);
            indoorMovementManager = new IndoorMovementManager(this, naverMapManager);

            // GPSManager에서 실내/외 상태 변경 시 호출되는 리스너 설정
            gpsManager.setOnIndoorStatusChangeListener(isIndoor -> {
                // 사용자가 실내로 감지되었을 때 Project B 페이지가 아닌 경우 실내 지도 액티비티 시작
                if (isIndoor && !isOnProjectBPage) {
                    startIndoorMapActivity();
                }
            });

            // GPS 위치 업데이트 및 실내 이동 프로세스 시작
            gpsManager.startLocationUpdates();
            indoorMovementManager.startIndoorMovementProcess();
        } catch (Exception e) {
            Log.e(TAG, "앱 초기화 중 오류 발생", e);
            Toast.makeText(this, "앱 초기화 중 오류가 발생했습니다.", Toast.LENGTH_LONG).show();
        }
        Log.d(TAG, "initializeApp 종료");
    }

    // 실내 지도 액티비티 시작 메서드
    // 사용자가 실내로 감지되었을 때 IndoorMapActivity를 시작합니다.
    private void startIndoorMapActivity() {
        Intent intent = new Intent(this, IndoorMapActivity.class);
        startActivityForResult(intent, INDOOR_MAP_REQUEST_CODE);
    }

    // Project B 액티비티 시작 메서드
    // 사용자가 Project B의 고급 실내 기능을 사용하기 위해 ProjectBActivity로 전환합니다.
    private void startProjectBActivity() {
        isOnProjectBPage = true;  // Project B 페이지로 전환되었음을 표시
        Intent intent = new Intent(this, ProjectBActivity.class);
        startActivity(intent);
    }

    // 액티비티 결과 처리 메서드
    // 다른 액티비티에서 돌아왔을 때 결과를 처리합니다.
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == INDOOR_MAP_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                // 실내 지도 액티비티에서 정상적으로 돌아왔을 때 GPS 위치를 갱신합니다.
                naverMapManager.updateMapWithGPSLocation();
            }
        }
    }

    // 권한 요청 결과 처리 메서드
    // 사용자가 권한 요청에 대해 응답한 결과를 처리합니다.
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
            // 모든 권한이 허용되었으면 앱을 초기화하고, 그렇지 않으면 앱 종료
            if (allPermissionsGranted) {
                initializeApp();
            } else {
                Toast.makeText(this, "필요한 권한이 거부되었습니다. 앱을 종료합니다.", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    // 액티비티 종료 시 호출되는 메서드: GPS 및 실내 이동 프로세스 중지
    // 이 메서드는 액티비티가 종료될 때 호출되며, 위치 업데이트 및 실내 이동 감지를 중지합니다.
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 실내 이동 프로세스를 중지하여 자원 낭비를 방지합니다.
        if (indoorMovementManager != null) {
            indoorMovementManager.stopIndoorMovementProcess();
        }
        // GPS 위치 업데이트를 중지하여 배터리 소모를 줄입니다.
        if (gpsManager != null) {
            gpsManager.stopLocationUpdates();
        }
    }

    // 액티비티 재개 시 호출되는 메서드: Project B 페이지 상태 리셋
    // 사용자가 다른 액티비티에서 돌아왔을 때 Project B 상태를 초기화합니다.
    @Override
    protected void onResume() {
        super.onResume();
        // Project B 페이지 상태를 초기화하여 실내 지도 전환이 가능하도록 설정
        isOnProjectBPage = false;
    }
}