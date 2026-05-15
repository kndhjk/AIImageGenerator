package com.cs702.aigenerator;

import android.content.Context;

/**
 * Native-only API key storage.
 *
 * Real key material lives only in C/JNI and is reconstructed at runtime from
 * custom encoded fragments. This is hardening, not perfect secrecy.
 */
public final class NativeKeyStore {

    private static final int REQUIRED_LENGTH = 128;

    private static boolean nativeLoaded = false;

    static {
        try {
            System.loadLibrary("native-key");
            nativeLoaded = true;
        } catch (UnsatisfiedLinkError e) {
            nativeLoaded = false;
        }
    }

    private NativeKeyStore() {}

    private static native String buildNativeKey();
    private static native int verifyNative(String key);
    private static native int nativeRuntimeSafe();
    private static native String nativeExpectedPackage();
    private static native String nativeBaseUrl();
    private static native String nativeAuthHeaderName();
    private static native String nativeAuthPath();
    private static native String nativeGeneratePath();
    private static native String nativeExpectedClassesDexSha256();
    private static native String nativeExpectedManifestSha256();
    private static native String nativeExpectedResourcesArscSha256();

    public static String getApiKey() {
        if (!nativeLoaded || nativeRuntimeSafe() != 1) return "";
        try {
            String key = buildNativeKey();
            return isKeyValid(key) ? key : "";
        } catch (Exception e) {
            return "";
        }
    }

    public static String getApiKey(Context context) {
        if (context == null || !nativeLoaded) return "";
        if (!nativeExpectedPackage().equals(context.getPackageName())) return "";
        if (RuntimeGuard.shouldBlockSensitiveOps(context)) return "";

        String key = getApiKey();
        if (!isKeyValid(key)) return "";
        return key;
    }

    public static boolean isKeyValid(String key) {
        if (key == null || key.length() != REQUIRED_LENGTH) return false;
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) {
                return false;
            }
        }
        return nativeLoaded && nativeRuntimeSafe() == 1 && verifyNative(key) == 1;
    }

    public static String getAuthHeaderName() {
        if (!nativeLoaded || nativeRuntimeSafe() != 1) return "";
        return nativeAuthHeaderName();
    }

    public static String getAuthPath() {
        if (!nativeLoaded || nativeRuntimeSafe() != 1) return "";
        return nativeAuthPath();
    }

    public static String getGeneratePath() {
        if (!nativeLoaded || nativeRuntimeSafe() != 1) return "";
        return nativeGeneratePath();
    }

    public static String getBaseUrl() {
        if (!nativeLoaded || nativeRuntimeSafe() != 1) return "";
        return nativeBaseUrl();
    }

    public static String getExpectedClassesDexSha256() {
        if (!nativeLoaded || nativeRuntimeSafe() != 1) return "";
        return nativeExpectedClassesDexSha256();
    }

    public static String getExpectedManifestSha256() {
        if (!nativeLoaded || nativeRuntimeSafe() != 1) return "";
        return nativeExpectedManifestSha256();
    }

    public static String getExpectedResourcesArscSha256() {
        if (!nativeLoaded || nativeRuntimeSafe() != 1) return "";
        return nativeExpectedResourcesArscSha256();
    }
}
