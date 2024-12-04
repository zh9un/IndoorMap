package com.example.navermapapi.coreModule.api.environment.model;

public enum EnvironmentType {
    OUTDOOR("실외"),
    INDOOR("실내"),
    TRANSITION("전환 중");

    private static final float OUTDOOR_THRESHOLD = -115.0f;  // dBm
    private static final float INDOOR_THRESHOLD = -135.0f;   // dBm
    private static final int MIN_SATELLITES = 4;

    private final String description;

    EnvironmentType(String description) {
        this.description = description;
    }

    public static EnvironmentType fromGpsSignal(float signalStrength, int visibleSatellites) {
        if (signalStrength > OUTDOOR_THRESHOLD && visibleSatellites >= MIN_SATELLITES) {
            return OUTDOOR;
        } else if (signalStrength < INDOOR_THRESHOLD || visibleSatellites < MIN_SATELLITES) {
            return INDOOR;
        }
        return TRANSITION;
    }

    public boolean isTransitioning() {
        return this == TRANSITION;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return description;
    }
}
