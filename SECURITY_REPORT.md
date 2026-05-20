# Security Report

## AI Image Generator - Fortify Documentation

This document describes how the AI Image Generator Android app was fortified for the CS702 Build & Fortify assignment. It focuses on the submitted `v1.1.5` version and uses clear, high-level explanations so the document can be understood by classmates as well as markers.

---

## 1. Security Goals

The main security goal is to protect the provided API authorization key and make it harder to extract through common Android reverse-engineering techniques.

The app is a client-side Android application, so the API key cannot be protected perfectly. The key must eventually be used by the app when making requests to the official AI server. Therefore, our goal is to increase the effort required for extraction and reduce common leakage paths, rather than claiming that the key is impossible to recover.

The final security goals are:

- Avoid storing the API key as a single plaintext Java string.
- Reduce simple Java-level reverse engineering.
- Restrict network communication to the official AI server.
- Use HTTPS and certificate pinning for stronger network protection.
- Detect common suspicious runtime environments.
- Avoid accidental API key leakage through logs, documentation, or screenshots.
- Keep the release APK stable and runnable on the standard Android Studio emulator.

---

## 2. Detailed Security Measures

### 2.1 Native-Backed API Key Protection

The API key is not stored as a single plaintext string in Java source code. Instead, key reconstruction and validation are moved into the native layer.

Relevant files:

```text
NativeKeyStore.java
app/src/main/cpp/native-key.c
libnative-key.so
```

This makes simple Java decompilation less effective. A basic search in decompiled Java code should not reveal the full API key as a normal string constant.

The real API key is not written in:

- README files
- Security documentation
- Screenshots
- Log output
- Temporary scripts included in the submission

The public test key provided in the assignment is not used in the submitted version.

---

### 2.2 Secure API Request Flow

The app communicates directly with the official AI server:

```text
https://ai.elliottwen.info/
```

It uses the required two-step API workflow:

1. `POST /auth` obtains a short-lived signature.
2. `POST /generate_image` sends the signature and user prompt to generate an image.

The app does not use a third-party proxy server, redirect server, or remote key delivery server.

The Authorization header is added through the OkHttp request flow. The app avoids writing the key into documentation or passing it through unnecessary files.

---

### 2.3 Certificate Pinning

The app uses OkHttp certificate pinning in release builds.

Relevant file:

```text
SecurityConfig.java
```

Certificate pinning helps reduce the risk of man-in-the-middle attacks. Even if an attacker installs a custom certificate authority on a test device, the release network client checks that the server certificate/public key matches the expected pinned value.

---

### 2.4 Network Surface Restriction

`SecurityConfig.java` restricts the API request surface before sending requests.

The secure OkHttp interceptor checks:

- The request uses HTTPS.
- The request host matches the official AI server.
- The request port matches the expected endpoint.
- The request method is `POST`.
- The request path is one of the expected API paths.
- Unexpected query strings are rejected.
- Redirects are disabled.

This reduces the risk of accidental or malicious requests being redirected to unexpected hosts or paths.

---

### 2.5 Network Security Configuration

Cleartext HTTP traffic is disabled in the manifest and network security configuration.

Relevant files:

```text
AndroidManifest.xml
res/xml/network_security_config.xml
```

The manifest includes:

```xml
android:usesCleartextTraffic="false"
```

This supports the assignment requirement that the app communicates with the official server using HTTPS.

---

### 2.6 Runtime Protection

The app includes runtime checks for suspicious environments.

Relevant files:

```text
RuntimeGuard.java
RootDetector.java
```

The app checks for indicators such as:

- Debugging
- Rooted environments
- Magisk-related indicators
- Frida-like traces
- Suspicious runtime or tampering signals

In release-like environments, suspicious runtime conditions can block sensitive operations.

These checks are not perfect, but they increase the effort needed for dynamic analysis.

---

### 2.7 Backup and Data Extraction Restrictions

The app disables Android backup features.

Relevant files:

```text
AndroidManifest.xml
res/xml/data_extraction_rules.xml
```

The manifest includes:

```xml
android:allowBackup="false"
android:fullBackupContent="false"
```

This reduces the risk of app data being copied through Android backup mechanisms.

---

### 2.8 Logging Controls

The app avoids intentionally logging the API key or Authorization header.

The app does not intentionally print:

- API key
- Authorization header
- Full key fragments
- Public test key

Debug network logging is only used for development builds. In release builds, the secure network client disables OkHttp network body logging.

Some development log statements may remain for workflow diagnosis, but they should not print the API key or Authorization header.

---

### 2.9 Release Stability Decision

The project includes a `proguard-rules.pro` file and can be configured for R8/ProGuard, but the submitted `v1.1.5` release build keeps minification and resource shrinking disabled.

Relevant release configuration:

```gradle
release {
    minifyEnabled false
    shrinkResources false
    proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
}
```

This decision was made because earlier testing showed compatibility regressions when aggressive R8/resource shrinking was enabled. Since the assignment states that the app must run correctly on a standard Android Studio emulator, runtime stability was prioritised for the submitted build.

Instead of relying on R8 minification, this version relies on native-layer key reconstruction, certificate pinning, network restrictions, runtime checks, disabled backup, and careful key handling.

No commercial obfuscator was used.

---

## 3. Implementation Steps

### Step 1: Build the Required App Features

We first implemented the core app workflow:

