# Security Report

## AI Image Generator – Fortify Documentation

This report explains how we fortified the AI Image Generator Android app for the CS702 Build & Fortify assignment. The document focuses on the security measures used in the submitted release version, why we chose them, and what limitations remain.

The app communicates directly with the official AI server only. It does not use a third-party proxy, a remote key server, or any additional endpoint outside the course server.

---

## 1. Security Goals

The main goal of this project is to protect the provided API authorization key and make it harder to extract from the app. Since this is an Android client, the key cannot be protected perfectly. The app must eventually use the key to send requests to the official server, so a determined attacker with full control of a device may still try to inspect memory, hook runtime methods, or reverse engineer the native library.

For this reason, our approach was not to claim perfect protection. Instead, we focused on reducing easy leakage paths and increasing the amount of work required to analyse the app. The main security goals were:

- Avoid storing the API key as a plaintext Java string.
- Reduce the usefulness of simple Java decompilation.
- Protect network communication against common interception attempts.
- Detect suspicious runtime environments where possible.
- Avoid leaking sensitive values through logs or documentation.
- Keep the app compliant with the assignment rule that only the official AI server should be contacted.

---

## 2. Detailed Security Measures

### 2.1 Native-Backed API Key Protection

The API key is not stored as a single plaintext string in the Java source code. Instead, key reconstruction and validation are moved into the native layer.

Relevant files:

```text
NativeKeyStore.java
native-key.c
libnative-key.so
```

This design means that a basic JADX inspection of the Java code should not reveal the complete API key directly. An attacker would need to inspect the compiled native library as well, which increases the difficulty compared with a plain Java constant.

The real API key is not written in:

- README files
- Security documentation
- Screenshots
- Logs
- Temporary scripts
- Public test documents

The public test key provided in the assignment is not used in the submitted version.

---

### 2.2 R8 / ProGuard Obfuscation

The release build enables R8/ProGuard and resource shrinking:

```gradle
release {
    minifyEnabled true
    shrinkResources true
    proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
}
```

R8/ProGuard is used to reduce the readability of the release APK by shrinking code, removing unused resources, and obfuscating many class, method, and field names. The project also includes release rules to remove standard Android logging calls during optimization.

Some classes and methods still need to be preserved to keep Retrofit, Gson, OkHttp, Glide, and JNI working correctly. Therefore, R8 is used as one layer of defence rather than as the only protection.

No commercial obfuscator was used.

---

### 2.3 Secure Communication and Certificate Pinning

The app communicates with:

```text
https://ai.elliottwen.info/
```

All API requests are sent over HTTPS. In release builds, OkHttp certificate pinning is used to reduce the risk of man-in-the-middle attacks.

Relevant file:

```text
SecurityConfig.java
```

The OkHttp client also restricts the request surface. It checks that requests use HTTPS, target the expected host and port, use POST, and access only the expected API paths. Redirects are disabled with `followRedirects(false)` and `followSslRedirects(false)`.

---

### 2.4 Network Security Configuration

The Android manifest disables cleartext traffic:

```xml
android:usesCleartextTraffic="false"
```

Relevant files:

```text
AndroidManifest.xml
res/xml/network_security_config.xml
```

The network security configuration blocks cleartext HTTP. The app also relies on OkHttp certificate pinning in release builds for stronger protection of the official server connection.

---

### 2.5 Runtime Protection

The app includes runtime checks for suspicious environments.

Relevant files:

```text
RuntimeGuard.java
RootDetector.java
```

The checks include:

- Debugger indicators
- Root indicators
- Magisk-related indicators
- Frida-like traces
- Suspicious runtime maps and ports
- Basic tampering indicators

If suspicious conditions are detected in release builds, sensitive operations may be blocked. These checks cannot stop every advanced attacker, but they make simple runtime analysis less convenient.

---

### 2.6 Backup and Data Extraction Restrictions

The app disables Android backup features:

```xml
android:allowBackup="false"
android:fullBackupContent="false"
```

Relevant files:

```text
AndroidManifest.xml
res/xml/data_extraction_rules.xml
```

This reduces the chance of app data being copied through Android backup mechanisms.

---

### 2.7 Logging Reduction

During development, logs are useful for debugging, but they can also leak sensitive information. The submitted release build uses R8 rules to remove standard `Log` calls during optimization. Debug-oriented network logging is not enabled in release builds.

We also avoided writing the API key, key fragments, or public test key into project documentation. This is important because documentation is shared with classmates at the end of the course.

---

### 2.8 Reliability Features

Although the main focus of this report is security, reliability is also important for the Build part of the assignment.

The app includes:

- A loading state while waiting for the server.
- A Cancel button that cancels the active Retrofit request and stops waiting.
- Basic error handling for authentication, generation, network, and save failures.
- A save flow using Android MediaStore.

These features help the app behave more smoothly during manual testing on the Android Studio emulator.

---

## 3. Implementation Steps

The fortification work was implemented in several stages.

### Step 1: Move key handling away from plain Java strings

We avoided storing the API key as a single Java constant. Instead, key handling was moved into `NativeKeyStore.java` and `native-key.c`, with the final reconstruction handled through the native layer.

### Step 2: Harden network access

We configured the app to use the official HTTPS API server and added request checks in `SecurityConfig.java`. The OkHttp client verifies the scheme, host, port, method, and allowed paths before sending a request. Certificate pinning is enabled in release builds.

### Step 3: Add runtime checks

We added `RuntimeGuard.java` and `RootDetector.java` to look for obvious debugging, root, Frida-like, and tampering indicators. These checks are used before sensitive operations.

### Step 4: Enable release obfuscation

