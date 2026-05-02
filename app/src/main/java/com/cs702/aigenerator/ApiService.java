package com.cs702.aigenerator;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.POST;

/**
 * Retrofit API interface for ai.elliottwen.info endpoints.
 *
 * Security notes:
 * - Authorization header value is obtained from NativeKeyStore (XOR+Base64 encoded)
 * - Header name obtained via char-code obfuscation
 * - Both measures protect the API key from casual reverse engineering
 */
interface ApiService {

    /**
     * Authenticate with the AI server.
     * The Authorization header value comes from NativeKeyStore.
     */
    @POST("auth")
    Call<AuthResponse> auth(@Header("Authorization") String authHeader);

    /**
     * Generate an image from a text prompt.
     * The signature is obtained from the /auth endpoint response.
     */
    @POST("generate_image")
    Call<ResponseBody> generateImage(
        @Header("Authorization") String authHeader,
        @Body GenerateRequest request
    );
}
