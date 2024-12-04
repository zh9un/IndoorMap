package com.example.navermapapi.utils;

import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.naver.maps.geometry.LatLng;
import java.util.Objects;

/**
 * PDR의 상대 좌표를 실제 위경도로 변환하는 유틸리티 클래스
 * 실내 위치 추적 시 건물 진입 지점을 기준으로 상대 좌표를 변환
 */
public class CoordinateConverter {
    private static final String TAG = "CoordinateConverter";

    // 상수 정의
    private static final double EARTH_RADIUS = 6371000.0;  // 지구 반지름 (미터)
    private static final double MIN_LATITUDE = -90.0;      // 최소 위도
    private static final double MAX_LATITUDE = 90.0;       // 최대 위도
    private static final double MIN_LONGITUDE = -180.0;    // 최소 경도
    private static final double MAX_LONGITUDE = 180.0;     // 최대 경도
    private static final double MAX_RELATIVE_DISTANCE = 1000.0; // 최대 1km

    // 기준점 좌표 (진입점)
    private LatLng referencePoint;

    // 좌표 계산을 위한 변수들
    private double metersPerLatitude;
    private double metersPerLongitude;

    public CoordinateConverter() {
    }

    /**
     * 기준점 설정
     * @param point 기준이 되는 위경도 좌표
     * @throws IllegalArgumentException 유효하지 않은 좌표가 입력된 경우
     */
    public void setReferencePoint(@NonNull LatLng point) {
        validateLatLng(point);
        this.referencePoint = point;

        // 현재 위도에서의 위/경도 1도당 미터 거리 계산
        this.metersPerLatitude = EARTH_RADIUS * Math.PI / 180.0;
        this.metersPerLongitude = metersPerLatitude * Math.cos(Math.toRadians(point.latitude));

        Log.d(TAG, String.format("Reference point set: %s", point));
    }

    /**
     * PDR의 상대좌표(미터)를 실제 위경도로 변환
     * @param relativeX 동쪽 방향 이동거리(미터)
     * @param relativeY 북쪽 방향 이동거리(미터)
     * @return 변환된 위경도 좌표
     * @throws IllegalStateException 기준점이 설정되지 않은 경우
     * @throws IllegalArgumentException 상대 좌표가 유효 범위를 벗어난 경우
     */
    @NonNull
    public LatLng toLatLng(double relativeX, double relativeY) {
        if (referencePoint == null) {
            throw new IllegalStateException("Reference point not set");
        }

        validateRelativeCoordinates(relativeX, relativeY);

        try {
            // 상대 좌표를 위경도 차이로 변환
            double latDiff = relativeY / metersPerLatitude;
            double lngDiff = relativeX / metersPerLongitude;

            // 새로운 위경도 계산
            double newLat = referencePoint.latitude + latDiff;
            double newLng = referencePoint.longitude + lngDiff;

            // 결과 위경도 유효성 검사
            validateLatLng(newLat, newLng);

            return new LatLng(newLat, newLng);

        } catch (Exception e) {
            Log.e(TAG, "Error converting coordinates", e);
            throw new IllegalArgumentException("Coordinate conversion failed", e);
        }
    }

    /**
     * 실제 위경도를 PDR 상대좌표(미터)로 변환
     * @param latLng 변환할 위경도 좌표
     * @return 기준점으로부터의 상대 좌표 [x, y] (미터)
     * @throws IllegalStateException 기준점이 설정되지 않은 경우
     * @throws IllegalArgumentException 유효하지 않은 좌표가 입력된 경우
     */
    @NonNull
    public double[] toRelativeCoordinates(@NonNull LatLng latLng) {
        if (referencePoint == null) {
            throw new IllegalStateException("Reference point not set");
        }

        validateLatLng(latLng);

        try {
            double relativeY = (latLng.latitude - referencePoint.latitude) * metersPerLatitude;
            double relativeX = (latLng.longitude - referencePoint.longitude) * metersPerLongitude;

            return new double[]{relativeX, relativeY};

        } catch (Exception e) {
            Log.e(TAG, "Error converting to relative coordinates", e);
            throw new IllegalArgumentException("Relative coordinate conversion failed", e);
        }
    }

    /**
     * 두 지점 간의 거리를 계산 (미터)
     */
    public static double calculateDistance(@NonNull LatLng point1, @NonNull LatLng point2) {
        validateLatLng(point1);
        validateLatLng(point2);

        double lat1 = Math.toRadians(point1.latitude);
        double lat2 = Math.toRadians(point2.latitude);
        double dLat = Math.toRadians(point2.latitude - point1.latitude);
        double dLng = Math.toRadians(point2.longitude - point1.longitude);

        double a = Math.sin(dLat/2) * Math.sin(dLat/2) +
                Math.cos(lat1) * Math.cos(lat2) *
                        Math.sin(dLng/2) * Math.sin(dLng/2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));

        return EARTH_RADIUS * c;
    }

    /**
     * 위경도 좌표 유효성 검사
     */
    private static void validateLatLng(@NonNull LatLng latLng) {
        validateLatLng(latLng.latitude, latLng.longitude);
    }

    /**
     * 위도와 경도 값의 유효성 검사
     */
    private static void validateLatLng(double latitude, double longitude) {
        if (latitude < MIN_LATITUDE || latitude > MAX_LATITUDE) {
            throw new IllegalArgumentException("Invalid latitude: " + latitude);
        }
        if (longitude < MIN_LONGITUDE || longitude > MAX_LONGITUDE) {
            throw new IllegalArgumentException("Invalid longitude: " + longitude);
        }
    }

    /**
     * 상대 좌표 유효성 검사
     */
    private void validateRelativeCoordinates(double relativeX, double relativeY) {
        if (Math.abs(relativeX) > MAX_RELATIVE_DISTANCE ||
                Math.abs(relativeY) > MAX_RELATIVE_DISTANCE) {
            throw new IllegalArgumentException(
                    "Relative coordinates exceed maximum allowed distance");
        }
    }

    /**
     * 기준점 좌표 반환
     */
    @Nullable
    public LatLng getReferencePoint() {
        return referencePoint;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CoordinateConverter that = (CoordinateConverter) o;
        return Objects.equals(referencePoint, that.referencePoint);
    }

    @Override
    public int hashCode() {
        return Objects.hash(referencePoint);
    }

    @NonNull
    @Override
    public String toString() {
        return "CoordinateConverter{" +
                "referencePoint=" + referencePoint +
                '}';
    }
}