package com.example.navermapapi.utils;

import com.naver.maps.geometry.LatLng;
import com.example.navermapapi.R;

/**
 * 도면 설정을 관리하는 클래스
 */
public class FloorPlanConfig {
    public static final int RESOURCE_ID = R.drawable.indoor_floor_plan_3f;

    // 오버레이의 중심 좌표
    public static final LatLng CENTER = new LatLng(37.558347, 127.048963); // 남서쪽과 북동쪽의 중간 지점

    // 오버레이의 폭 (미터 단위) - 여기에서 크기를 조정합니다.
    public static final double OVERLAY_WIDTH_METERS = 55.7; // 필요에 따라 조정

    // 도면 회전 각도
    public static final float ROTATION = -18.7f; // 필요한 각도로 변경

    // 도면 투명도
    public static final float OPACITY = 0.7f; // 필요에 따라 변경
}
