package com.example.navermapapi.beaconModule.utils;

import android.content.Context;
import com.example.navermapapi.beaconModule.internal.beacon.data.model.BeaconData;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

public class BeaconUtils {
    private static final String BEACON_CONFIG_FILE = "beacon_info.txt";
    private static final double RSSI_AT_1M = -59.0;
    private static Map<String, BeaconData> beaconConfigMap = null;

    // 비콘 설정 로드
    public static Map<String, BeaconData> loadBeaconConfig(Context context) {
        if (beaconConfigMap != null) {
            return beaconConfigMap;
        }

        beaconConfigMap = new HashMap<>();
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    context.getAssets().open(BEACON_CONFIG_FILE)));

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;

                String[] parts = line.split(",");
                if (parts.length == 8) {
                    BeaconData beacon = new BeaconData(
                            parts[0].trim(),                    // MAC
                            parts[1].trim(),                    // UUID
                            Integer.parseInt(parts[2].trim()),  // Major
                            Integer.parseInt(parts[3].trim()),  // Minor
                            Integer.parseInt(parts[4].trim()),  // RSSI
                            Double.parseDouble(parts[5].trim()),// X
                            Double.parseDouble(parts[6].trim()),// Y
                            parts[7].trim()                     // Color
                    );
                    beaconConfigMap.put(beacon.getMacAddress(), beacon);
                }
            }
            reader.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return beaconConfigMap;
    }

    // BeaconData 생성 메서드 추가
    public static BeaconData createBeaconData(String macAddress, int rssi) {
        if (beaconConfigMap == null || !beaconConfigMap.containsKey(macAddress)) {
            return null;
        }

        BeaconData configuredBeacon = beaconConfigMap.get(macAddress);
        return new BeaconData(
                macAddress,
                configuredBeacon.getUuid(),
                configuredBeacon.getMajor(),
                configuredBeacon.getMinor(),
                rssi,  // 현재 측정된 RSSI 값 사용
                configuredBeacon.getX(),
                configuredBeacon.getY(),
                configuredBeacon.getColor()
        );
    }

    // MAC 주소 검증
    public static boolean isValidMacAddress(String macAddress) {
        return macAddress != null && macAddress.matches("([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})");
    }

    // RSSI를 거리로 변환
    public static double calculateDistance(int rssi) {
        double ratio = (RSSI_AT_1M - rssi) / (10 * 2.0);
        return Math.pow(10, ratio);
    }
}