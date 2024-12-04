package com.example.navermapapi.beaconModule.internal.beacon;

import com.naver.maps.geometry.LatLng;
import java.util.HashMap;
import java.util.Map;

public class BeaconConfig {
    // 비콘 UUID
    public static final String BEACON_UUID = "fda50693-a4e2-4fb1-afcf-c6eb07647825";

    // 시작점과 도착점 위치 정의
    public static final LatLng START_POINT = new LatLng(37.558414, 127.048783); // 시작점
    public static final LatLng END_POINT = new LatLng(37.558368, 127.049108);   // 도착점

    private static final Map<String, BeaconInfo> KNOWN_BEACONS = new HashMap<>();

    static {
        // 시작점 비콘
        addBeacon("C3:00:00:19:2F:4C", BEACON_UUID, 123, 456, START_POINT, "START");

        // 도착점 비콘
        addBeacon("C3:00:00:19:2F:4A", BEACON_UUID, 123, 457, END_POINT, "END");
    }

    private static void addBeacon(String macAddress, String uuid, int major, int minor,
                                  LatLng position, String description) {
        BeaconInfo info = new BeaconInfo(
                macAddress,
                uuid,
                major,
                minor,
                position,
                description
        );
        KNOWN_BEACONS.put(macAddress, info);
    }

    public static BeaconInfo getBeaconInfo(String macAddress) {
        return KNOWN_BEACONS.get(macAddress);
    }

    public static boolean isKnownBeacon(String macAddress, String uuid) {
        BeaconInfo info = KNOWN_BEACONS.get(macAddress);
        if (info == null) return false;
        return info.uuid.equalsIgnoreCase(uuid);
    }

    public static class BeaconInfo {
        public final String macAddress;
        public final String uuid;
        public final int major;
        public final int minor;
        public final LatLng position;
        public final String description;

        public BeaconInfo(String macAddress, String uuid, int major, int minor,
                          LatLng position, String description) {
            this.macAddress = macAddress;
            this.uuid = uuid;
            this.major = major;
            this.minor = minor;
            this.position = position;
            this.description = description;
        }

        public boolean isStartBeacon() {
            return "START".equals(description);
        }

        public boolean isEndBeacon() {
            return "END".equals(description);
        }
    }
}