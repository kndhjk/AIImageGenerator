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

## 2. API Key Protection (String Obfuscation)

### Implementation
The API authorization key is NOT stored as a plaintext string in the Java code. Instead, we use a multi-layer obfuscation scheme:

1. **XOR Cipher**: Each byte of the API key is XORed with a single-byte key (0x7A)
2. **Base64 Encoding**: The XORed bytes are Base64-encoded and stored in `ENCODED_KEY`
3. **Runtime Reconstruction**: At runtime, the key is decoded and XORed back to recover the plaintext
4. **Char-Code Obfuscation**: The HTTP header name "Authorization" is stored as char codes to avoid plaintext strings

### Why This Matters
Even if an attacker decompiles the APK using tools like jadx or apktool, they will find only the obfuscated form of the key. The plaintext key never appears in the DEX bytecode or string pools.

### Code Reference
- `NativeKeyStore.java` - `getApiKey()`, `getAuthHeaderName()`, `isValidKey()`
- Key is XOR-encrypted with key `0x7A` before Base64 encoding

### Code Flow
```
ENCODED_KEY (Base64) → XOR decode → Plaintext API key → HTTP Header
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

## 4. ProGuard/R8 Code Obfuscation

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

## 5. Network Security Configuration

### Implementation
An `network_security_config.xml` is included to enforce cleartext traffic restrictions:

- Cleartext traffic to non-API domains is blocked
- Certificate trust chain is configured for the app's trust store only

### Code Reference
- `app/src/main/res/xml/network_security_config.xml`

---

## 6. OkHttpClient Hardening

### Additional Security Options Applied
- `followRedirects(false)` - Prevents DNS rebinding attacks
- `followSslRedirects(false)` - Prevents SSL downgrade via redirect
- Custom timeouts prevent resource exhaustion attacks
- Certificate pinner is set at the client level (not just network config)

### Code Reference
- `SecurityConfig.java` - `buildSecureOkHttpClient()`
- `ApiClient.java` - Client initialization

---

## 7. Debug Flags Disabled

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
| APK decompilation | XOR+Base64 obfuscation | ★★★☆☆ |
| Root/Jailbreak detection | RootDetector checks | ★★★★☆ |
| Dynamic analysis (Frida/Xposed) | Root/hooking framework detection | ★★★☆☆ |
| Debugger attachment | Debug flags disabled | ★★★☆☆ |
| Network traffic interception | SSL Pinning + no HTTP | ★★★★★ |
| API key extraction from strings | String obfuscation | ★★★☆☆ |

---

## Limitations & Future Improvements

1. **Native Library Obfuscation**: The NDK-based approach would provide stronger key protection, but requires NDK toolchain. Current implementation uses pure-Java obfuscation.

2. **Code Integrity Checks**: A checksum/signature verification of the APK at runtime would detect tampering, but adds complexity.

3. **Emulator Detection**: Basic emulator detection could be added to prevent analysis in sandboxed environments.

4. **Network Traffic Encryption**: Beyond TLS, adding a second layer of encryption (e.g., HMAC-signed requests) would provide additional protection.

---

## Files Modified for Fortify

| File | Purpose |
|------|---------|
| `SecurityConfig.java` | SSL Certificate Pinning configuration |
| `NativeKeyStore.java` | XOR+Base64 obfuscated API key storage |
| `RootDetector.java` | Root/hook detection with user warning |
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

To verify obfuscation:
1. Use `apktool d app-release-unsigned.apk` to decompile
2. Search for the API key string - it should NOT appear in plaintext
3. The `ENCODED_KEY` field should appear in `NativeKeyStore`
