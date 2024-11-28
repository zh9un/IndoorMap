package com.example.navermapapi.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.naver.maps.geometry.LatLng;
import com.naver.maps.geometry.LatLngBounds;
import com.naver.maps.map.NaverMap;
import com.naver.maps.map.overlay.GroundOverlay;
import com.naver.maps.map.overlay.OverlayImage;

/**
 * 도면 이미지를 관리하고 지도 위에 오버레이하는 클래스
 */
public class FloorPlanManager {
    private static final String TAG = "FloorPlanManager";

    private final Context context;
    private final GroundOverlay groundOverlay;
    private Bitmap floorPlanBitmap;
    private float rotation;
    private float opacity;
    private boolean isVisible;
    private NaverMap naverMap;

    // 도면 관련 변수
    private LatLngBounds bounds;

    // 오버레이 폭 (미터 단위)
    private double overlayWidthMeters;

    // 싱글톤 패턴 적용
    private static FloorPlanManager instance;

    public static FloorPlanManager getInstance(@NonNull Context context) {
        if (instance == null) {
            instance = new FloorPlanManager(context);
        }
        return instance;
    }

    private FloorPlanManager(@NonNull Context context) {
        this.context = context;
        this.groundOverlay = new GroundOverlay();
        this.rotation = 0.0f;
        this.opacity = 0.7f;
        this.isVisible = true;
        groundOverlay.setAlpha(opacity);
    }

    /**
     * 도면 설정 초기화
     * @param resourceId 도면 이미지 리소스 ID
     * @param center 오버레이의 중심 좌표
     * @param overlayWidthMeters 오버레이의 폭 (미터 단위)
     * @param rotationDegrees 회전 각도
     * @param opacity 투명도
     */
    public void initialize(int resourceId, LatLng center, double overlayWidthMeters, float rotationDegrees, float opacity) {
        Log.d(TAG, "Initializing FloorPlanManager with resourceId: " + resourceId);

        this.overlayWidthMeters = overlayWidthMeters;
        this.rotation = rotationDegrees;
        setOpacity(opacity);

        loadFloorPlan(resourceId);

        // 이미지의 가로세로 비율을 계산하여 LatLngBounds를 생성
        calculateBounds(center);
        groundOverlay.setBounds(bounds);
    }

    private void loadFloorPlan(int resourceId) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false; // 이미지 스케일링 방지
        Bitmap originalBitmap = BitmapFactory.decodeResource(context.getResources(), resourceId, options);

        if (originalBitmap == null) {
            Log.e(TAG, "Failed to load floor plan image. Resource ID: " + resourceId);
            return;
        }

        // 회전 적용
        Bitmap rotatedBitmap = BitmapUtils.rotateBitmap(originalBitmap, rotation);

        this.floorPlanBitmap = rotatedBitmap;
        groundOverlay.setImage(OverlayImage.fromBitmap(floorPlanBitmap));
    }

    private void calculateBounds(LatLng center) {
        // 이미지의 가로세로 비율 계산
        double imageWidth = floorPlanBitmap.getWidth();
        double imageHeight = floorPlanBitmap.getHeight();
        double imageAspectRatio = imageWidth / imageHeight;

        // 이미지의 회전을 고려하여 가로세로 비율을 재계산
        double adjustedAspectRatio = imageAspectRatio;

        // 오버레이의 높이를 폭과 비율로 계산
        double overlayHeightMeters = overlayWidthMeters / adjustedAspectRatio;

        // 중심 좌표에서의 Offset 계산
        double latOffset = metersToLat(overlayHeightMeters / 2, center.latitude);
        double lngOffset = metersToLng(overlayWidthMeters / 2, center.latitude);

        // LatLngBounds 생성
        LatLng southWest = new LatLng(center.latitude - latOffset, center.longitude - lngOffset);
        LatLng northEast = new LatLng(center.latitude + latOffset, center.longitude + lngOffset);
        bounds = new LatLngBounds(southWest, northEast);
    }

    private double metersToLat(double meters, double latitude) {
        return (meters / 6378137) * (180 / Math.PI);
    }

    private double metersToLng(double meters, double latitude) {
        return (meters / (6378137 * Math.cos(Math.toRadians(latitude)))) * (180 / Math.PI);
    }

    /**
     * 도면 회전 각도 설정
     * @param degrees 회전 각도 (도 단위)
     */
    public void setRotation(float degrees) {
        this.rotation = degrees;

        // 기존 Bitmap 해제
        if (floorPlanBitmap != null && !floorPlanBitmap.isRecycled()) {
            floorPlanBitmap.recycle();
        }

        // 원본 Bitmap 다시 로드
        loadFloorPlan(FloorPlanConfig.RESOURCE_ID);

        // 회전 후 이미지로 업데이트
        groundOverlay.setImage(OverlayImage.fromBitmap(floorPlanBitmap));
    }

    /**
     * 도면 투명도 설정
     * @param opacity 투명도 (0.0 ~ 1.0)
     */
    public void setOpacity(float opacity) {
        this.opacity = Math.max(0.0f, Math.min(1.0f, opacity));
        groundOverlay.setAlpha(this.opacity);
    }

    /**
     * 도면 표시 여부 설정
     * @param visible true면 표시, false면 숨김
     */
    public void setVisible(boolean visible) {
        this.isVisible = visible;
        groundOverlay.setMap(isVisible ? naverMap : null);
    }

    /**
     * 네이버 지도에 도면 오버레이 표시
     * @param map 네이버 지도 객체
     */
    public void setMap(@Nullable NaverMap map) {
        this.naverMap = map;
        if (isVisible && map != null) {
            groundOverlay.setMap(map);
        } else {
            groundOverlay.setMap(null);
        }
    }

    /**
     * 리소스 정리
     */
    public void cleanup() {
        setMap(null);
        if (floorPlanBitmap != null && !floorPlanBitmap.isRecycled()) {
            floorPlanBitmap.recycle();
            floorPlanBitmap = null;
        }
        groundOverlay.setImage(null);
    }

    // Getter 메서드들
    @Nullable
    public LatLngBounds getBounds() {
        return bounds;
    }
}
