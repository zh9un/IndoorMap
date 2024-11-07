package com.example.navermapapi.beaconModule.internal.floor;

import com.example.navermapapi.beaconModule.internal.floor.FloorTransitionDetector.TransitionType;

public class FloorTransitionEvent {
    private final TransitionType type;
    private final int floorChange;
    private final long duration;      // 밀리초 단위
    private final float speed;        // m/s 단위
    private final long timestamp;
    private final float confidence;   // 0.0 ~ 1.0
    private final int currentFloor;   // 현재 층 추가

    public FloorTransitionEvent(TransitionType type, int floorChange, long duration,
                                float speed, int currentFloor) {
        this.type = type;
        this.floorChange = floorChange;
        this.duration = duration;
        this.speed = speed;
        this.timestamp = System.currentTimeMillis();
        this.currentFloor = currentFloor;
        this.confidence = calculateConfidence();
    }

    public TransitionType getType() {
        return type;
    }

    public int getFloorChange() {
        return floorChange;
    }

    public int getTargetFloor() {
        return currentFloor + floorChange;
    }

    public long getDuration() {
        return duration;
    }

    public float getSpeed() {
        return speed;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public float getConfidence() {
        return confidence;
    }

    public boolean isValid() {
        return confidence > 0.6f;  // 60% 이상의 신뢰도를 가진 전환만 유효
    }

    private float calculateConfidence() {
        float typeConfidence = calculateTypeConfidence();
        float speedConfidence = calculateSpeedConfidence();
        float durationConfidence = calculateDurationConfidence();

        return (typeConfidence + speedConfidence + durationConfidence) / 3.0f;
    }

    private float calculateTypeConfidence() {
        // 전환 유형에 따른 신뢰도 계산
        switch (type) {
            case ELEVATOR:
                // 엘리베이터는 더 신뢰할 수 있는 전환으로 간주
                return 0.9f;
            case STAIRS:
                // 계단은 상대적으로 낮은 신뢰도
                return 0.7f;
            default:
                return 0.5f;
        }
    }

    private float calculateSpeedConfidence() {
        // 속도 기반 신뢰도 계산
        float expectedSpeed;
        if (type == TransitionType.ELEVATOR) {
            expectedSpeed = 2.0f;  // 예상 엘리베이터 속도 (m/s)
        } else {
            expectedSpeed = 0.5f;  // 예상 계단 이동 속도 (m/s)
        }

        float speedDiff = Math.abs(speed - expectedSpeed);
        float maxAllowedDiff = expectedSpeed * 0.5f;  // 50% 오차 허용

        if (speedDiff <= maxAllowedDiff) {
            return 1.0f - (speedDiff / maxAllowedDiff);
        }
        return 0.0f;
    }

    private float calculateDurationConfidence() {
        // 지속 시간 기반 신뢰도 계산
        long expectedDuration;
        if (type == TransitionType.ELEVATOR) {
            expectedDuration = 5000;  // 예상 엘리베이터 이동 시간 (5초)
        } else {
            expectedDuration = 15000; // 예상 계단 이동 시간 (15초)
        }

        long durationDiff = Math.abs(duration - expectedDuration);
        long maxAllowedDiff = expectedDuration / 2;  // 50% 오차 허용

        if (durationDiff <= maxAllowedDiff) {
            return 1.0f - ((float)durationDiff / maxAllowedDiff);
        }
        return 0.0f;
    }

    @Override
    public String toString() {
        return String.format("FloorTransition{type=%s, change=%d, duration=%dms, speed=%.2fm/s, confidence=%.2f}",
                type, floorChange, duration, speed, confidence);
    }
}