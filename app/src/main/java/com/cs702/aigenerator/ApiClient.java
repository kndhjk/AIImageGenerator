package com.cs702.aigenerator;

import android.content.Context;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * API Client singleton with security hardening for Fortify assignment.
 *
 * Security features enabled:
 * - SSL Certificate Pinning (prevents MITM attacks)
 * - Custom timeouts (prevents resource exhaustion)
 * - Logging interceptor removed in release builds
 */
public class ApiClient {

    private static final String BASE_URL = "https://ai.elliottwen.info/";
    private static ApiService apiService;

    public static synchronized ApiService getApiService(Context context) {
        if (apiService == null) {

            // Build hardened OkHttpClient with SSL Pinning
            OkHttpClient client = SecurityConfig.buildSecureOkHttpClient(context);

            // Add logging interceptor only for debug builds
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            logging.setLevel(HttpLoggingInterceptor.Level.BODY);

            // In release, we don't add logging interceptor to avoid leaking sensitive data
            client = client.newBuilder()
                // Keep certificate pinner from SecurityConfig
                .certificatePinner(SecurityConfig.buildCertificatePinner())
                // Disable redirect to prevent DNS rebinding
                .followRedirects(false)
                .followSslRedirects(false)
                .build();

            Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
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
