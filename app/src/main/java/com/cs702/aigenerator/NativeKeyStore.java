package com.cs702.aigenerator;

import android.content.Context;
import android.util.Base64;
import java.nio.charset.StandardCharsets;

/**
 * Layered API key protection.
 *
 * Notes:
 * - raises reverse-engineering cost but does not make extraction impossible
 * - debug builds stay usable; release builds become stricter
 * - storage is split into fragments to reduce obvious one-string recovery
 */
public class NativeKeyStore {

    // Original encoded blob is split and reordered to reduce easy recovery from one field.
    private static final String _segB = "<redacted-seg-b>";
    private static final String _segD = "<redacted-seg-d>";
    private static final String _segA = "<redacted-seg-a>";
    private static final String _segC = "<redacted-seg-c>";

    // Decoys — never used for the real key.
    private static final String _fake1 = "YTliM2IxYzMtNDUxNi00ZTk5LWFmYmItZDNjMzk0ZjUwNjAz";
    private static final String _fake2 = "ZWUwYzc5ZDAtYTIzMy00YTU5LThmZjMtYTMyZDM4YmQ3ZjMx";

    private static final int VALIDATE_CHAR = 'c';
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

    private static String getEncodedBlob() {
        return _segA + _segB + _segC + _segD;
    }

    public static String getApiKey() {
        try {
            String reversed = new StringBuilder(getEncodedBlob()).reverse().toString();
            if (nativeLoaded) {
                String nativeKey = getNativeKey(reversed);
                if (isKeyValid(nativeKey)) {
                    return nativeKey;
                }
            }
            return reversed;
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Context-aware accessor for production use.
     * Release builds will refuse to return the key under obvious hook/tamper conditions.
     */
    public static String getApiKey(Context context) {
        if (context == null) return getApiKey();
        if (!EXPECTED_PACKAGE.equals(context.getPackageName())) {
            return "";
        }
        if (RuntimeGuard.shouldBlockSensitiveOps(context)) {
            return "";
        }

        String key = getApiKey();
        if (RuntimeGuard.isDebugBuild(context)) {
            return key;
        }

        // In release builds, require the native layer to participate.
        if (!nativeLoaded) {
            return "";
        }
        return key;
    }

    private static native String getNativeKey(String reversedKey);
    private static native int verifyNative(String key);

    public static String getFakeApiKey() {
        return new String(Base64.decode(_fake1, Base64.NO_WRAP), StandardCharsets.UTF_8)
             + new String(Base64.decode(_fake2, Base64.NO_WRAP), StandardCharsets.UTF_8);
    }

    public static boolean isKeyValid(String key) {
        if (key == null || key.length() < 64) return false;
        if (nativeLoaded) {
            return verifyNative(key) == 1;
        }
        return key.charAt(0) == VALIDATE_CHAR;
    }

    public static String getAuthHeaderName() {
        char[] obfuscated = {65, 117, 116, 104, 111, 114, 105, 122, 97, 116, 105, 111, 110};
        return new String(obfuscated);
    }
}
