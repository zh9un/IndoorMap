package com.example.navermapapi.constants;

import com.example.navermapapi.coreModule.api.environment.model.EnvironmentType;
import com.naver.maps.geometry.LatLng;

public class ExhibitionConstants {
    // 실내 출입구 좌표 배열
    public static final LatLng[] INDOOR_ENTRANCES = {
            new LatLng(37.558458, 127.049080), // 예시: 정보관 입구
            new LatLng(37.558378, 127.048645),
            new LatLng(37.558289, 127.049251)
    };

    // 실외 출구 좌표 배열
    public static final LatLng[] OUTDOOR_EXITS = {
            new LatLng(37.558458, 127.049080), // 예시: 정보관 출구
            new LatLng(37.558378, 127.048645),
            new LatLng(37.558289, 127.049251)
    };

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

    // 실제 데모 시나리오 포인트
    public static final LatLng HANYANG_GATE = new LatLng(37.558640, 127.050685);  // 한양여대 정문
    public static final LatLng INFO_ENTRANCE = new LatLng(37.558289, 127.049251); // 정보문화관 출입구
    public static final LatLng EXHIBITION_HALL = new LatLng(37.558885, 127.048966); // 전시장

    // 데모 경로 좌표
    public static final DemoPoint[] DEMO_SCENARIOS = {
            // 정문 시작
            new DemoPoint(
                    HANYANG_GATE,
                    "한양여자대학교 정문입니다.",
                    EnvironmentType.OUTDOOR
            ),
            // 정보문화관 방향 3걸음
            new DemoPoint(
                    new LatLng(37.558589, 127.050456),
                    "약 200걸음 뒤 계단이 있습니다.",
                    EnvironmentType.OUTDOOR
            ),
            // 정보문화관 출입구
            new DemoPoint(
                    INFO_ENTRANCE,
                    "정보문화관 출입구에 도착했습니다. 실내로 진입합니다.",
                    EnvironmentType.TRANSITION
            ),
            // 실내 첫 걸음
            new DemoPoint(
                    new LatLng(37.558295, 127.049260),
                    "현재 3층입니다.",
                    EnvironmentType.INDOOR
            ),
            // 전시장 안내
            new DemoPoint(
                    new LatLng(37.558350, 127.049270),
                    "정면으로 세 걸음 걸은 뒤 오른쪽으로 20걸음 가세요. 전시장입니다.",
                    EnvironmentType.INDOOR
            ),
            // 전시장 도착
            new DemoPoint(
                    EXHIBITION_HALL,
                    "전시장에 도착했습니다.",
                    EnvironmentType.INDOOR
            )
    };

    // 내부 클래스 추가
    public static class DemoPoint {
        private final LatLng location;
        private final String announcement;
        private final EnvironmentType environment;

        public DemoPoint(LatLng location, String announcement, EnvironmentType environment) {
            this.location = location;
            this.announcement = announcement;
            this.environment = environment;
        }

        public LatLng getLocation() { return location; }
        public String getAnnouncement() { return announcement; }
        public EnvironmentType getEnvironment() { return environment; }
    }

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