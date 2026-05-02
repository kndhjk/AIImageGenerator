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
 * Detects if the device is rooted or has root management tools installed.
 */
public class RootDetector {

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

        boolean isRooted = !warnings.isEmpty();
        return new RootCheckResult(isRooted, warnings.toArray(new String[0]));
    }

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
}
