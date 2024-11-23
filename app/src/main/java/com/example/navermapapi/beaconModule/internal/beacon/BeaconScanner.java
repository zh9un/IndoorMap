package com.example.navermapapi.beaconModule.internal.beacon;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.BeaconConsumer;
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
    private boolean isScanning = false;

    public BeaconScanner(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.beaconManager = BeaconManager.getInstanceForApplication(context);
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.callbacks = new ArrayList<>();

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
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void stopScanning() {
        if (isScanning) {
            try {
                beaconManager.stopRangingBeacons(new Region("myRangingUniqueId", null, null, null));
            } catch (Exception e) {
                e.printStackTrace();
            }
            isScanning = false;
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

    private void notifyBeaconsDetected(List<BeaconData> beacons) {
        mainHandler.post(() -> {
            for (BeaconScanCallback callback : callbacks) {
                callback.onBeaconsDetected(beacons);
            }
        });
    }

    public interface BeaconScanCallback {
        void onBeaconsDetected(List<BeaconData> beacons);
    }
}