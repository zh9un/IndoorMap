package com.example.navermapapi.beaconModule.internal.beacon.data.model;

public class BeaconData {
    private String macAddress;
    private String uuid;
    private int major;
    private int minor;
    private int rssi;
    private double x;
    private double y;
    private String color;
    private long timestamp;

    public BeaconData(String macAddress, String uuid, int major, int minor, int rssi,
                      double x, double y, String color) {
        this.macAddress = macAddress;
        this.uuid = uuid;
        this.major = major;
        this.minor = minor;
        this.rssi = rssi;
        this.x = x;
        this.y = y;
        this.color = color;
        this.timestamp = System.currentTimeMillis();
    }

    public String getMacAddress() { return macAddress; }
    public String getUuid() { return uuid; }
    public int getMajor() { return major; }
    public int getMinor() { return minor; }
    public int getRssi() { return rssi; }
    public double getX() { return x; }
    public double getY() { return y; }
    public String getColor() { return color; }
    public long getTimestamp() { return timestamp; }

    public void setRssi(int rssi) {
        this.rssi = rssi;
        this.timestamp = System.currentTimeMillis();
    }
}
