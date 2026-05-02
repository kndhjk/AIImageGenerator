package com.cs702.aigenerator;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;

/**
 * Root Detection utility for CS702 Fortify assignment.
 * Enhanced version with better Magisk/Zygisk detection.
 */
public class RootDetector {

    // Basic paths checked in original version
    private static final String[] ROOT_PATHS = {
        "/system/app/Superuser.apk",
        "/sbin/su",
        "/system/bin/su",
        "/system/xbin/su",
        "/data/local/xbin/su",
        "/data/local/bin/su",
        "/system/sd/xbin/su",
        "/system/bin/failsafe/su",
        "/data/local/su",
        "/su/bin/su",
        "/su/bin",
        "/system/xbin/daemonsu",
        "/system/lib/liblzma.so",
    };

    private static final String[] ROOT_PACKAGES = {
        "com.topjohnwu.magisk",
        "eu.chainfire.supersu",
        "com.koushikdutta.superuser",
        "com.noshufou.android.su",
        "com.noshufou.android.su.elite",
        "com.yellowes.su",
        "com.kingroot.kinguser",
        "com.kingo.root",
        "com.smedialink.oneclickroot",
        "com.zhiqupk.root.global",
        "com.alephzain.framaroot",
    };

    private static final String[] DANGEROUS_APPS = {
        "com.chelpus.lackypatch",
        "com.devadvance.rootcloak",
        "com.devadvance.rootcloakplus",
        "de.robv.android.xposed.installer",
        "com.saurik.substrate",
        "com.amphoras.hidemyroot",
        "com.formyhm.hideroot",
    };

    // Magisk/Zygisk specific paths (enhanced)
    private static final String[] MAGISK_PATHS = {
        "/data/adb/magisk",
        "/data/adb/magisk.img",
        "/data/adb/zygisk",
        "/data/adb/modules",
        "/data/adb/modules_update",
        "/data/adb/su_data",
        "/data/adb/su.img",
        "/data/local/magisk",
    };

    // Known Zygisk module base names
    private static final String[] ZYGOSK_MODULE_BASES = {
        "zygisk", "magisk", "su", "knox", "deny",
    };

    public static class RootCheckResult {
        public final boolean isRooted;
        public final String[] warnings;
        public RootCheckResult(boolean isRooted, String[] warnings) {
            this.isRooted = isRooted;
            this.warnings = warnings;
        }
    }

    /**
     * Performs comprehensive root detection checks.
     * @param context Application context
     * @return RootCheckResult containing root status and warnings
     */
    public static RootCheckResult check(Context context) {
        java.util.List<String> warnings = new java.util.ArrayList<>();

        if (isRootedByBinary()) {
            warnings.add("su binary found on system");
        }

        if (isRootedByTestKeys()) {
            warnings.add("Device built with test-keys (rooted build)");
        }

        if (isRootedByRootPackages(context)) {
            warnings.add("Root management app detected (Magisk/SuperSU/KingRoot)");
        }

        if (isRootedByDangerousApps(context)) {
            warnings.add("Hooking framework detected (Xposed/Substrate/RootCloak)");
        }

        if (isRootedByProps()) {
            warnings.add("Dangerous system properties detected");
        }

        if (isRootedBySuCommand()) {
            warnings.add("su command executes successfully");
        }

        // New: Enhanced Magisk detection
        if (isMagiskInstalled()) {
            warnings.add("Magisk system detected");
        }

        // New: Check for Magisk manager even if hidden
        if (isMagiskManagerInstalled(context)) {
            warnings.add("Magisk Manager app detected");
        }

        // New: Check for Zygisk modules (Magisk v24+)
        if (hasZygiskModules()) {
            warnings.add("Zygisk/Magisk modules active");
        }

        // New: Check for Magisk SU in mergedSlots (Magisk v20+)
        if (isMagiskSuPresent()) {
            warnings.add("Magisk SU binary present");
        }

        boolean isRooted = !warnings.isEmpty();
        return new RootCheckResult(isRooted, warnings.toArray(new String[0]));
    }

    // ===== Original methods preserved exactly =====

    private static boolean isRootedByBinary() {
        for (String path : ROOT_PATHS) {
            File file = new File(path);
            if (file.exists()) {
                return true;
            }
        }
        return false;
    }

