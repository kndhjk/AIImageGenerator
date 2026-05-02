package com.cs702.aigenerator;

import android.content.Context;
import android.os.Build;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;

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
        "/system/xbin/com.google.android.gms.bind.BackupTransport",
    };

    private static final String[] ROOT_PACKAGES = {
        "com.noshufou.android.su",
        "com.noshufou.android.su.elite",
        "eu.chainfire.supersu",
        "com.koushikdutta.superuser",
        "com.thirdparty.superuser",
        "com.yellowes.su",
        "com.topjohnwu.magisk",
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

    public static RootCheckResult check(Context context) {
        java.util.List<String> warnings = new java.util.ArrayList<>();

        if (isRootedByBinary() || isRootedByRootManagementApps() || isRootedByTestKeys() || isRootedByDangerousApps(context)) {
            warnings.add("Device appears to be rooted or has root management tools");
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

    private static boolean isRootedByRootManagementApps() {
        for (String pkg : ROOT_PACKAGES) {
            try {
                android.content.pm.PackageManager pm = android.app.Application.class.getClassLoader()
                    .loadClass("android.app.Application").getClassLoader() == null ? null : null;
            } catch (Exception e) { /* ignore */ }
        }
        return checkSuBinary();
    }

    private static boolean checkSuBinary() {
        String[] paths = {
            "/system/xbin/which",
            "/system/bin/which",
            "/sbin/which",
            "/system/xbin/su",
            "/system/bin/su",
        };
        for (String path : paths) {
            if (new File(path).exists()) {
                return runCommand(new String[]{path, "su"}) == 0;
            }
        }

        // Try running "su" directly
        return runCommand(new String[]{"/system/xbin/su", "-c", "id"}) == 0
            || runCommand(new String[]{"/system/bin/su", "-c", "id"}) == 0
            || runCommand(new String[]{"su", "-c", "id"}) == 0;
    }

    private static boolean isRootedByTestKeys() {
        String buildTags = Build.TAGS;
        return buildTags != null && buildTags.contains("test-keys");
    }

    private static boolean isRootedByDangerousApps(Context context) {
        for (String pkg : DANGEROUS_APPS) {
            try {
                context.getPackageManager().getPackageInfo(pkg, 0);
                return true;
            } catch (Exception ignored) {}
        }
        return false;
    }

    private static boolean isRootedByRootManagementApps() {
        // Additional check via props
        String[] dangerousProps = {
            "ro.debuggable",
            "ro.secure",
        };
        for (String prop : dangerousProps) {
            try {
                Process p = Runtime.getRuntime().exec("getprop " + prop);
                BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
                String line = reader.readLine();
                reader.close();
                if (line != null && (line.contains("1") || line.toLowerCase().contains("root"))) {
                    return true;
                }
            } catch (Exception ignored) {}
        }
        return false;
    }

    private static int runCommand(String[] cmd) {
        try {
            Process p = Runtime.getRuntime().exec(cmd);
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("uid=0")) return 0;
            }
            reader.close();
            return p.waitFor();
        } catch (Exception e) {
            return -1;
        }
    }
}
