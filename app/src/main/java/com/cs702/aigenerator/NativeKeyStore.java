package com.cs702.aigenerator;

import android.content.Context;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Vault-inspired local secret storage.
 *
 * The real Authorization key is no longer stored as a directly reversible hex blob.
 * Instead we keep:
 * - reversed/split AES-GCM ciphertext
 * - reversed/split IV
 * - split seed fragments used to derive the AES key at runtime
 *
 * This is still not perfect against determined reverse engineering, but it removes the
 * old test key from source and raises extraction cost meaningfully.
 */
public class NativeKeyStore {

    private static final String EXPECTED_PACKAGE = "com.cs702.aigenerator";

    // Reversed ciphertext+tag, split into uneven chunks to avoid a single obvious blob.
    private static final String[] CIPHER_SEGMENTS = {
        "e6eee143aea77c44cb829198dbe4ea1d2601690a",
        "fdf14ce2824a54153176cdd4181863c90c0a43cb",
        "a05b1603f69b519e358cd68b2c4ef478059b42f9",
        "84e6122e47473ccc7164031c978b1a05082b9403",
        "492a6224cb2755b430dd66be7f96188c61a223df",
        "ef755db8b9220f926995ef3862da66903c731282",
        "78da12e5fbeaaad7316a030bc1b960b72ef472ef",
        "c3d500f2"
    };

    // Reversed IV, also split.
    private static final String[] IV_SEGMENTS = {
        "7db7d675",
        "5df02bab",
        "536bae88"
    };

    // Seed fragments (runtime order differs from source order).
    private static final String PART_B = "Petal";
    private static final String PART_D = "Canvas";
    private static final String PART_A = "Vault";
    private static final String PART_E = "Mirror";
    private static final String PART_C = "Nebula";

    // Decoys — never used for the real key.
    private static final String FAKE_1 = "YTliM2IxYzMtNDUxNi00ZTk5LWFmYmItZDNjMzk0ZjUwNjAz";
    private static final String FAKE_2 = "ZWUwYzc5ZDAtYTIzMy00YTU5LThmZjMtYTMyZDM4YmQ3ZjMx";

    private static boolean nativeLoaded = false;

    static {
        try {
            System.loadLibrary("native-key");
            nativeLoaded = true;
        } catch (UnsatisfiedLinkError e) {
            nativeLoaded = false;
        }
    }

    public static String getApiKey() {
        try {
            return decryptStoredKey();
        } catch (Exception e) {
            return "";
        }
    }

    public static String getApiKey(Context context) {
        if (context == null) return getApiKey();
        if (!EXPECTED_PACKAGE.equals(context.getPackageName())) {
            return "";
        }
        if (RuntimeGuard.shouldBlockSensitiveOps(context)) {
            return "";
        }
        return getApiKey();
    }

    private static String decryptStoredKey() throws Exception {
        byte[] aesKey = deriveAesKey();
        byte[] iv = hexToBytes(reverse(join(IV_SEGMENTS)));
        byte[] cipherBlob = hexToBytes(reverse(join(CIPHER_SEGMENTS)));

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        SecretKeySpec keySpec = new SecretKeySpec(aesKey, "AES");
        GCMParameterSpec gcmSpec = new GCMParameterSpec(128, iv);
        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec);

        byte[] plain = cipher.doFinal(cipherBlob);
        return new String(plain, StandardCharsets.UTF_8);
    }

    private static byte[] deriveAesKey() throws Exception {
        String seed = PART_C + "#" + PART_A + "@" + PART_E + "!" + PART_B + "$" + PART_D + "%" + EXPECTED_PACKAGE;
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return digest.digest(seed.getBytes(StandardCharsets.UTF_8));
    }

    private static String join(String[] segments) {
        StringBuilder sb = new StringBuilder();
        for (String segment : segments) sb.append(segment);
        return sb.toString();
    }

    private static String reverse(String value) {
        return new StringBuilder(value).reverse().toString();
    }

    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] out = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            out[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                + Character.digit(hex.charAt(i + 1), 16));
        }
        return out;
    }

    public static String getFakeApiKey() {
        return new String(Base64.decode(FAKE_1, Base64.NO_WRAP), StandardCharsets.UTF_8)
             + new String(Base64.decode(FAKE_2, Base64.NO_WRAP), StandardCharsets.UTF_8);
    }

    public static boolean isKeyValid(String key) {
        if (key == null || key.length() != 128) return false;
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            boolean isHex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
            if (!isHex) return false;
        }
        return true;
    }

    public static String getAuthHeaderName() {
        char[] obfuscated = {65, 117, 116, 104, 111, 114, 105, 122, 97, 116, 105, 111, 110};
        return new String(obfuscated);
    }

    public static boolean hasNativeLayer() {
        return nativeLoaded;
    }
}
