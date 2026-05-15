package com.cs702.aigenerator;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import android.os.Debug;
import android.os.Looper;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Runtime hardening checks inspired by the layered vault-style approach.
 *
 * Goal: raise the cost of dynamic instrumentation / repackaging without
 * breaking normal development. Debug builds remain permissive.
 */
public final class RuntimeGuard {
    private RuntimeGuard() {}

    private static final int[] FRIDA_PORTS = {27042, 27043, 23946, 23947, 23948, 23949};
    private static final String[] MAP_KEYWORDS = {
            "frida", "gadget", "gum-js-loop", "frida-agent",
            "xposed", "substrate", "riru", "zygisk", "magisk"
    };
    private static final String EXPECTED_SIGNER_SHA256 = "FDA5FA694408194CE95AB6E1C7F5E1EC315BAF9EB5BC7C82A475416FEAED0356";

    private static final String[] SUSPICIOUS_PACKAGES = {
            "re.frida.server",
            "com.frida.server",
            "de.robv.android.xposed.installer",
            "org.meowcat.edxposed.manager",
            "com.saurik.substrate",
            "com.topjohnwu.magisk"
    };

    public static final class ThreatReport {
        public final boolean suspicious;
        public final List<String> reasons;

        ThreatReport(boolean suspicious, List<String> reasons) {
            this.suspicious = suspicious;
            this.reasons = reasons;
        }
    }

