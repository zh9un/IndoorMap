package com.example.navermapapi;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class IndoorMapActivity extends AppCompatActivity {

    private TextView tvCurrentLocation;
    private TextView tvFloorInfo;
    private Button btnChangeFloorUp;
    private Button btnChangeFloorDown;
    private FrameLayout indoorMapContainer;
    private int currentFloor = 1;
    private IndoorMapView indoorMapView;
    private int[][] floorPlan3;  // 3층 도면
    private int[][] floorPlan4;  // 4층 도면

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;
    private boolean scanning = false;
    private Handler handler = new Handler();

    private static final long SCAN_PERIOD = 10000; // 10 seconds
    private static final int REQUEST_ENABLE_BT = 1;
    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 1;

    private int[][] floorPlan;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_indoor_map);

        // 뷰 초기화 및 Bluetooth 설정, 리스너 설정 등을 수행합니다.
        initializeViews();
        setupListeners();
        updateFloorInfo();
        initializeBluetooth();

        // 3층 도면 데이터 로드
        floorPlan3 = CSVReader.readCSV(this, "3rdFloorCsv.csv");
        // 4층 도면 데이터 로드 (나중에 추가)
        // floorPlan4 = CSVReader.readCSV(this, "floor_plan_4.csv");

        updateMapView();
    }

    // 뷰 초기화 메서드 - UI 요소들을 초기화합니다.
    private void initializeViews() {
        tvCurrentLocation = findViewById(R.id.tvCurrentLocation);
        tvFloorInfo = findViewById(R.id.tvFloorInfo);
        btnChangeFloorUp = findViewById(R.id.btnChangeFloorUp);
        btnChangeFloorDown = findViewById(R.id.btnChangeFloorDown);
        indoorMapContainer = findViewById(R.id.indoor_map_container);
    }

    // 리스너 설정 메서드 - 버튼 클릭 리스너를 설정합니다.
    private void setupListeners() {
        btnChangeFloorUp.setOnClickListener(v -> changeFloor(true));
        btnChangeFloorDown.setOnClickListener(v -> changeFloor(false));

        Button switchToMapBtn = findViewById(R.id.switch_to_map_btn);
        switchToMapBtn.setOnClickListener(v -> switchToMap());
    }

    // 층 변경 메서드 - 사용자가 층을 변경할 때 호출됩니다.
    private void changeFloor(boolean up) {
        if (up) {
            currentFloor = Math.min(currentFloor + 1, 5); // 최대 5층까지 이동 가능
        } else {
            currentFloor = Math.max(currentFloor - 1, 1); // 최소 1층까지 이동 가능
        }
        updateFloorInfo();
        updateMapView();
        Toast.makeText(this, currentFloor + "층으로 이동했습니다.", Toast.LENGTH_SHORT).show();
    }

    // 층 정보 업데이트 메서드 - 현재 층 정보를 화면에 표시합니다.
    private void updateFloorInfo() {
        tvFloorInfo.setText("현재 층: " + currentFloor + "층");
    }

    // 지도 뷰 업데이트 메서드 - 층 변경 시 실내 지도 화면을 업데이트합니다.
    private void updateMapView() {
        indoorMapContainer.removeAllViews(); // 기존 뷰 제거

        if (currentFloor == 3 && floorPlan3 != null) {
            indoorMapView = new IndoorMapView(this, floorPlan3);
            indoorMapContainer.addView(indoorMapView);
            indoorMapContainer.setVisibility(View.VISIBLE);
        } else if (currentFloor == 4 && floorPlan4 != null) {
            // 4층 도면 추가 시 구현
            // indoorMapView = new IndoorMapView(this, floorPlan4);
            // indoorMapContainer.addView(indoorMapView);
            // indoorMapContainer.setVisibility(View.VISIBLE);
        } else {
            indoorMapContainer.setVisibility(View.GONE);
            Toast.makeText(this, currentFloor + "층 도면이 없습니다.", Toast.LENGTH_SHORT).show();
        }
    }

    // Bluetooth 초기화 메서드 - Bluetooth 어댑터를 초기화하고 권한을 요청합니다.
    private void initializeBluetooth() {
        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Toast.makeText(this, "Bluetooth를 활성화해주세요.", Toast.LENGTH_LONG).show();
            return;
        }

        requestLocationPermission();
    }

    // 위치 권한 요청 메서드 - Bluetooth 스캔을 위해 위치 권한을 요청합니다.
    private void requestLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                        PERMISSION_REQUEST_COARSE_LOCATION);
            } else {
                startBeaconScan();
            }
        } else {
            startBeaconScan();
        }
    }

    // 비콘 스캔 시작 메서드 - Bluetooth LE 스캔을 시작합니다.
    private void startBeaconScan() {
        if (!scanning) {
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    scanning = false;
                    bluetoothLeScanner.stopScan(leScanCallback);
                }
            }, SCAN_PERIOD);

            scanning = true;
            bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
            bluetoothLeScanner.startScan(leScanCallback);
        } else {
            scanning = false;
            bluetoothLeScanner.stopScan(leScanCallback);
        }
    }

    // Bluetooth LE 스캔 콜백 - 스캔 결과를 처리합니다.
    private ScanCallback leScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            processBeaconData(result);
        }
    };

    // 비콘 데이터 처리 메서드 - 비콘 스캔 결과를 처리하여 현재 위치를 업데이트합니다.
    private void processBeaconData(ScanResult result) {
        String deviceName = result.getDevice().getName();
        if (deviceName != null) {
            tvCurrentLocation.setText("현재 위치: " + deviceName + " 근처");
        }
    }

    // 지도 화면으로 전환 메서드 - 실내 지도에서 메인 지도 화면으로 돌아갑니다.
    private void switchToMap() {
        Intent resultIntent = new Intent();
        setResult(RESULT_OK, resultIntent);
        finish();
    }

    // 권한 요청 결과 처리 메서드 - 사용자가 권한 요청에 응답한 결과를 처리합니다.
    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case PERMISSION_REQUEST_COARSE_LOCATION: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startBeaconScan();
                } else {
                    Toast.makeText(this, "위치 권한이 없어 비콘 스캔을 할 수 없습니다.", Toast.LENGTH_LONG).show();
                }
                return;
            }
        }
    }

    // 액티비티 종료 시 호출되는 메서드 - Bluetooth 스캔을 중지합니다.
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (scanning) {
            bluetoothLeScanner.stopScan(leScanCallback);
        }
    }

    // IndoorMapView 클래스 - 실내 지도를 그리기 위한 커스텀 뷰입니다.
    private class IndoorMapView extends View {
        private Paint paint;
        private int[][] mapData;
        private float scaleFactor = 1.0f;

        // IndoorMapView 생성자 - 지도 데이터를 받아 초기화합니다.
        public IndoorMapView(Context context, int[][] mapData) {
            super(context);
            this.mapData = mapData;
            paint = new Paint();
            calculateScaleFactor();
        }

        // 스케일 계산 메서드 - 화면 크기에 맞게 지도의 스케일을 계산합니다.
        private void calculateScaleFactor() {
            if (mapData == null || mapData.length == 0) return;
            int mapWidth = mapData[0].length;
            int mapHeight = mapData.length;
            int viewWidth = getWidth();
            int viewHeight = getHeight();
            if (viewWidth == 0 || viewHeight == 0) {
                viewWidth = getResources().getDisplayMetrics().widthPixels;
                viewHeight = getResources().getDisplayMetrics().heightPixels;
            }
            float scaleX = (float) viewWidth / mapWidth;
            float scaleY = (float) viewHeight / mapHeight;
            scaleFactor = Math.min(scaleX, scaleY) * 0.9f; // 90%의 크기로 설정
        }

        // 뷰 크기 변경 시 호출되는 메서드 - 스케일을 다시 계산합니다.
        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            calculateScaleFactor();
        }

        // 지도 그리기 메서드 - 지도 데이터를 이용해 화면에 그립니다.
        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (mapData == null) return;

            canvas.save();
            canvas.scale(scaleFactor, scaleFactor);

            for (int y = 0; y < mapData.length; y++) {
                for (int x = 0; x < mapData[y].length; x++) {
                    float left = x;
                    float top = y;
                    float right = left + 1;
                    float bottom = top + 1;

                    switch (mapData[y][x]) {
                        case 0: // 실내공간
                            paint.setColor(Color.WHITE);
                            break;
                        case 1: // 벽
                            paint.setColor(Color.BLACK);
                            break;
                        case 2: // 루트
                            paint.setColor(Color.GREEN);
                            break;
                        case 3: // 비콘
                            paint.setColor(Color.BLUE);
                            break;
                        default:
                            paint.setColor(Color.GRAY);
                            break;
                    }
                    canvas.drawRect(left, top, right, bottom, paint);
                }
            }

            canvas.restore();
        }
    }
}