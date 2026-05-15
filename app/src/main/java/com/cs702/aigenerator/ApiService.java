package com.cs702.aigenerator;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface ApiService {

    @POST("auth")
    Call<AuthResponse> auth();

    @POST("generate_image")
    Call<ResponseBody> generateImage(@Body GenerateRequest request);
}
