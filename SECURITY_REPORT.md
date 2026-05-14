# CS702 Security Fortification Report
## AI Image Generator - Security Enhancements

---

## 1. SSL Certificate Pinning

### Implementation
We implement certificate pinning at the OkHttpClient level using `CertificatePinner` with SHA-256 fingerprints of the server's TLS certificate.

**Pinned Certificate:**
- SHA-256: `3rZyrzZdM7XRbcJRlxhhiA0TstYV7KKtUnolImZIRHI=`
- Backup pin: `GrXIkJaICZfPJ5qR8aPBzPAjMX8Vrl7gB0pKmwx0eWA=` (Cloudflare intermediate CA)

### Why This Matters
Without pinning, a man-in-the-middle (MITM) attacker could intercept HTTPS traffic by presenting a forged certificate. This is especially critical because the API key is transmitted with every request. Certificate pinning ensures that even if an attacker has a valid certificate from a trusted CA, the connection will be refused unless it matches our pinned certificate.

### Code Reference
- `SecurityConfig.java` - `buildCertificatePinner()` method
- `SecurityConfig.java` - `validateCertificatePinning()` runtime verification

---

## 2. API Key Protection (Native-only Layering)

### Implementation
The API authorization key is NOT stored as a plaintext string in Java. The current implementation uses a layered native-only design:

1. **Native-only storage**: the real key lives only in `native-key.c`
2. **Custom symbol encoding**: each character is encoded with an invented Morse-like syllable alphabet (`di/da/tu/ka`)
3. **Group shuffling**: encoded chunks are split into 8 groups and reconstructed in non-source order
4. **Rolling byte transform**: each character is protected with a position-dependent byte mask and bit rotation
5. **Native validation**: JNI verifies lowercase-hex format and a fixed FNV-1a checksum before returning the key
6. **Release guardrails**: release builds refuse to return the key if runtime hook/tamper signals are present

### Why This Matters
This does **not** make extraction impossible, but it raises the cost materially:
- there is no plaintext key string in DEX
- the old Java reversal method is gone
- the old prebuilt `.so` files are gone
- analysis must recover the custom syllable mapping, group order, rolling transform, and checksum rules
- and in release builds, the key path is blocked when obvious instrumentation is detected

### Code Reference
- `NativeKeyStore.java` - `getApiKey(Context)`, `isKeyValid()`
- `native-key.c` - `buildNativeKey()` and `verifyNative()`

### Code Flow
```
native encoded groups
→ unshuffle by group index
→ decode custom di/da/tu/ka symbols
→ undo rolling rotate+mask transform
→ native checksum validate
→ API key
```

---

## 3. Root Detection

### Implementation
The app performs multiple checks to detect if the device is rooted:

1. **Binary Detection**: Checks for existence of `su` binary in standard paths (`/system/xbin/su`, `/sbin/su`, etc.)
2. **Test Keys Detection**: Checks if `Build.TAGS` contains "test-keys"
3. **Root Management Apps**: Scans installed packages for known root management apps (Magisk, SuperSU, KingRoot, etc.)
4. **Dangerous Apps Detection**: Checks for Xposed Framework, Substrate, and other hooking frameworks

### User Warning
When root is detected, the app displays a security warning dialog. Users can choose to:
- **Continue Anyway**: App functions but logs a warning
- **Exit**: App terminates immediately

### Code Reference
- `RootDetector.java` - `check()`, `isRootedByBinary()`, `isRootedByTestKeys()`, `isRootedByRootManagementApps()`, `isRootedByDangerousApps()`
- `MainActivity.java` - `performSecurityChecks()`, `showRootWarningDialog()`

---

## 4. Runtime Instrumentation / Hook Detection

### Implementation
A dedicated `RuntimeGuard` layer now checks for common dynamic-analysis signals:

1. **Debugger detection** via `Debug.isDebuggerConnected()`
2. **TracerPid detection** from `/proc/self/status`
3. **Frida port probing** on `127.0.0.1` (`27042`, `27043`, `23946`)
4. **Suspicious memory map scanning** for strings such as `frida`, `gadget`, `gum-js-loop`, `xposed`, `substrate`, `zygisk`, `magisk`
5. **Suspicious package detection** for known instrumentation managers
6. **RootDetector integration** to feed release-time blocking decisions

### Behavior
- **Debug builds** remain permissive to avoid breaking development
- **Release builds** block sensitive operations (including API-key retrieval / generation flow) when suspicious runtime signals are present

### Code Reference
- `RuntimeGuard.java`
- `MainActivity.java` startup checks
- `NativeKeyStore.java` release-path enforcement

---

## 5. ProGuard/R8 Code Obfuscation

