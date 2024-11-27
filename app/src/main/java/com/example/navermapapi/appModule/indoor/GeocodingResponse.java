package com.example.navermapapi.appModule.indoor;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class GeocodingResponse {
    @SerializedName("addresses")
    private List<Address> addresses;

    public List<Address> getAddresses() {
        return addresses;
    }

    public static class Address {
        @SerializedName("roadAddress")
        private String roadAddress;

        @SerializedName("jibunAddress")
        private String jibunAddress;

        @SerializedName("x")
        private String x; // 경도 (Longitude)

        @SerializedName("y")
        private String y; // 위도 (Latitude)

        public String getRoadAddress() {
            return roadAddress;
        }

        public String getJibunAddress() {
            return jibunAddress;
        }

        public double getLng() {
            return Double.parseDouble(x);
        }

        public double getLat() {
            return Double.parseDouble(y);
        }
    }
}