    private static boolean isRootedBySuCommand() {
        File su = new File("/system/xbin/su");
        if (!su.exists()) su = new File("/system/bin/su");
        if (!su.exists()) return false;

        try {
            Process p = Runtime.getRuntime().exec(new String[]{su.getAbsolutePath(), "-c", "id"});
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = reader.readLine();
            reader.close();
            p.waitFor();
            if (line != null && line.contains("uid=0")) {
                return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    private static boolean isRootedByTestKeys() {
        String buildTags = Build.TAGS;
        return buildTags != null && buildTags.contains("test-keys");
    }

    private static boolean isRootedByRootPackages(Context context) {
        PackageManager pm = context.getPackageManager();
        for (String pkg : ROOT_PACKAGES) {
            try {
                pm.getPackageInfo(pkg, 0);
                return true;
            } catch (PackageManager.NameNotFoundException ignored) {}
        }
        return false;
    }

    private static boolean isRootedByDangerousApps(Context context) {
        PackageManager pm = context.getPackageManager();
        for (String pkg : DANGEROUS_APPS) {
            try {
                pm.getPackageInfo(pkg, 0);
                return true;
            } catch (PackageManager.NameNotFoundException ignored) {}
        }
        return false;
    }

    private static boolean isRootedByProps() {
        String[] dangerousProps = {"ro.debuggable", "ro.secure"};
        for (String prop : dangerousProps) {
            try {
                Process p = Runtime.getRuntime().exec("getprop " + prop);
                BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
                String line = reader.readLine();
                reader.close();
                p.waitFor();
                if (line != null && line.contains("=1")) {
                    return true;
                }
            } catch (Exception ignored) {}
        }
        return false;
    }

    // ===== New Magisk detection methods =====

    /**
     * Checks for Magisk system directories
     */
    private static boolean isMagiskInstalled() {
        for (String path : MAGISK_PATHS) {
            File file = new File(path);
            if (file.exists()) {
                return true;
            }
            // Also check sub-directory flag files
            if (path.equals("/data/adb/magisk") && new File("/data/adb/magisk/util_functions.sh").exists()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if Magisk Manager package is installed (looks harder to hide)
     */
    private static boolean isMagiskManagerInstalled(Context context) {
        PackageManager pm = context.getPackageManager();
        // Standard Magisk Manager package
        try {
            pm.getPackageInfo("com.topjohnwu.magisk", 0);
            return true;
        } catch (PackageManager.NameNotFoundException ignored) {}

        // Check for hidden/lite variants
        String[] magiskVariants = {
            "com.topjohnwu.magisk.dt",
            "com.topjohnwu.magisk.nb",
            "com.octavi.xposed",
        };
        for (String pkg : magiskVariants) {
            try {
                pm.getPackageInfo(pkg, 0);
                return true;
            } catch (PackageManager.NameNotFoundException ignored) {}
        }

        // Try to read shared prefs that Magisk Manager leaves behind
        // Even if app is hidden, package manager can still report it sometimes
        // This is a fallback that catches cases where pkg is reported but app name differs
        try {
            java.lang.reflect.Method getApplicationInfoMethod = pm.getClass().getMethod(
                "getApplicationInfo", String.class, int.class);
            android.content.pm.ApplicationInfo info = (android.content.pm.ApplicationInfo)
                getApplicationInfoMethod.invoke(pm, "com.topjohnwu.magisk", 0);
            return info != null;
        } catch (Exception ignored) {}

        return false;
    }

    /**
     * Checks for Zygisk (Magisk v24+) module directories
     */
    private static boolean hasZygiskModules() {
        File modulesDir = new File("/data/adb/modules");
        if (!modulesDir.exists() || !modulesDir.isDirectory()) {
            return false;
        }

        String[] modules = modulesDir.list();
        // If modules dir exists and has content, likely Zygisk/Magisk active
        if (modules != null && modules.length > 0) {
            // Check for the zygisk flag file
            File zygiskFlag = new File("/data/adb/zygisk/zygisk64");
            if (zygiskFlag.exists()) {
                return true;
            }
            // Any non-system module in the modules dir suggests Magisk
            for (String m : modules) {
                if (!m.equals("system") && !m.isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Checks for Magisk SU binary specifically (different from generic su)
     * Magisk v20+ puts su in /data/adb/su/bin/ and uses merged slots
     */
    private static boolean isMagiskSuPresent() {
        // Magisk-specific su location
        String[] magiskSuPaths = {
            "/data/adb/su/bin/su",
            "/data/adb/su/bin/magisk-su",
            "/data/adb/su/su",
            "/sbin/supolicy",
        };
        for (String path : magiskSuPaths) {
            if (new File(path).exists()) {
                return true;
            }
        }
        // Check for Magisk's daemon process
        return isMagiskDaemonRunning();
    }

    /**
     * Checks if Magisk daemon (magiskd) is running
     */
    private static boolean isMagiskDaemonRunning() {
        try {
            Process p = Runtime.getRuntime().exec("ps -A");
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("magiskd") || line.contains("magisk")) {
                    reader.close();
                    return true;
                }
            }
            reader.close();
            p.waitFor();
        } catch (Exception ignored) {}
        return false;
    }
}
