package com.example.navermapapi.appModule.indoor;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.naver.maps.geometry.LatLng;
import com.naver.maps.geometry.LatLngBounds;
import com.naver.maps.map.NaverMap;
import com.naver.maps.map.overlay.GroundOverlay;
import com.naver.maps.map.overlay.OverlayImage;

/**
 * 실내 도면을 네이버 지도 위에 오버레이하는 클래스
 * - 도면 이미지 크기/위치 조정
 * - 회전 및 정렬 지원
 * - 투명도 조절
 */
public class IndoorMapOverlay {
    private static final float DEFAULT_OPACITY = 0.7f;

    private final GroundOverlay groundOverlay;
    private Bitmap floorPlanBitmap;
    private LatLngBounds bounds;
    private double rotation = 0.0;
    private float opacity = DEFAULT_OPACITY;
    private boolean isVisible = true;
    private NaverMap lastMap;

    public IndoorMapOverlay() {
        this.groundOverlay = new GroundOverlay();
        groundOverlay.setAlpha(DEFAULT_OPACITY);
    }

    /**
     * 도면 이미지 설정
     * @param bitmap 도면 비트맵 이미지
     */
    public void setFloorPlan(@NonNull Bitmap bitmap) {
        if (bitmap == null) {
            Log.e("IndoorMapOverlay", "Bitmap is null. Cannot set floor plan.");
            return;
        }
        this.floorPlanBitmap = bitmap;
        updateOverlayImage();
    }

    /**
     * 도면의 위치와 크기 설정
     * @param bounds 도면이 표시될 위경도 영역
     */
    public void setBounds(@NonNull LatLngBounds bounds) {
        this.bounds = bounds;
        groundOverlay.setBounds(bounds);
    }

    /**
     * 도면 회전 각도 설정
     * @param degrees 시계 방향 회전 각도 (도 단위)
     */
    public void setRotation(double degrees) {
        this.rotation = degrees;
        updateOverlayImage();
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
        groundOverlay.setMap(isVisible ? lastMap : null);
    }

    /**
     * 네이버 지도에 도면 오버레이 표시
     * @param map 네이버 지도 객체
     */
    public void setMap(@Nullable NaverMap map) {
        this.lastMap = map;
        if (isVisible) {
            groundOverlay.setMap(map);
        }
    }

    private void updateOverlayImage() {
        if (floorPlanBitmap == null) {
            Log.e("IndoorMapOverlay", "Floor plan bitmap is null. Cannot update overlay image.");
            return;
        }

        // 회전된 비트맵 생성
        Matrix matrix = new Matrix();
        matrix.postRotate((float) rotation);

        Bitmap rotatedBitmap = Bitmap.createBitmap(
                floorPlanBitmap,
                0,
                0,
                floorPlanBitmap.getWidth(),
                floorPlanBitmap.getHeight(),
                matrix,
                true
        );

        groundOverlay.setImage(OverlayImage.fromBitmap(rotatedBitmap));
    }

    /**
     * 도면의 중심 좌표 반환
     * @return 중심 위경도 좌표
     */
    @Nullable
    public LatLng getCenter() {
        return bounds != null ? bounds.getCenter() : null;
    }

    /**
     * 현재 회전 각도 반환
     * @return 회전 각도 (도 단위)
     */
    public double getRotation() {
        return rotation;
    }

    /**
     * 도면이 표시되는 영역 반환
     * @return 위경도 영역
     */
    @Nullable
    public LatLngBounds getBounds() {
        return bounds;
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
}
