package com.example.navermapapi.beaconModule.model;

public class BeaconData {
    private final String id;
    private final String uuid;
    private final int major;
    private final int minor;
    private final int rssi;
    private final double x;
    private final double y;
    private final String color;

    private BeaconData(Builder builder) {
        this.id = builder.id;
        this.uuid = builder.uuid;
        this.major = builder.major;
        this.minor = builder.minor;
        this.rssi = builder.rssi;
        this.x = builder.x;
        this.y = builder.y;
        this.color = builder.color;
    }

    public String getId() { return id; }
    public String getUuid() { return uuid; }
    public int getMajor() { return major; }
    public int getMinor() { return minor; }
    public int getRssi() { return rssi; }
    public double getX() { return x; }
    public double getY() { return y; }
    public String getColor() { return color; }

    public static class Builder {
        private String id;
        private String uuid;
        private int major;
        private int minor;
        private int rssi;
        private double x;
        private double y;
        private String color;

        public Builder setId(String id) {
            this.id = id;
            return this;
        }

        public Builder setUuid(String uuid) {
            this.uuid = uuid;
            return this;
        }

        public Builder setMajor(int major) {
            this.major = major;
            return this;
        }

        public Builder setMinor(int minor) {
            this.minor = minor;
            return this;
        }

        public Builder setRssi(int rssi) {
            this.rssi = rssi;
            return this;
        }

        public Builder setX(double x) {
            this.x = x;
            return this;
        }

        public Builder setY(double y) {
            this.y = y;
            return this;
        }

        public Builder setColor(String color) {
            this.color = color;
            return this;
        }

        public BeaconData build() {
            return new BeaconData(this);
        }
    }

    @Override
    public String toString() {
        return "BeaconData{" +
                "id='" + id + '\'' +
                ", uuid='" + uuid + '\'' +
                ", major=" + major +
                ", minor=" + minor +
                ", rssi=" + rssi +
                ", x=" + x +
                ", y=" + y +
                ", color='" + color + '\'' +
                '}';
    }
}