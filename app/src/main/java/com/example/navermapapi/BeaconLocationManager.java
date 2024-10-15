package com.example.navermapapi;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.util.Log;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.Manifest;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.Comparator;

public class BeaconLocationManager {
    private static final String TAG = "BeaconLocationManager";
    private Context context;
    private BluetoothLeScanner bluetoothLeScanner;
    private static Map<String, Beacon> beacons;
    private LocationCallback locationCallback;
    private static final int MIN_BEACONS_FOR_LOCATION = 3;
    private static final long SCAN_PERIOD = 10000; // 10 seconds
    private static final long SCAN_INTERVAL = 60000; // 1 minute
    private boolean isScanning = false;
    private long lastScanTime = 0;

    public interface LocationCallback {
        void onLocationEstimated(double x, double y);
        void onLocationEstimationFailed();
    }

    // BeaconLocationManager 클래스 생성자 - 비콘 정보를 로드하고 블루투스 스캐너를 초기화합니다.
    public BeaconLocationManager(Context context, LocationCallback callback) {
        this.context = context;
        this.locationCallback = callback;
        this.beacons = new HashMap<>();

        BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();
        bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();

        loadBeaconsFromFile(); // 비콘 정보를 파일에서 로드합니다.
        Log.d(TAG, "BeaconLocationManager initialized with " + beacons.size() + " beacons");
    }