1. Add a text input field for the image prompt.
2. Add a Generate button.
3. Call `/auth` to obtain a short-lived signature.
4. Call `/generate_image` with the signature and prompt.
5. Display the generated image using Glide.
6. Add a Save button using Android MediaStore.
7. Add loading and error messages.
8. Add a Cancel button for user control during network waiting.

---

### Step 2: Move Key Handling Away from Plain Java Constants

We avoided storing the API key as a single plaintext Java string. Key reconstruction was moved into native-backed code, and Java code calls `NativeKeyStore` instead of directly holding the full key as a normal constant.

---

### Step 3: Harden Network Communication

We implemented a hardened OkHttp client in `SecurityConfig.java`.

The client:

1. Uses HTTPS.
2. Uses certificate pinning in release builds.
3. Disables redirects.
4. Checks that the request host, path, port, method, and query string match the expected API usage.
5. Adds the Authorization header inside the request flow.

---

### Step 4: Add Runtime Environment Checks

We added `RootDetector.java` and `RuntimeGuard.java` to detect common suspicious runtime conditions such as root, debugging, and Frida-like traces.

If suspicious runtime signals are detected, sensitive operations may be blocked.

---

### Step 5: Reduce Backup and Cleartext Risks

We updated the manifest and XML security configuration to:

- Disable app backup.
- Disable cleartext traffic.
- Apply network security settings.

---

### Step 6: Document the Security Design

We wrote this report to document the fortification measures, why they were selected, and the limitations of client-side API key protection.

---

## 4. Rationale

### Why native-backed key handling?

Java bytecode can be inspected with tools such as JADX. If the API key were stored as a simple Java string, it would be easy to find. Moving key reconstruction into native code increases the effort required for reverse engineering.

### Why certificate pinning?

HTTPS protects communication, but certificate pinning adds another check that the app is talking to the expected server certificate/public key. This reduces the risk of man-in-the-middle interception.

### Why restrict the network surface?

The assignment requires direct communication with the official server only. Restricting host, method, path, and query format helps prevent unintended network destinations and supports compliance with the assignment rules.

### Why runtime checks?

Runtime hooking frameworks and rooted environments can make it easier to inspect app memory or intercept sensitive functions. Runtime checks do not stop all attackers, but they increase the cost of dynamic analysis.

### Why disable backup?

Backup mechanisms may expose app data on some devices. Disabling backup reduces unnecessary data extraction paths.

### Why keep R8 minification disabled?

During testing, aggressive minification/resource shrinking caused compatibility regressions. Since a non-running app would fail the Build and Fortify requirements, stability was prioritised. This choice is documented honestly, and other hardening layers are used instead.

---

## 5. Challenges and Solutions

### Challenge 1: Protecting a client-side API key

A client-side API key cannot be protected perfectly because the app must eventually use it.

**Solution:** We avoided simple plaintext Java storage and used native-backed reconstruction, runtime checks, and secure networking to increase extraction difficulty.

---

### Challenge 2: Avoiding third-party server communication

A remote key server might appear to improve key protection, but the assignment only allows HTTPS communication with the official AI server.

**Solution:** We did not use any third-party proxy or remote key delivery server in the submitted version.

---

### Challenge 3: Balancing R8 obfuscation and app stability

Aggressive R8/resource shrinking caused regressions during testing.

**Solution:** We kept release minification disabled for the submitted build and documented this decision. We relied on native code, certificate pinning, runtime checks, and network restrictions instead.

---

### Challenge 4: Preventing accidental leakage

Debugging output can accidentally reveal sensitive information.

**Solution:** The app avoids intentionally logging the API key or Authorization header. Network BODY logging is limited to debug builds.

---

### Challenge 5: Maintaining reliability

The app needs to remain smooth and responsive while waiting for the AI server.

**Solution:** We added request cancellation, loading indicators, error messages, and save checks so the app does not freeze or crash during normal use.

---

## 6. Defensive Reverse-Engineering Review

We performed a defensive review of the app from an attacker's perspective. The goal was to check whether obvious secrets or dangerous endpoints were visible.

The review checked for:

- Public test key remnants
- Plaintext API key strings
- Remote key server endpoints
- Third-party IP addresses
- Sensitive logs
- Direct cleartext traffic
- Obvious Java-level key constants

The submitted version does not use the public test key and does not use a remote key server.

We also identified realistic limitations: if an attacker fully controls the device, they may still attempt runtime hooking, memory inspection, APK patching, or native reverse engineering.

---

## 7. Limitations

No client-side protection is perfect. A determined attacker may still attempt:

- Runtime hooking
- Memory inspection
- Native library reverse engineering
- APK repackaging
- Dynamic instrumentation

The goal of this project is to raise the difficulty of these attacks, not to make them impossible.

---

## 8. Compliance Statement

This project follows the assignment requirements:

- The app communicates directly with the official AI server.
- No third-party proxy server is used.
- No remote key delivery server is used.
- No commercial obfuscator is used.
- The public test key is not used in the submitted version.
- The API key is not shared with other groups.
- Cleartext traffic is disabled.
- The app is designed to run on a standard Android Studio emulator.

---

## 9. Summary

The final design uses layered client-side hardening. The most important protections are native-backed API key handling, HTTPS communication, certificate pinning, network request restrictions, runtime checks, backup restrictions, and careful logging practices.

These measures do not make API key extraction impossible, but they make extraction more difficult than storing the key directly in plaintext Java code.
