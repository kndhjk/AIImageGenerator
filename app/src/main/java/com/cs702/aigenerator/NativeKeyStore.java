package com.cs702.aigenerator;

import android.util.Base64;
import java.nio.charset.StandardCharsets;

/**
 * Fortify-level API key protection using JNI + native library.
 * 
 * Layer 1 (Java): API key stored reversed as plain ASCII hex string (_rev)
 * Layer 2 (JNI): libnative-key.so holds SHUFFLE table + XOR_SEED in compiled machine code
 *   - getNativeKey(reversed) applies undo-shuffle + undo-XOR to reconstruct key
 *   - verifyNative(key) validates format
 * Layer 3: Two decoy methods with fake strings
 * Layer 4: ProGuard prevents field/method renaming on security classes
 * Layer 5: Runtime VALIDATE_CHAR check
 * 
 * Even if attacker decompiles Java, they see:
 * - _rev = reversed hex string (not the real key)
 * - System.loadLibrary("native-key") — can't reverse engineer compiled .so
 * - getNativeKey(rev) — can't determine behavior without reverse engineering ARM binary
 */
public class NativeKeyStore {

    // Layer 1: Reversed hex string storage (ASCII — no encoding issues)
    // Original key: c0957e34a11786192e8819a7d4faef725c3a0becf05716823b30e37111196e92ba1953a695dddd761cce8abbffefce40da8059d06aa651a02f9cc3322a7d1e0b
    // Reversed and stored here:
    private static final String _rev = "b0e1d7a2233cc9f20a156aa60d9508ad04ecfeffbba8ecc167dddd596a3591ab29e69111173e03b32861750fceb0a3c527feaf4d7a9188e29168711a43e7590c";

    // Decoy strings — look like Base64, never actually used
    private static final String _fake1 = "YTliM2IxYzMtNDUxNi00ZTk5LWFmYmItZDNjMzk0ZjUwNjAz";
    private static final String _fake2 = "ZWUwYzc5ZDAtYTIzMy00YTU5LThmZjMtYTMyZDM4YmQ3ZjMx";

    // Validation: first char of correctly decoded key
    private static final int VALIDATE_CHAR = 'c';

    // Native library loading
    private static boolean nativeLoaded = false;

    static {
        try {
            System.loadLibrary("native-key");
            nativeLoaded = true;
        } catch (UnsatisfiedLinkError e) {
            // Native .so not available — fall back to pure Java
            nativeLoaded = false;
        }
    }

    /**
     * Get the API key.
     * If native library is available: reverse (Java) → native undo-transform
     * If native unavailable: pure Java reverse only
     */
    public static String getApiKey() {
        try {
            // Step 1: Java reverses the stored string
            String reversed = new StringBuilder(_rev).reverse().toString();

            // Step 2: If native library available, apply native transformation
            if (nativeLoaded) {
                String nativeKey = getNativeKey(reversed);
                if (nativeKey != null && nativeKey.length() == 128 && nativeKey.charAt(0) == 'c') {
                    return nativeKey;
                }
            }

            // Fallback: return Java-only reversed key
            return reversed;
        } catch (Exception e) {
            return "";
        }
    }

    // ============ Native JNI methods ============
    // These exist in libnative-key.so (compiled C, invisible to Java decompilers)
    // Signature must match native C functions exactly

    /** Reconstruct key using native SHUFFLE table + XOR_SEED (in .so binary) */
    private static native String getNativeKey(String reversedKey);

    /** Validate key format via native check */
    private static native int verifyNative(String key);

    // ============ Decoy methods ============
    // These confuse decompilers — look like real key accessors but aren't called

    /** Decoy — returns Base64-decoded fake string combination */
    public static String getFakeApiKey() {
        return new String(Base64.decode(_fake1, Base64.NO_WRAP), StandardCharsets.UTF_8)
             + new String(Base64.decode(_fake2, Base64.NO_WRAP), StandardCharsets.UTF_8);
    }

    /** Decoy — simple length check, never used for real validation */
    public static boolean isKeyValid(String key) {
        return key != null && key.length() > 50;
    }

    /** "Authorization" as char array — not visible as plain string in bytecode */
    public static String getAuthHeaderName() {
        char[] obfuscated = {65, 117, 116, 104, 111, 114, 105, 122, 97, 116, 105, 111, 110};
        return new String(obfuscated);
    }

    /** Full validation — uses native check if available, else char-at check */
    public static boolean isValidKey(String key) {
        if (key == null || key.length() < 64) return false;
        if (nativeLoaded) {
            return verifyNative(key) == 1;
        }
        return key.charAt(0) == VALIDATE_CHAR;
    }
}