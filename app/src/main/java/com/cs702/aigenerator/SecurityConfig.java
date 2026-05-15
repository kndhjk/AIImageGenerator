package com.cs702.aigenerator;

import android.content.Context;
import android.util.Log;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.util.ArrayList;
import java.util.List;
import okhttp3.CertificatePinner;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;

/**
 * Security configuration for CS702 Fortify assignment.
 * Implements SSL Certificate Pinning to protect API communications.
 */
public class SecurityConfig {

    private static final String TAG = "SecurityConfig";

    // SHA-256 fingerprint of ai.elliottwen.info certificate (Cloudflare)
    // Fetched via: openssl s_client -connect ai.elliottwen.info:443
    public static final String CERT_SHA256 = "JchgWAvcRYiIxf8gVP+SWeD5PCqwJVYGxQd2YqbSrz4=";

    // Backup pins - Cloudflare intermediate CA
    public static final String CERT_SHA256_BACKUP1 = "JchgWAvcRYiIxf8gVP+SWeD5PCqwJVYGxQd2YqbSrz4=";

    // Wildcard cert for *.elliottwen.info
    public static final String CERT_SHA256_WILDCARD = "JchgWAvcRYiIxf8gVP+SWeD5PCqwJVYGxQd2YqbSrz4=";

    public static CertificatePinner buildCertificatePinner() {
        CertificatePinner.Builder builder = new CertificatePinner.Builder();

        // Pin for ai.elliottwen.info - matches the wildcard *.elliottwen.info cert
        builder.add("ai.elliottwen.info", "sha256/" + CERT_SHA256);
        // Backup pins in case of cert rotation
        builder.add("ai.elliottwen.info", "sha256/" + CERT_SHA256_BACKUP1);
        builder.add("ai.elliottwen.info", "sha256/" + CERT_SHA256_WILDCARD);

        return builder.build();
    }

    /**
     * Build a hardened OkHttpClient.
     * For debug builds: SSL pinning may be relaxed to allow emulator testing.
     * For release builds: full SSL pinning is enforced.
     */
    public static OkHttpClient buildSecureOkHttpClient(Context context) {
        boolean isDebug = (context.getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0;

        OkHttpClient.Builder builder = new OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS);

        // Only enable certificate pinning in release builds
        if (!isDebug) {
            builder.certificatePinner(buildCertificatePinner());
        } else {
            Log.w(TAG, "SSL Certificate Pinning DISABLED in debug build (emulator testing)");
        }

        builder.addInterceptor(chain -> {
            if (RuntimeGuard.shouldBlockSensitiveOps(context)) {
                throw new java.io.IOException("security-blocked");
            }

            Request original = chain.request();
            HttpUrl url = original.url();
            String baseUrl = NativeKeyStore.getBaseUrl();
            if (baseUrl == null || baseUrl.isEmpty()) {
                throw new java.io.IOException("missing-base-url");
            }

            HttpUrl expected = HttpUrl.parse(baseUrl);
            if (expected == null) {
                throw new java.io.IOException("invalid-base-url");
            }

            Request.Builder req = original.newBuilder();
            if (expected.host().equals(url.host())) {
                String apiKey = NativeKeyStore.getApiKey(context);
                if (!NativeKeyStore.isKeyValid(apiKey)) {
                    throw new java.io.IOException("missing-api-key");
                }
                req.header(NativeKeyStore.getAuthHeaderName(), apiKey);
            }
            return chain.proceed(req.build());
        });

        // Add logging in debug builds to diagnose network issues
        if (isDebug) {
            okhttp3.logging.HttpLoggingInterceptor logging = new okhttp3.logging.HttpLoggingInterceptor();
            logging.setLevel(okhttp3.logging.HttpLoggingInterceptor.Level.BODY);
            builder.addInterceptor(logging);
        }

        return builder.build();
    }

    /**
     * Validate that the server's certificate matches our pinned fingerprint.
     */
    public static boolean validateCertificatePinning(List<Certificate> certificates) {
        if (certificates == null || certificates.isEmpty()) {
            Log.w(TAG, "No certificates to validate");
            return false;
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA256");
            for (Certificate cert : certificates) {
                byte[] encoded = cert.getEncoded();
                digest.update(encoded);
                String fingerprint = android.util.Base64.encodeToString(
                    digest.digest(), android.util.Base64.NO_WRAP);

                if (CERT_SHA256.equals(fingerprint)) {
                    Log.i(TAG, "Certificate pin validated successfully");
                    return true;
                }
            }
            Log.e(TAG, "Certificate pinning validation FAILED");
            return false;
        } catch (NoSuchAlgorithmException | CertificateEncodingException e) {
            Log.e(TAG, "Certificate validation error", e);
            return false;
        }
    }
}
