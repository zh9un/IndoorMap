package com.example.navermapapi.beaconModule.internal.beacon;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.NonNull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.BeaconParser;
import org.altbeacon.beacon.Region;
import com.example.navermapapi.beaconModule.model.BeaconData;

public class BeaconScanner {
    private static final String TAG = "BeaconScanner";
    private static final long SCAN_PERIOD = 1000L;

    private final Context context;
    private final BeaconManager beaconManager;
    private final Handler mainHandler;
    private final List<BeaconScanCallback> callbacks;
    private final List<BeaconData> lastDetectedBeacons;
    private boolean isScanning = false;

    public BeaconScanner(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.beaconManager = BeaconManager.getInstanceForApplication(context);
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.callbacks = new ArrayList<>();
        this.lastDetectedBeacons = new ArrayList<>();

        initializeBeaconManager();
    }

    private void initializeBeaconManager() {
        // iBeacon 레이아웃 설정
        beaconManager.getBeaconParsers().add(new BeaconParser()
                .setBeaconLayout("m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24"));

        beaconManager.setForegroundScanPeriod(SCAN_PERIOD);
    }

    public void startScanning() {
        if (!isScanning) {
            beaconManager.addRangeNotifier((beacons, region) -> {
                List<BeaconData> beaconDataList = new ArrayList<>();
                for (Beacon beacon : beacons) {
                    BeaconData data = new BeaconData.Builder()
                            .setId(beacon.getBluetoothAddress())
                            .setUuid(beacon.getId1().toString())
                            .setMajor(beacon.getId2().toInt())
                            .setMinor(beacon.getId3().toInt())
                            .setRssi(beacon.getRssi())
                            .build();
                    beaconDataList.add(data);
                }
                notifyBeaconsDetected(beaconDataList);
            });

            try {
                beaconManager.startRangingBeacons(new Region("myRangingUniqueId", null, null, null));
                isScanning = true;
                Log.d(TAG, "Beacon scanning started");
            } catch (Exception e) {
                Log.e(TAG, "Error starting beacon scanning", e);
            }
        }
    }

    public void stopScanning() {
        if (isScanning) {
            try {
                beaconManager.stopRangingBeacons(new Region("myRangingUniqueId", null, null, null));
                isScanning = false;
                lastDetectedBeacons.clear();
                Log.d(TAG, "Beacon scanning stopped");
            } catch (Exception e) {
                Log.e(TAG, "Error stopping beacon scanning", e);
            }
        }
    }

    public void addScanCallback(BeaconScanCallback callback) {
        if (!callbacks.contains(callback)) {
            callbacks.add(callback);
        }
    }

    public void removeScanCallback(BeaconScanCallback callback) {
        callbacks.remove(callback);
    }

    /**
     * 마지막으로 스캔된 비콘의 수를 반환합니다.
     * @return 감지된 비콘의 수
     */
    public int getDetectedBeaconCount() {
        return lastDetectedBeacons.size();
    }

    private void notifyBeaconsDetected(List<BeaconData> beacons) {
        mainHandler.post(() -> {
            synchronized (lastDetectedBeacons) {
                lastDetectedBeacons.clear();
                lastDetectedBeacons.addAll(beacons);
            }
            for (BeaconScanCallback callback : new ArrayList<>(callbacks)) {
                callback.onBeaconsDetected(beacons);
            }
        });
    }

    public interface BeaconScanCallback {
        void onBeaconsDetected(List<BeaconData> beacons);
    }

    public boolean isScanning() {
        return isScanning;
    }

    public void cleanup() {
        stopScanning();
        callbacks.clear();
    }
}