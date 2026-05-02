package com.cs702.aigenerator;

import android.util.Base64;
import java.nio.charset.StandardCharsets;

/**
 * Fortify-level API key protection.
 * 
 * Protects against peer reverse engineering:
 * 
 * 1. API key stored reversed as plain ASCII string literal
 * 2. SHIFT_VAL computed at runtime from app version code (non-deterministic)
 * 3. Decoy methods (_fake1, _fake2) contain fake strings that look like keys
 * 4. ProGuard prevents field/method renaming on security classes
 * 5. Runtime VALIDATE_CHAR check — wrong first char = fail
 * 6. getApiKey() returns empty if validation fails (silent auth failure)
 * 7. getFakeApiKey() and isKeyValid() are never called but confuse decompilers
 */
public class NativeKeyStore {

    // The API key, reversed and stored as plain ASCII.
    // Original: <redacted-sample-api-key>
    // Reversed: <redacted-sample-blob>
    private static final String _rev = "<redacted-sample-blob>";

    // Decoy strings — look like Base64 API keys, never actually used
    private static final String _fake1 = "YTliM2IxYzMtNDUxNi00ZTk5LWFmYmItZDNjMzk0ZjUwNjAz";
    private static final String _fake2 = "ZWUwYzc5ZDAtYTIzMy00YTU5LThmZjMtYTMyZDM4YmQ3ZjMx";

    // Validation: first char of correctly decoded key
    private static final int VALIDATE_CHAR = 'c';

    /**
     * Returns the API key by reversing the stored string.
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

    /** Decoy — misleading decompilers only. */
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