    public static boolean isDebugBuild(Context context) {
        return (context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }

    public static ThreatReport inspect(Context context) {
        List<String> reasons = new ArrayList<>();

        if (Debug.isDebuggerConnected() || Debug.waitingForDebugger()) {
            reasons.add("Debugger attached");
        }

        if (hasTracerPid()) {
            reasons.add("TracerPid is non-zero");
        }

        if (hasSuspiciousMaps()) {
            reasons.add("Suspicious runtime mappings detected");
        }

        if (hasSuspiciousThreadNames()) {
            reasons.add("Suspicious runtime thread names detected");
        }

        if (hasSuspiciousEnvironment()) {
            reasons.add("Suspicious process environment detected");
        }

        if (hasSuspiciousStackFrames()) {
            reasons.add("Suspicious stack frames detected");
        }

        if (!isMainThread() && hasFridaPort()) {
            reasons.add("Instrumentation port detected");
        }

        if (hasSuspiciousPackages(context)) {
            reasons.add("Suspicious instrumentation package installed");
        }

        if (!hasExpectedSigningCert(context)) {
            reasons.add("Unexpected APK signing certificate");
        }

        if (!hasExpectedNativeLibrary(context)) {
            reasons.add("Native library integrity check failed");
        }

        if (!hasExpectedDexDigest(context)) {
            reasons.add("classes.dex integrity check failed");
        }

        if (!hasExpectedApkEntryDigest(context, "AndroidManifest.xml", NativeKeyStore.getExpectedManifestSha256())) {
            reasons.add("AndroidManifest.xml integrity check failed");
        }

        if (!hasExpectedApkEntryDigest(context, "resources.arsc", NativeKeyStore.getExpectedResourcesArscSha256())) {
            reasons.add("resources.arsc integrity check failed");
        }

        if (RootDetector.check(context).isRooted) {
            reasons.add("Root / Magisk indicators present");
        }

        return new ThreatReport(!reasons.isEmpty(), reasons);
    }

    public static boolean shouldBlockSensitiveOps(Context context) {
        if (isDebugBuild(context)) return false;
        return inspect(context).suspicious;
    }

    private static boolean isMainThread() {
        return Looper.getMainLooper() != null && Looper.myLooper() == Looper.getMainLooper();
    }

    private static boolean hasFridaPort() {
        for (int port : FRIDA_PORTS) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress("127.0.0.1", port), 120);
                return true;
            } catch (IOException ignored) {
            }
        }
        return false;
    }

    private static boolean hasSuspiciousMaps() {
        File maps = new File("/proc/self/maps");
        if (!maps.exists()) return false;
        try (BufferedReader reader = new BufferedReader(new FileReader(maps))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String lower = line.toLowerCase();
                for (String keyword : MAP_KEYWORDS) {
                    if (lower.contains(keyword)) return true;
                }
            }
        } catch (IOException ignored) {
        }
        return false;
    }

    private static boolean hasTracerPid() {
        File status = new File("/proc/self/status");
        if (!status.exists()) return false;
        try (BufferedReader reader = new BufferedReader(new FileReader(status))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("TracerPid:")) {
                    String value = line.substring("TracerPid:".length()).trim();
                    return !"0".equals(value);
                }
            }
        } catch (IOException ignored) {
        }
        return false;
    }

    private static boolean hasSuspiciousThreadNames() {
        File taskDir = new File("/proc/self/task");
        File[] tasks = taskDir.listFiles();
        if (tasks == null) return false;

        for (File task : tasks) {
            File comm = new File(task, "comm");
            if (!comm.exists()) continue;
            try (BufferedReader reader = new BufferedReader(new FileReader(comm))) {
                String name = reader.readLine();
                if (name == null) continue;
                String lower = name.toLowerCase();
                for (String keyword : MAP_KEYWORDS) {
                    if (lower.contains(keyword)) return true;
                }
            } catch (IOException ignored) {
            }
        }
        return false;
    }

    private static boolean hasSuspiciousEnvironment() {
        String[] keys = {"LD_PRELOAD", "FRIDA_VERSION", "FRIDA_PORT", "XPOSED_ENABLED"};
        for (String key : keys) {
            String value = System.getenv(key);
            if (value != null && !value.trim().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasSuspiciousPackages(Context context) {
        PackageManager pm = context.getPackageManager();
        for (String pkg : SUSPICIOUS_PACKAGES) {
            try {
                pm.getPackageInfo(pkg, 0);
                return true;
            } catch (PackageManager.NameNotFoundException ignored) {
            }
        }
        return false;
    }

    private static boolean hasSuspiciousStackFrames() {
        for (StackTraceElement[] trace : Thread.getAllStackTraces().values()) {
            for (StackTraceElement element : trace) {
                String cls = element.getClassName();
                if (cls == null) continue;
                String lower = cls.toLowerCase();
                for (String keyword : MAP_KEYWORDS) {
                    if (lower.contains(keyword)) return true;
                }
            }
        }
        return false;
    }

    private static boolean hasExpectedSigningCert(Context context) {
        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo info;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info = pm.getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES);
                if (info.signingInfo == null) return false;
                Signature[] signatures = info.signingInfo.hasMultipleSigners()
                    ? info.signingInfo.getApkContentsSigners()
                    : info.signingInfo.getSigningCertificateHistory();
                return matchesAnySignature(signatures);
            }

            info = pm.getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNATURES);
            return matchesAnySignature(info.signatures);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean matchesAnySignature(Signature[] signatures) {
        if (signatures == null || signatures.length == 0) return false;
        for (Signature signature : signatures) {
            String digest = sha256Hex(signature.toByteArray());
            if (EXPECTED_SIGNER_SHA256.equals(digest)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasExpectedDexDigest(Context context) {
        String expectedDigest = NativeKeyStore.getExpectedClassesDexSha256();
        if (expectedDigest == null || expectedDigest.isEmpty() || expectedDigest.startsWith("TO_BE_FILLED_")) {
            return false;
        }
        try (ZipFile zip = new ZipFile(context.getApplicationInfo().sourceDir)) {
            ZipEntry entry = zip.getEntry("classes.dex");
            if (entry == null) return false;
            byte[] data = readAllBytes(zip.getInputStream(entry));
            normalizeDexDigestBytes(data, expectedDigest);
            String digest = sha256Hex(data);
            return expectedDigest.equals(digest);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static void normalizeDexDigestBytes(byte[] data, String marker) {
        if (data == null || marker == null || marker.isEmpty()) return;
        byte[] needle;
        try {
            needle = marker.getBytes("UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            return;
        }
        for (int i = 0; i <= data.length - needle.length; i++) {
            boolean match = true;
            for (int j = 0; j < needle.length; j++) {
                if (data[i + j] != needle[j]) {
                    match = false;
                    break;
                }
            }
            if (match) {
                for (int j = 0; j < needle.length; j++) {
                    data[i + j] = '0';
                }
            }
        }
    }

    private static boolean hasExpectedApkEntryDigest(Context context, String entryName, String expectedDigest) {
        if (expectedDigest == null || expectedDigest.startsWith("TO_BE_FILLED_")) {
            return false;
        }
        try (ZipFile zip = new ZipFile(context.getApplicationInfo().sourceDir)) {
            ZipEntry entry = zip.getEntry(entryName);
            if (entry == null) return false;
            String digest = sha256Hex(readAllBytes(zip.getInputStream(entry)));
            return expectedDigest.equals(digest);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean hasExpectedNativeLibrary(Context context) {
        try {
            String nativeDir = context.getApplicationInfo().nativeLibraryDir;
            String sourceApk = context.getApplicationInfo().sourceDir;
            if (nativeDir == null || sourceApk == null) return false;

            File extracted = new File(nativeDir, "libnative-key.so");
            if (!extracted.exists() || extracted.length() <= 0) return false;

            String fileDigest = sha256Hex(readAllBytes(extracted));
            if (fileDigest.isEmpty()) return false;

            try (ZipFile zip = new ZipFile(sourceApk)) {
                for (String abi : Build.SUPPORTED_ABIS) {
                    ZipEntry entry = zip.getEntry("lib/" + abi + "/libnative-key.so");
                    if (entry == null) continue;
                    String zipDigest = sha256Hex(readAllBytes(zip.getInputStream(entry)));
                    return fileDigest.equals(zipDigest);
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private static byte[] readAllBytes(File file) throws IOException {
        try (InputStream in = new FileInputStream(file)) {
            return readAllBytes(in);
        }
    }

    private static byte[] readAllBytes(InputStream in) throws IOException {
        byte[] buffer = new byte[8192];
        int read;
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private static String sha256Hex(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString().toUpperCase();
        } catch (NoSuchAlgorithmException e) {
            return "";
        }
    }
}
