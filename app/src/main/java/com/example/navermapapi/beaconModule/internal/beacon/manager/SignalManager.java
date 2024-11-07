package com.example.navermapapi.beaconModule.internal.beacon.manager;

import com.example.navermapapi.beaconModule.utils.SignalUtils;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SignalManager {
    private final Map<String, Double> filteredRssi;
    private final Map<String, Long> lastUpdateTime;
    private static final long SIGNAL_TIMEOUT = 5000; // 5초

    public SignalManager() {
        this.filteredRssi = new ConcurrentHashMap<>();
        this.lastUpdateTime = new ConcurrentHashMap<>();
    }

    public void updateSignal(String macAddress, int rssi) {
        if (!SignalUtils.isValidRssi(rssi)) return;

        // 노이즈 제거
        int cleanRssi = SignalUtils.removeNoise(rssi);

        // 필터링된 RSSI 업데이트
        double currentFiltered = filteredRssi.getOrDefault(macAddress, (double) cleanRssi);
        double newFiltered = SignalUtils.filterRssi(currentFiltered, cleanRssi);

        filteredRssi.put(macAddress, newFiltered);
        lastUpdateTime.put(macAddress, System.currentTimeMillis());
    }

    public double getFilteredRssi(String macAddress) {
        return filteredRssi.getOrDefault(macAddress, Double.NaN);
    }

    public boolean isSignalValid(String macAddress) {
        Long lastUpdate = lastUpdateTime.get(macAddress);
        if (lastUpdate == null) return false;
        return (System.currentTimeMillis() - lastUpdate) < SIGNAL_TIMEOUT;
    }

    public void clearStaleSignals() {
        long currentTime = System.currentTimeMillis();
        lastUpdateTime.entrySet().removeIf(entry ->
                (currentTime - entry.getValue()) > SIGNAL_TIMEOUT);
        filteredRssi.keySet().retainAll(lastUpdateTime.keySet());
    }
}