### Implementation
The release build uses aggressive R8 optimization with the following measures:

1. **Symbol Renaming**: All class, method, and field names are renamed to meaningless single characters
2. **String Encryption**: Sensitive string constants are subject to R8's built-in obfuscation
3. **Dead Code Elimination**: Unused classes and methods are removed
4. **Reflection Suppression**: Prevents runtime reflection from accessing protected classes
5. **Logging Removal**: All `Log.d()`, `Log.i()`, `Log.v()` calls are removed in release builds
6. **Resource Shrinkage**: Unused resources are removed from the APK

### Key ProGuard Rules
- `-repackageclasses ''` - Flattens package structure
- `-allowaccessmodification` - Enables more aggressive inlining
- `-optimizationpasses 10` - Multiple optimization passes
- `-keep` rules for all security classes ensure they are not removed or renamed in ways that break functionality

### Code Reference
- `proguard-rules.pro` - Full configuration file

---

## 6. Network Security Configuration

### Implementation
An `network_security_config.xml` is included to enforce cleartext traffic restrictions:

- Cleartext traffic to non-API domains is blocked
- Certificate trust chain is configured for the app's trust store only

### Code Reference
- `app/src/main/res/xml/network_security_config.xml`

---

## 7. OkHttpClient Hardening

### Additional Security Options Applied
- `followRedirects(false)` - Prevents DNS rebinding attacks
- `followSslRedirects(false)` - Prevents SSL downgrade via redirect
- Custom timeouts prevent resource exhaustion attacks
- Certificate pinner is set at the client level (not just network config)

### Code Reference
- `SecurityConfig.java` - `buildSecureOkHttpClient()`
- `ApiClient.java` - Client initialization

---

## 8. Debug Flags Disabled

### Implementation
In release builds:
- `android:debuggable="false"` in manifest (via build config)
- `buildFeatures.debuggable = false`
- `jniDebuggable = false`

This prevents `adb install -r` from attaching debuggers to the release APK, making runtime analysis harder.

---

## Attack Surface Summary

| Attack Vector | Protection | Effectiveness |
|---------------|-----------|---------------|
| MITM with forged cert | Certificate Pinning | ★★★★★ |
| APK decompilation | Native custom symbol encoding + rolling transform | ★★★★☆ |
| Root/Jailbreak detection | RootDetector checks | ★★★★☆ |
| Dynamic analysis (Frida/Xposed) | RuntimeGuard + root/hooking checks + release blocking | ★★★★☆ |
| Debugger attachment | Debug flags disabled | ★★★☆☆ |
| Network traffic interception | SSL Pinning + no HTTP | ★★★★★ |
| API key extraction from strings | Native-only storage | ★★★★☆ |

---

## Limitations & Future Improvements

1. **No client-side defense is unbreakable**: Frida / repackaging resistance can only raise cost, not guarantee impossibility.

2. **Signature-bound integrity**: A stronger next step is binding key reconstruction to the app signing certificate digest in release builds.

3. **Server-side hardening**: Moving from static API key trust to short-lived server-issued tokens would reduce client secret exposure.

4. **Emulator-specific policy**: Emulator detection can be added if you want to block analysis environments more aggressively, but it may affect QA/testing.

---

## Files Modified for Fortify

| File | Purpose |
|------|---------|
| `SecurityConfig.java` | SSL Certificate Pinning configuration |
| `NativeKeyStore.java` | Native-only API key access |
| `RootDetector.java` | Root / Magisk / dangerous package detection |
| `RuntimeGuard.java` | Anti-debug / anti-Frida / runtime instrumentation checks |
| `ApiClient.java` | Hardened OkHttpClient with pinning |
| `MainActivity.java` | Security checks on startup, updated API calls |
| `proguard-rules.pro` | Aggressive R8 obfuscation rules |
| `build.gradle` | Release security flags |
| `SECURITY_REPORT.md` | This documentation |

---

## Testing

To test SSL Pinning:
1. Install the app on a real device
2. Try to intercept traffic using a proxy (e.g., Charles Proxy, mitmproxy)
3. The app should refuse to connect when a non-pinned certificate is presented

To test Root Detection:
1. Install the app on a rooted device/emulator with Magisk
2. The warning dialog should appear on app launch

To test RuntimeGuard:
1. Start a common Frida server port locally on a test device/emulator
2. Launch a release build
3. Sensitive operations should be blocked instead of returning the key

To verify obfuscation:
1. Use `apktool d` or jadx on a release APK
2. Search for the API key string - it should NOT appear in plaintext
3. Inspect `NativeKeyStore` and confirm the real key is not present as a Java string constant
