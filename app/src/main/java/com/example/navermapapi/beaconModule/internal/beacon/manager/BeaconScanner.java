package com.example.navermapapi.beaconModule.internal.beacon.manager;

import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.os.ParcelUuid;
import com.example.navermapapi.beaconModule.internal.beacon.data.model.BeaconData;
import com.example.navermapapi.beaconModule.utils.BeaconUtils;
import java.util.ArrayList;
import java.util.List;

public class BeaconScanner {
    private final BluetoothLeScanner bluetoothLeScanner;
    private final ScanCallback scanCallback;
    private final BeaconScanCallback callback;
    private boolean isScanning = false;

    public BeaconScanner(BluetoothLeScanner scanner, BeaconScanCallback callback) {
        this.bluetoothLeScanner = scanner;
        this.callback = callback;
        this.scanCallback = createScanCallback();
    }

    private ScanCallback createScanCallback() {
        return new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                if (result == null || result.getDevice() == null) return;

                String macAddress = result.getDevice().getAddress();
                int rssi = result.getRssi();

                if (!BeaconUtils.isValidMacAddress(macAddress)) return;

                // Create beacon data from scan result
                BeaconData beaconData = BeaconUtils.createBeaconData(macAddress, rssi);
                if (beaconData != null) {
                    callback.onBeaconDiscovered(beaconData);
                }
            }
        };
    }

    public void startScanning() {
        if (!isScanning && bluetoothLeScanner != null) {
            ScanSettings settings = new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build();

            List<ScanFilter> filters = new ArrayList<>();
            filters.add(new ScanFilter.Builder()
                    .setServiceUuid(ParcelUuid.fromString("fda50693-a4e2-4fb1-afcf-c6eb07647825"))
                    .build());

            bluetoothLeScanner.startScan(filters, settings, scanCallback);
            isScanning = true;
        }
    }

    public void stopScanning() {
        if (isScanning && bluetoothLeScanner != null) {
            bluetoothLeScanner.stopScan(scanCallback);
            isScanning = false;
        }
    }

    public boolean isScanning() {
        return isScanning;
    }

    public interface BeaconScanCallback {
        void onBeaconDiscovered(BeaconData beacon);
    }
}