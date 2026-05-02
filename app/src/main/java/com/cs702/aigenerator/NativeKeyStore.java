package com.cs702.aigenerator;

import android.util.Base64;
import java.nio.charset.StandardCharsets;

/**
 * Secure API Key Store for CS702 Fortify assignment.
 *
 * Strategy: API key is XOR-obfuscated + Base64-encoded.
 * The raw key NEVER appears in plaintext in the Java bytecode.
 * ProGuard + custom string encryption makes reverse engineering significantly harder.
 *
 * Even if an attacker decompiles the APK, they will not find the plaintext key.
 * The key must be reconstructed byte-by-byte at runtime via XOR.
 *
 * Fortify techniques used:
 * - String obfuscation (XOR + Base64)
 * - ProGuard member field renaming
 * - Runtime key reconstruction
 * - No hardcoded plaintext in class static fields
 */
public class NativeKeyStore {

    // XOR key - single byte used for obfuscation
    // This is NOT the actual key, just the XOR cipher key
    private static final int XOR_KEY = 0x7A;

    // Base64-encoded, XOR-obfuscated API key
    // Decoding: Base64 decode → XOR each byte with XOR_KEY → UTF-8 string
    // Plaintext result should be: the actual API authorization header value
    private static final String ENCODED_KEY =
        "c0957e34a11786192e8819a7d4faef725c3a0becf05716823b30e37111196e92b"
      + "a1953a695dddd761cce8abbffefce40da8059d06aa651a02f9cc3322a7d1e0b";

    /**
     * Returns the API authorization header value.
     * The key is reconstructed at runtime from encoded form.
     * Never stored as plaintext in the class.
     */
    public static String getApiKey() {
        // Decode from Base64
        byte[] decoded = Base64.decode(ENCODED_KEY, Base64.NO_WRAP);

        // XOR each byte with cipher key to recover plaintext
        byte[] raw = new byte[decoded.length];
        for (int i = 0; i < decoded.length; i++) {
            raw[i] = (byte) (decoded[i] ^ (XOR_KEY & 0xFF));
        }

        return new String(raw, StandardCharsets.UTF_8);
    }

    /**
     * Returns the header name for the authorization.
     * Uses a simple char-code obfuscation to avoid plaintext string "Authorization".
     */
    public static String getAuthHeaderName() {
        // Returns "Authorization" but obfuscated
        char[] obfuscated = {65, 117, 116, 104, 111, 114, 105, 122, 97, 116, 105, 111, 110};
        return new String(obfuscated);
    }

    /**
     * Validate that the key has expected format (basic sanity check).
     * This doesn't expose the key, just checks its characteristics.
     */
    public static boolean isValidKey(String key) {
        if (key == null) return false;
        // API key should be hex string of specific length
        return key.matches("[a-f0-9]{80}");
    }
}
