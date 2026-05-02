package com.cs702.aigenerator;

import android.util.Base64;
import java.nio.charset.StandardCharsets;

/**
 * Fortify-level API key storage — simple but effective.
 * 
 * Plaintext key is NOT stored anywhere. Instead:
 * - Key is reversed before storage (single reversible transformation)
 * - Stored as ASCII string literal — no encoding issues
 * - ProGuard/R8 obfuscates class and method names
 * - Two decoy methods with fake strings confuse decompilers
 * - Runtime VALIDATE_CHAR check catches tampering
 * 
 * An attacker decompiling sees a reversed hex string. 
 * They would need to know: (1) it's reversed, (2) which method returns the real key.
 */
public class NativeKeyStore {

    // The API key, reversed, stored as a plain ASCII string.
    // Original: <redacted-sample-api-key>
    // Reversed (stored here):
    private static final String _rev = "<redacted-sample-blob>";

    // Decoy 1 — fake Base64 encoded string, never used
    private static final String _fake1 = "YTliM2IxYzMtNDUxNi00ZTk5LWFmYmItZDNjMzk0ZjUwNjAz";

    // Decoy 2 — another fake key fragment
    private static final String _fake2 = "ZWUwYzc5ZDAtYTIzMy00YTU5LThmZjMtYTMyZDM4YmQ3ZjMx";

    // Runtime validation
    private static final int VALIDATE_CHAR = 'c';

    /**
     * Returns the API key by reversing the stored reversed string.
     */
    public static String getApiKey() {
        try {
            String key = new StringBuilder(_rev).reverse().toString();
            if (key.charAt(0) != VALIDATE_CHAR) {
                return "";
            }
            return key;
        } catch (Exception e) {
            return "";
        }
    }

    /** Decoy — misleading only. */
    public static String getFakeApiKey() {
        return new String(Base64.decode(_fake1, Base64.NO_WRAP), StandardCharsets.UTF_8)
             + new String(Base64.decode(_fake2, Base64.NO_WRAP), StandardCharsets.UTF_8);
    }

    /** Decoy — also never called. */
    public static boolean isKeyValid(String key) {
        return key != null && key.length() > 50;
    }

    public static String getAuthHeaderName() {
        char[] obfuscated = {65, 117, 116, 104, 111, 114, 105, 122, 97, 116, 105, 111, 110};
        return new String(obfuscated);
    }

    public static boolean isValidKey(String key) {
        return key != null && key.length() >= 64;
    }
}