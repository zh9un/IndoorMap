package com.example.navermapapi.appModule.indoor;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Query;

public interface GeocodingService {
    @GET("map-geocode/v2/geocode")
    Call<GeocodingResponse> getCoordinates(
            @Query("query") String query,
            @Header("X-Ncp-Apigw-Id") String clientId,
            @Header("X-Ncp-Apigw-Key") String clientSecret
    );
}