We enabled R8/ProGuard and resource shrinking in the release build. The ProGuard rules were adjusted to keep required libraries and JNI behaviour working while still applying release obfuscation and optimization.

### Step 5: Reduce leakage through logs and files

The release configuration removes standard Android logs, and the documentation avoids including secrets. We also checked that the public test key and remote endpoint references were not present in the final package.

### Step 6: Check the APK defensively

We performed a defensive reverse-engineering review using tools such as APK inspection and Java-layer decompilation. The purpose was to identify whether obvious plaintext secrets, test keys, remote endpoints, or sensitive logs remained in the submitted APK.

---

## 4. Rationale

We chose a layered design because no single client-side defence is enough on its own. If the API key were written directly in Java, it would be easy to find with common decompilation tools. Moving the key logic into native code does not make it impossible to analyse, but it raises the level of effort needed. Someone would need to inspect the native library instead of only reading the Java classes.

R8/ProGuard was enabled for a similar reason. It reduces the readability of the release APK and removes unnecessary code and resources. This is useful against quick static inspection, although it should not be treated as complete protection. Some names and methods still need to remain available because the app depends on Retrofit, Gson, OkHttp, Glide, and JNI.

For network security, we used HTTPS together with certificate pinning. HTTPS protects the connection, while pinning gives the app a stricter expectation of the server certificate. This helps reduce the risk of a simple man-in-the-middle setup being used to inspect or modify traffic.

Runtime checks were added because static protection alone is not enough. Attackers may try to run the app in a rooted, instrumented, or debugged environment. Root, Frida-like, and debugger checks cannot stop every advanced setup, but they can block or slow down common analysis attempts.

We also decided not to use a remote key server. Although remote delivery might sound useful, it would add a third-party network endpoint, which conflicts with the assignment rule that the app should communicate directly with the official AI server only. Keeping the app within this rule was more important than adding a risky extra mechanism.

Overall, the aim was to make the app harder to reverse engineer while keeping it stable enough to run in a standard Android Studio emulator. The design favours several practical layers rather than relying on one strong-looking but fragile solution.

---

## 5. Challenges and Solutions

### Challenge 1: Protecting a client-side API key

The main challenge was that the API key must be used by the client. This means it cannot be hidden perfectly.

**Solution:** We avoided plaintext Java storage and moved key reconstruction into the native layer. We also combined this with R8/ProGuard, runtime checks, and secure network configuration.

### Challenge 2: Avoiding non-compliant remote key delivery

A remote key fragment approach was considered, but it would require the app to contact a third-party endpoint.

**Solution:** The submitted version does not use a remote key server, third-party proxy, or extra endpoint. The app communicates directly with the official AI server only.

### Challenge 3: Keeping security and functionality balanced

Some aggressive obfuscation rules can break Retrofit, Gson, OkHttp, Glide, or JNI calls.

**Solution:** We enabled R8/ProGuard but kept the rules needed for required libraries and native methods. This keeps the app functional while still applying release optimization and obfuscation.

### Challenge 4: Avoiding accidental leakage through development files

During development, logs and notes can accidentally expose sensitive details.

**Solution:** The final documentation avoids API keys, key fragments, public test keys, and step-by-step extraction instructions. Release optimization also removes standard Android log calls.

### Challenge 5: Supporting manual testing on the emulator

The assignment requires the app to run on a standard Android Studio emulator. Security checks that are too strict could block legitimate testing.

**Solution:** The runtime checks are designed to protect sensitive operations in suspicious release environments, while still allowing normal app use during expected testing.

---

## 6. Defensive Reverse-Engineering Review

A defensive review was performed to understand what another group might see when inspecting the APK. The review focused on safe checks, not on publishing secrets or exploit scripts.

The review checked for:

- Public test key remnants
- Plaintext API key strings
- Remote key server references
- Third-party endpoint references
- Sensitive logs
- Obvious Java-level key storage
- Native library presence
- Certificate pinning configuration

The review confirmed that the submitted version does not rely on a remote key server or third-party endpoint. It also confirmed that the API key is not exposed as a simple plaintext Java string.

The review also showed an important limitation: because the API key must be used by the app, a determined attacker may still try native reverse engineering or runtime hooking. This is why the report describes the design as cost-increasing rather than unbreakable.

---

## 7. Limitations

The app is more protected than a simple plaintext-key implementation, but it is not impossible to reverse engineer.

Known limitations include:

- A determined attacker may inspect the native library.
- Runtime hooks may still be attempted on a fully controlled device.
- Client-side secrets can never be protected as strongly as server-side secrets.
- Certificate pinning may need updates if the server certificate changes.
- Runtime detection can be bypassed by advanced attackers.

These limitations are expected for a client-side Android app. The goal is to raise the cost of attack, not to guarantee perfect secrecy.

---

## 8. Compliance Statement

The submitted version follows the assignment requirements:

- The app communicates directly with the official AI server.
- No third-party proxy server is used.
- No remote key delivery server is used.
- No extra third-party endpoint is used.
- No commercial obfuscator is used.
- Android's built-in R8/ProGuard is used for release obfuscation and shrinking.
- The public test key is not used in the submitted APK.
- The API key is not shared with other groups.
- No DDoS or abusive server testing is performed.

---

## 9. Summary

The final app uses several layers of defence: native-backed key handling, R8/ProGuard release obfuscation, certificate pinning, HTTPS-only API communication, request restrictions, runtime checks, disabled backup, and documentation hygiene.

These measures do not make the API key impossible to extract, but they make simple static analysis and accidental leakage much less likely. The app remains compliant with the assignment rule that it communicates directly with the official AI server only.
