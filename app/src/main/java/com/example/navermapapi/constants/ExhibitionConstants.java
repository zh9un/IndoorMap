package com.example.navermapapi.constants;

import com.example.navermapapi.coreModule.api.environment.model.EnvironmentType;
import com.naver.maps.geometry.LatLng;

public class ExhibitionConstants {
    // 실외 주요 포인트
    public static final LatLng MAIN_GATE_POINT = new LatLng(37.558396, 127.048793);
    public static final LatLng INFO_BUILDING_ENTRANCE = new LatLng(37.558458, 127.049080);

    // 정보관 1층 주요 포인트
    public static final LatLng LOBBY_POINT = new LatLng(37.558352, 127.049129);
    public static final LatLng ELEVATOR_POINT = new LatLng(37.558338, 127.049084);
    public static final LatLng EXHIBITION_POINT = new LatLng(37.558435, 127.049041);

    // 전시 데모용 경로
    public static final LatLng[] DEMO_PATH = {
            MAIN_GATE_POINT,
            INFO_BUILDING_ENTRANCE,
            LOBBY_POINT,
            EXHIBITION_POINT
    };

    // 실내외 전환 포인트
    public static final LatLng TRANSITION_POINT = INFO_BUILDING_ENTRANCE;

    // 경로 설명
    public static final String[] PATH_DESCRIPTIONS = {
            "정문",
            "정보관 입구",
            "1층 로비",
            "전시장"
    };

    // 주요 음성 안내 메시지
    public static final String[] VOICE_MESSAGES = {
            "정문에서 출발합니다.",
            "정보관 입구에 도착했습니다. 실내로 진입합니다.",
            "1층 로비에 도착했습니다. 전시장 방향으로 이동하세요.",
            "전시장에 도착했습니다."
    };

    private ExhibitionConstants() {
        // Utility class
    }

    public static String getPointDescription(LatLng point) {
        for (int i = 0; i < DEMO_PATH.length; i++) {
            if (isNearPoint(DEMO_PATH[i], point)) {
                return PATH_DESCRIPTIONS[i];
            }
        }
        return "알 수 없는 위치";
    }

    public static boolean isTransitionPoint(LatLng point) {
        return isNearPoint(TRANSITION_POINT, point);
    }

    public static boolean isNearPoint(LatLng point1, LatLng point2) {
        return calculateDistance(point1, point2) < 5.0; // 5미터 이내
    }

    public static double calculateDistance(LatLng point1, LatLng point2) {
        float[] results = new float[1];
        android.location.Location.distanceBetween(
                point1.latitude, point1.longitude,
                point2.latitude, point2.longitude,
                results
        );
        return results[0];
    }

    public static String getVoiceMessage(int index) {
        if (index >= 0 && index < VOICE_MESSAGES.length) {
            return VOICE_MESSAGES[index];
        }
        return "경로에서 벗어났습니다.";
    }

    public static EnvironmentType getEnvironmentType(LatLng point) {
        if (isTransitionPoint(point)) {
            return EnvironmentType.TRANSITION;
        }
        // MAIN_GATE_POINT와 INFO_BUILDING_ENTRANCE 사이는 실외
        if (calculateDistance(point, MAIN_GATE_POINT) <
                calculateDistance(point, LOBBY_POINT)) {
            return EnvironmentType.OUTDOOR;
        }
        return EnvironmentType.INDOOR;
    }
}