    // 블루투스 권한을 확인하고 요청하는 메서드입니다.
    private void checkBluetoothPermissions() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions((Activity) context,
                    new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT}, 1);
            Log.d(TAG, "Requesting Bluetooth permissions");
        } else {
            Log.d(TAG, "Bluetooth permissions already granted");
        }
    }

    // 필요한 권한이 모두 있는지 확인하는 메서드입니다.
    private boolean hasRequiredPermissions() {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }

    // 비콘 스캔을 중지하는 메서드입니다.
    @SuppressLint("MissingPermission")
    public void stopBeaconScan() {
        if (bluetoothLeScanner != null && isScanning) {
            bluetoothLeScanner.stopScan(leScanCallback);
            isScanning = false;
            lastScanTime = System.currentTimeMillis();
            Log.d(TAG, "Beacon scanning stopped");
        }
    }

    // 비콘 스캔을 시작하는 메서드입니다.
    public void startBeaconScan() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "BLUETOOTH_SCAN permission not granted");
            locationCallback.onLocationEstimationFailed();
            return;
        }

        if (bluetoothLeScanner != null && !isScanning) {
            isScanning = true;

            // 스캔 필터 설정 - 특정 비콘의 MAC 주소를 필터링하여 스캔합니다.
            List<ScanFilter> filters = new ArrayList<>();
            for (Beacon beacon : beacons.values()) {
                if (isValidMacAddress(beacon.getMacAddress())) {
                    ScanFilter filter = new ScanFilter.Builder()
                            .setDeviceAddress(beacon.getMacAddress())
                            .build();
                    filters.add(filter);
                } else {
                    Log.w(TAG, "Invalid MAC address: " + beacon.getMacAddress());
                }
            }

            // 스캔 설정 - 저지연 모드로 설정하여 빠르게 스캔합니다.
            ScanSettings settings = new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build();

            bluetoothLeScanner.startScan(filters, settings, leScanCallback);
            Log.d(TAG, "Beacon scanning started with " + filters.size() + " filters");

            // 타임아웃 핸들러 설정 - 일정 시간 후 스캔이 중지되지 않으면 실패로 처리합니다.
            new android.os.Handler().postDelayed(() -> {
                if (isScanning) {
                    stopBeaconScan();
                    Log.e(TAG, "Failed to set initial location within timeout");
                    locationCallback.onLocationEstimationFailed();
                }
            }, 30000);
        } else {
            Log.e(TAG, "BluetoothLeScanner is null or already scanning");
            locationCallback.onLocationEstimationFailed();
        }
    }

    // MAC 주소가 유효한 형식인지 확인하는 메서드입니다.
    private boolean isValidMacAddress(String address) {
        if (address == null) return false;
        return address.matches("^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$");
    }

    // 비콘 스캔 콜백 - 스캔된 결과를 처리하는 메서드입니다.
    private final ScanCallback leScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            String deviceAddress = result.getDevice().getAddress().toUpperCase();
            Log.d(TAG, "Scanned device: " + deviceAddress);

            Beacon beacon = beacons.get(deviceAddress);
            if (beacon != null) {
                int rssi = result.getRssi();
                beacon.setRssi(rssi);
                Log.d(TAG, "Beacon detected: " + deviceAddress + " RSSI: " + rssi);

                Map<String, Double> distances = getBeaconDistances();
                estimateLocation(distances); // 감지된 비콘 정보를 사용해 위치를 추정합니다.
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.e(TAG, "Beacon scan failed with error code: " + errorCode);
        }
    };

    // 감지된 비콘의 개수를 반환하는 메서드입니다.
    private int getDetectedBeaconCount() {
        return (int) beacons.values().stream().filter(beacon -> beacon.getRssi() != 0).count();
    }

    // 각 비콘으로부터 거리를 계산하여 반환하는 메서드입니다.
    private Map<String, Double> getBeaconDistances() {
        Map<String, Double> distances = new HashMap<>();
        for (Map.Entry<String, Beacon> entry : beacons.entrySet()) {
            Beacon beacon = entry.getValue();
            if (beacon.getRssi() != 0) {
                double distance = calculateDistance(beacon.getRssi(), beacon.getTxPower());
                distances.put(beacon.getId(), distance);
                Log.d(TAG, "Calculated distance for beacon " + beacon.getId() + ": " + distance + " meters");
            }
        }
        return distances;
    }

    // RSSI 값을 사용하여 비콘과의 거리를 계산하는 메서드입니다.
    private double calculateDistance(double rssi, int txPower) {
        if (rssi == 0) {
            return -1.0;
        }

        double ratio = rssi * 1.0 / txPower;
        if (ratio < 1.0) {
            return Math.pow(ratio, 10);
        } else {
            return (0.89976) * Math.pow(ratio, 7.7095) + 0.111;
        }
    }
        // 감지된 비콘 정보를 바탕으로 위치를 추정하는 메서드입니다.
        private void estimateLocation(Map<String, Double> distances) {
            List<Beacon> detectedBeacons = getDetectedBeacons();
            Log.d(TAG, "Detected beacons: " + detectedBeacons.size());
            for (Beacon beacon : detectedBeacons) {
                Log.d(TAG, "Beacon: " + beacon.getId() + ", RSSI: " + beacon.getRssi() + ", Distance: " + distances.get(beacon.getId()));
            }
            if (detectedBeacons.size() >= 3) {
                double[] estimatedPosition = trilaterate(detectedBeacons, distances);
                if (estimatedPosition != null) {
                    Log.d(TAG, "Estimated location: (" + estimatedPosition[0] + ", " + estimatedPosition[1] + ")");
                    locationCallback.onLocationEstimated(estimatedPosition[0], estimatedPosition[1]);
                } else {
                    Log.e(TAG, "Trilateration failed");
                }
            } else {
                Log.d(TAG, "Not enough beacons for trilateration. Detected: " + detectedBeacons.size());
            }
        }

        // 감지된 비콘 정보를 필터링하여 리스트로 반환하는 메서드입니다.
        private List<Beacon> getDetectedBeacons() {
            return beacons.values().stream()
                    .filter(beacon -> beacon.getRssi() != 0) // RSSI 값이 0이 아닌, 감지된 비콘만 필터링
                    .collect(Collectors.toList());
        }

        // 삼변측량 알고리즘을 사용하여 위치를 추정하는 메서드입니다.
        private double[] trilaterate(List<Beacon> beacons, Map<String, Double> distances) {
            // 간단한 가중 평균 방식을 사용하여 위치를 추정합니다.
            double totalWeight = 0;
            double weightedSumX = 0;
            double weightedSumY = 0;

            for (Beacon beacon : beacons) {
                double distance = distances.get(beacon.getId());
                double weight = 1 / (distance * distance);  // 거리의 제곱에 반비례하는 가중치 계산
                totalWeight += weight;
                weightedSumX += beacon.getX() * weight;
                weightedSumY += beacon.getY() * weight;
            }

            // 추정된 위치 계산
            double estimatedX = weightedSumX / totalWeight;
            double estimatedY = weightedSumY / totalWeight;

            Log.d(TAG, "Trilateration result: (" + estimatedX + ", " + estimatedY + ")");
            return new double[]{estimatedX, estimatedY};
        }

        // 파일에서 비콘 정보를 로드하는 메서드입니다.
        private void loadBeaconsFromFile() {
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(context.getAssets().open("beacon_info.txt")));
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split(",");
                    if (parts.length < 8) {
                        Log.w(TAG, "Skipping invalid line: " + line);
                        continue;
                    }
                    String macAddress = parts[0].toUpperCase();
                    String uuid = parts[1];
                    int major = Integer.parseInt(parts[2]);
                    int minor = Integer.parseInt(parts[3]);
                    int txPower = Integer.parseInt(parts[4]);
                    double x = Double.parseDouble(parts[5]);
                    double y = Double.parseDouble(parts[6]);
                    String color = parts[7];

                    // 비콘 객체 생성 후 해시맵에 저장
                    Beacon beacon = new Beacon(macAddress, uuid, x, y, txPower, 0, color);
                    beacon.setMajor(major);
                    beacon.setMinor(minor);
                    beacons.put(macAddress, beacon);
                    Log.d(TAG, "Loaded beacon: " + macAddress + " at (" + x + ", " + y + ")");
                }
                reader.close();
            } catch (IOException e) {
                Log.e(TAG, "Error loading beacon info", e);
            } catch (NumberFormatException e) {
                Log.e(TAG, "Error parsing beacon data", e);
            }
            Log.d(TAG, "Loaded " + beacons.size() + " beacons");
        }

        // 현재 비콘 정보를 반환하는 메서드입니다.
        public static List<Beacon> getBeacons() {
            return new ArrayList<>(beacons.values());
        }

        // 비콘 객체를 나타내는 내부 클래스입니다.
        public static class Beacon {
            private String id;
            private String macAddress;
            private String uuid;
            private int major;
            private int minor;
            private double x;
            private double y;
            private int txPower;
            private double rssi;
            private String color;

            public Beacon(String id, String macAddress, double x, double y, int txPower, double rssi, String color) {
                this.id = id;
                this.macAddress = macAddress;
                this.uuid = uuid;
                this.x = x;
                this.y = y;
                this.txPower = txPower;
                this.rssi = rssi;
                this.color = color;
            }

            // 각 비콘의 속성에 접근할 수 있는 getter와 setter 메서드들
            public String getId() { return id; }
            public String getMacAddress() { return macAddress; }
            public double getX() { return x; }
            public double getY() { return y; }
            public int getTxPower() { return txPower; }
            public double getRssi() { return rssi; }
            public String getColor() { return color; }
            public void setMajor(int major){
                this.major = major;
            }

            public void setMinor(int minor){
                this.minor = minor;
            }

            public int getMajor() {
                return major;
            }

            public int getMinor() {
                return minor;
            }

            public String getUuid() {
                return uuid;
            }

            public void setRssi(double rssi) { this.rssi = rssi; }
        }
    }