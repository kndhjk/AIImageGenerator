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
    private static final String EXPECTED_PACKAGE = "com.cs702.aigenerator";

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

    public static String getApiKey() {
        if (!nativeLoaded) return "";
        try {
            String key = buildNativeKey();
            return isKeyValid(key) ? key : "";
        } catch (Exception e) {
            return "";
        }
    }

    public static String getApiKey(Context context) {
        if (context == null || !nativeLoaded) return "";
        if (!EXPECTED_PACKAGE.equals(context.getPackageName())) return "";
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
        return nativeLoaded && verifyNative(key) == 1;
    }

    public static String getAuthHeaderName() {
        char[] obfuscated = {65, 117, 116, 104, 111, 114, 105, 122, 97, 116, 105, 111, 110};
        return new String(obfuscated);
    }
}
