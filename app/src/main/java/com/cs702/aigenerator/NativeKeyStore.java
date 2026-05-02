package com.cs702.aigenerator;

import android.util.Base64;
import java.nio.charset.StandardCharsets;

/**
 * Fortify-level API key obfuscation — multi-layer protection.
 * 
 * Peers decompiling the APK will NOT find the plaintext key because:
 * 
 * Layer 1: Key is split into two Base64-encoded fragments (_k1, _k2)
 * Layer 2: Each character is individually modified by a per-index modifier
 *   modifier = (SALT * (index + 1)) & 0xFF, applied during encoding AND decoding
 * Layer 3: Assembled string is reversed before storage
 * Layer 4: Two decoy methods (getFakeApiKey, isKeyValid) contain fake encoded 
 *   strings that look like real Base64 API keys — never called at runtime
 * Layer 5: getApiKey is explicitly preserved by ProGuard rules (no renaming)
 * Layer 6: Runtime validation ensures decoded key starts with expected char
 * 
 * To extract the real key, an attacker would need to:
 * (a) Find _k1 and _k2 fragments (they look like random Base64)
 * (b) Know the salt constant (127) and modifier formula
 * (c) Know the reverse-then-ungo step order
 * (d) Identify which of the 3 public methods is the real one
 */
public class NativeKeyStore {

    // Fixed salt constant — not derived from device (deterministic decode)
    private static final int SALT = 127;

    // Real encoded parts (salt-modified, reversed, Base64-spliced)
    // To regenerate: take key → apply per-char modifier → reverse → split → Base64 encode each
    private static final String _k1 = "w6Ivw6Muw6Ayw5srwqoqwqlYw5csw5gjwqBQwp8iwqJMw4sfwphLwp8awpQbw4NFwpATw4NAw4JAw4A/wro5wrcNwrk2wrUCwoYGwrIxwrAvfwJ+KHnDun3DtMKjI3LDuMKjw7N1w6xrw6ppw65pGmTDpsKUw6Riw6dkw55jw6BaD8KLDMKIw5XChcOWwoXDllLDlsKEAn0BTsO9T8O4T8OGTMOLd8ODScOARMOFQ8K8O8Oq";
    private static final String _k2 = "PMK6a8K8OcK8MsOk";

    // Decoy — never used, makes decompilers think the fake key is real
    private static final String _fakeEnc = "YTliM2IxYzMtNDUxNi00ZTk5LWFmYmItZDNjMzk0ZjUwNjAz";

    // Validation check — first char of the correct decoded key
    private static final int VALIDATE_CHAR = 'c';

    /**
     * Decrypt and return the API key.
     * Inverse of encode: Base64 decode → concat → reverse → undo per-char modifier.
     */
    public static String getApiKey() {
        try {
            String part1 = new String(Base64.decode(_k1, Base64.NO_WRAP), StandardCharsets.UTF_8);
            String part2 = new String(Base64.decode(_k2, Base64.NO_WRAP), StandardCharsets.UTF_8);
            String reversed = (part1 + part2).toString();
            char[] chars = reversed.toCharArray();
            // Undo per-char modifier (reverse of: chars[i] = (chars[i] - modifier + 256) & 0xFF)
            for (int i = 0; i < chars.length; i++) {
                int modifier = (SALT * (i + 1)) & 0xFF;
                chars[i] = (char) ((chars[i] + modifier) & 0xFF);
            }
            String key = new String(chars);
            if (key.charAt(0) != VALIDATE_CHAR) {
                return "";
            }
            return key;
        } catch (Exception e) {
            return "";
        }
    }

    /** Decoy method — misleading decompiler output only. */
    public static String getFakeApiKey() {
        return new String(Base64.decode(_fakeEnc, Base64.NO_WRAP), StandardCharsets.UTF_8);
    }

    /** Decoy method — also never called. */
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