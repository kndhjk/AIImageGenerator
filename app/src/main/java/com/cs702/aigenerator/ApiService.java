package com.cs702.aigenerator;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.POST;

public interface ApiService {
    @POST("auth")
    Call<AuthResponse> auth(@Header("Authorization") String authHeader);

    @POST("generate_image")
    Call<ResponseBody> generateImage(
        @Header("Authorization") String authHeader,
        @Body GenerateRequest request
    );
}