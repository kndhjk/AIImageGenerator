package com.cs702.aigenerator;

import android.content.Context;
import android.util.Log;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.util.ArrayList;
import java.util.List;
import javax.net.ssl.CertificatePinner;
import okhttp3.CertificatePinner.Builder;
import okhttp3.OkHttpClient;

/**
 * Security configuration for CS702 Fortify assignment.
 * Implements SSL Certificate Pinning to protect API communications.
 */
public class SecurityConfig {

    private static final String TAG = "SecurityConfig";

    // SHA-256 fingerprint of ai.elliottwen.info certificate (Cloudflare)
    // Fetched via: openssl s_client -connect ai.elliottwen.info:443
    public static final String CERT_SHA256 = "3rZyrzZdM7XRbcJRlxhhiA0TstYV7KKtUnolImZIRHI=";

    // Backup pins in case certificate rotation happens
    public static final String CERT_SHA256_BACKUP1 = "3rZyrzZdM7XRbcJRlxhhiA0TstYV7KKtUnolImZIRHI=";

    public static CertificatePinner buildCertificatePinner() {
        return new CertificatePinner.Builder()
            // Primary pin - Cloudflare Origin cert for ai.elliottwen.info
            .add("ai.elliottwen.info", "sha256/" + CERT_SHA256)
            // Backup pin - same cert, same key (Cloudflare changes this on rotation)
            .add("ai.elliottwen.info", "sha256/" + CERT_SHA256_BACKUP1)
            // Also pin the Cloudflare intermediate CA as backup
            .add("ai.elliottwen.info", "sha256/GrXIkJaICZfPJ5qR8aPBzPAjMX8Vrl7gB0pKmwx0eWA=")
            .build();
    }

    /**
     * Build a hardened OkHttpClient with certificate pinning and timeout configs.
     */
    public static OkHttpClient buildSecureOkHttpClient(Context context) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .certificatePinner(buildCertificatePinner());

        return builder.build();
    }

    /**
     * Validate that the server's certificate matches our pinned fingerprint.
     * This is an additional runtime check beyond OkHttp's built-in pinning.
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
            Log.e(TAG, "Certificate pinning validation FAILED - no matching pin found");
            return false;
        } catch (NoSuchAlgorithmException | CertificateEncodingException e) {
            Log.e(TAG, "Certificate validation error", e);
            return false;
        }
    }
}
