// PdrPositionManager.java

package com.example.navermapapi.beaconModule.internal.pdr;

import com.naver.maps.geometry.LatLng;

/**
 * PDR 데이터를 기반으로 상대 좌표를 절대 위경도로 변환하는 클래스.
 * 초기 GPS 좌표를 기준으로 LatLng 계산.
 */
public class PdrPositionManager {
    private static final double EARTH_RADIUS = 6378137.0; // 지구 반지름 (미터 단위)
    private final LatLng initialLocation;

    public PdrPositionManager(LatLng initialLocation) {
        this.initialLocation = initialLocation;
    }

    /**
     * 상대 거리 오프셋(X, Y)으로 위도 및 경도 계산
     *
     * @param offsetX 동쪽으로 이동한 거리 (미터)
     * @param offsetY 북쪽으로 이동한 거리 (미터)
     * @return 변환된 LatLng 좌표
     */
    public LatLng calculateLatLng(double offsetX, double offsetY) {
        double deltaLat = (offsetY / EARTH_RADIUS) * (180 / Math.PI);
        double deltaLng = (offsetX / (EARTH_RADIUS * Math.cos(Math.PI * initialLocation.latitude / 180))) * (180 / Math.PI);

        double latitude = initialLocation.latitude + deltaLat;
        double longitude = initialLocation.longitude + deltaLng;

        return new LatLng(latitude, longitude);
    }
}
