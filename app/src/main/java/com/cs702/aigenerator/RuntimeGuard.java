package com.cs702.aigenerator;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Debug;
import android.os.Looper;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

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

        if (!isMainThread() && hasFridaPort()) {
            reasons.add("Instrumentation port detected");
        }

        if (hasSuspiciousPackages(context)) {
            reasons.add("Suspicious instrumentation package installed");
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
}
