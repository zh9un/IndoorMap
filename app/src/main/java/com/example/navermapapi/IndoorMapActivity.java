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
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class IndoorMapActivity extends AppCompatActivity {

    private TextView tvCurrentLocation;
    private TextView tvFloorInfo;
    private Button btnChangeFloor;
    private ListView lvNearbyPlaces;
    private int currentFloor = 1;
    private IndoorMapView indoorMapView;
    private FrameLayout indoorMapContainer;
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

    private void changeFloor() {
        currentFloor = (currentFloor % 5) + 1; // 1층부터 5층까지 순환
        updateFloorInfo();
        updateMapView();
        Toast.makeText(this, currentFloor + "층으로 이동했습니다.", Toast.LENGTH_SHORT).show();
    }

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

    private void initializeViews() {
        tvCurrentLocation = findViewById(R.id.tvCurrentLocation);
        tvFloorInfo = findViewById(R.id.tvFloorInfo);
        btnChangeFloor = findViewById(R.id.btnChangeFloor);
        indoorMapContainer = findViewById(R.id.indoor_map_container);
    }


    private void setupListeners() {
        btnChangeFloor.setOnClickListener(v -> changeFloor());

        Button switchToMapBtn = findViewById(R.id.switch_to_map_btn);
        switchToMapBtn.setOnClickListener(v -> switchToMap());
    }


    private void updateFloorInfo() {
        tvFloorInfo.setText("현재 층: " + currentFloor + "층");
    }



    private void initializeBluetooth() {
        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Toast.makeText(this, "Bluetooth를 활성화해주세요.", Toast.LENGTH_LONG).show();
            return;
        }

        requestLocationPermission();
    }

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

    private ScanCallback leScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            processBeaconData(result);
        }
    };

    private void processBeaconData(ScanResult result) {
        String deviceName = result.getDevice().getName();
        if (deviceName != null) {
            tvCurrentLocation.setText("현재 위치: " + deviceName + " 근처");
        }
    }

    private void switchToMap() {
        Intent resultIntent = new Intent();
        setResult(RESULT_OK, resultIntent);
        finish();
    }

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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (scanning) {
            bluetoothLeScanner.stopScan(leScanCallback);
        }
    }
    private class IndoorMapView extends View {
        private Paint paint;
        private int[][] mapData;
        private float scaleFactor = 1.0f;

        public IndoorMapView(Context context, int[][] mapData) {
            super(context);
            this.mapData = mapData;
            paint = new Paint();
            calculateScaleFactor();
        }

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
            scaleFactor = Math.min(scaleX, scaleY) * 0.9f; // 90% of the smaller scale
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            calculateScaleFactor();
        }

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