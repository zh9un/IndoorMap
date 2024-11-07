package com.example.navermapapi.beaconModule.internal.beacon.manager;

import android.content.Context;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothAdapter;
import android.os.Handler;
import android.os.Looper;
import com.example.navermapapi.beaconModule.internal.beacon.data.model.BeaconData;
import com.example.navermapapi.beaconModule.utils.BeaconUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BeaconManager {
    private static final long SCAN_INTERVAL = 1000; // 1초

    private final Context context;
    private final BeaconScanner beaconScanner;
    private final SignalManager signalManager;
    private final Map<String, BeaconData> activeBeacons;
    private final List<BeaconManagerCallback> callbacks;
    private final Handler handler;
    private boolean isScanning = false;

    public BeaconManager(Context context) {
        this.context = context.getApplicationContext();
        this.signalManager = new SignalManager();
        this.activeBeacons = new ConcurrentHashMap<>();
        this.callbacks = new ArrayList<>();
        this.handler = new Handler(Looper.getMainLooper());

        BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter bluetoothAdapter = bluetoothManager != null ? bluetoothManager.getAdapter() : null;

        if (bluetoothAdapter != null) {
            this.beaconScanner = new BeaconScanner(
                    bluetoothAdapter.getBluetoothLeScanner(),
                    this::onBeaconDiscovered
            );
        } else {
            this.beaconScanner = null;
        }
    }

    public void startScanning() {
        if (beaconScanner != null && !isScanning) {
            isScanning = true;
            beaconScanner.startScanning();
            handler.post(scanRunnable);
        }
    }

    public void stopScanning() {
        if (beaconScanner != null && isScanning) {
            isScanning = false;
            beaconScanner.stopScanning();
            handler.removeCallbacks(scanRunnable);
            activeBeacons.clear();
        }
    }

    private final Runnable scanRunnable = new Runnable() {
        @Override
        public void run() {
            if (isScanning) {
                updateBeaconList();
                handler.postDelayed(this, SCAN_INTERVAL);
            }
        }
    };

    private void onBeaconDiscovered(BeaconData beacon) {
        signalManager.updateSignal(beacon.getMacAddress(), beacon.getRssi());
        activeBeacons.put(beacon.getMacAddress(), beacon);
        notifyBeaconUpdated(beacon);
    }

    private void updateBeaconList() {
        long currentTime = System.currentTimeMillis();
        activeBeacons.entrySet().removeIf(entry ->
                (currentTime - entry.getValue().getTimestamp()) > 10000); // 10초 이상 된 비콘 제거
    }

    public List<BeaconData> getActiveBeacons() {
        return new ArrayList<>(activeBeacons.values());
    }

    public void addCallback(BeaconManagerCallback callback) {
        if (!callbacks.contains(callback)) {
            callbacks.add(callback);
        }
    }

    public void removeCallback(BeaconManagerCallback callback) {
        callbacks.remove(callback);
    }

    private void notifyBeaconUpdated(BeaconData beacon) {
        for (BeaconManagerCallback callback : callbacks) {
            callback.onBeaconUpdated(beacon);
        }
    }

    public interface BeaconManagerCallback {
        void onBeaconUpdated(BeaconData beacon);
    }
}