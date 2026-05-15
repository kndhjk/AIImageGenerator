package com.cs702.aigenerator;

import android.content.Context;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * API Client singleton with security hardening for Fortify assignment.
 *
 * Security features (release builds):
 * - SSL Certificate Pinning (prevents MITM attacks)
 * - Custom timeouts (prevents resource exhaustion)
 * - Redirect disabled (prevents DNS rebinding)
 *
 * Note: SSL pinning is automatically disabled in debug builds for emulator testing.
 */
public class ApiClient {

    private static ApiService apiService;

    public static synchronized ApiService getApiService(Context context) {
        if (apiService == null) {
            OkHttpClient client = SecurityConfig.buildSecureOkHttpClient(context);

            String baseUrl = NativeKeyStore.getBaseUrl();
            if (baseUrl == null || baseUrl.isEmpty()) {
                throw new IllegalStateException("baseUrl unavailable");
            }

            Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

            apiService = retrofit.create(ApiService.class);
        }
        return apiService;
    }

    public static synchronized void reset() {
        apiService = null;
    }
}
