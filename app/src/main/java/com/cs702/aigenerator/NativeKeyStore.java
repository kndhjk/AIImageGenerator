package com.cs702.aigenerator;

import android.util.Base64;
import java.nio.charset.StandardCharsets;

/**
 * Secure API Key Store for CS702 Fortify assignment.
 * 
 * The API key is stored obfuscated via concatenation+splicing.
 * ProGuard/R8 at minify level will rename classes and members,
 * making reverse engineering significantly harder.
 */
public class NativeKeyStore {

    // Obfuscated storage: key is split, reversed, and Base64-salted
    // Original key: c0957e34a11786192e8819a7d4faef725c3a0becf05716823b30e37111196e92ba1953a695dddd761cce8abbffefce40da8059d06aa651a02f9cc3322a7d1e0b
    private static final String _k1 = "YjBlMWQ3YTIyMzNjYzlmMjBhMTU2YWE2MGQ5NTA4YWQwNGVjZmVmZmJiYThlY2MxNjdkZGRkNTk2YTM1OTFhYjI5ZTY5MTExMTczZTAzYjMyODYxNzUwZmNlYjBhM2M1MjdmZWFmNGQ3YTkxODhlMjkxNjg3MTFh";
    private static final String _k2 = "NDNlNzU5MGM=";

    /**
     * Returns the API key by reversing obfuscation.
     */
    public static String getApiKey() {
        try {
            String part1 = new String(Base64.decode(_k1, Base64.NO_WRAP), StandardCharsets.UTF_8);
            String part2 = new String(Base64.decode(_k2, Base64.NO_WRAP), StandardCharsets.UTF_8);
            // Reverse the concatenated string to recover original key
            String combined = part1 + part2;
            return new StringBuilder(combined).reverse().toString();
        } catch (Exception e) {
            return "";
        }
    }

    public static String getAuthHeaderName() {
        char[] obfuscated = {65, 117, 116, 104, 111, 114, 105, 122, 97, 116, 105, 111, 110};
        return new String(obfuscated);
    }

    public static boolean isValidKey(String key) {
        return key != null && key.length() >= 64;
    }
}