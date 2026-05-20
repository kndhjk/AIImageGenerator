# Security Report

## 1. Security Goals

The main purpose of our fortification work was to make the Android app harder to analyse, modify, or misuse. The most important asset we needed to protect was the provided API authorization key.

Because this is a client-side Android app, the key eventually has to be used when the app sends requests to the official AI image generation server. This means the key cannot be protected perfectly. Our aim was to remove easy leakage points, make reverse engineering more difficult, and reduce the chance of accidental exposure.

Our main security goals were to:

- avoid storing the API key as a plain Java string;
- reduce useful information exposed through Java decompilation;
- protect communication with the official AI server;
- avoid leaking sensitive values through logs or documentation;
- make debugging, rooting, and instrumentation-based analysis harder;
- keep the app stable on a standard Android Studio emulator;
- follow the assignment rule that the app communicates directly with the official AI server only.

---

## 2. Detailed Security Measures

### 2.1 API Key Protection

The API key is not stored as one obvious plaintext string in the Java source code.

Instead, key handling is supported by the native layer. This means that simple Java decompilation with tools such as JADX should not directly reveal the full key as a clear string constant.

Relevant components:

```text
NativeKeyStore.java
native-key.c
libnative-key.so
```

The real API key is not included in:

- README files;
- this Security Report;
- screenshots;
- public documentation;
- Logcat output;
- temporary scripts;
- GitHub comments or release notes.

The public test key provided in the assignment is not used in the submitted version.

---

### 2.2 Native Layer Hardening

Part of the key handling logic is implemented in native C/JNI code. This adds another layer of difficulty because an attacker would need to inspect the compiled native library rather than only reading decompiled Java code.

Relevant files:

```text
app/src/main/cpp/native-key.c
lib/arm64-v8a/libnative-key.so
lib/armeabi-v7a/libnative-key.so
lib/x86_64/libnative-key.so
```

The native library is packaged for multiple supported architectures so the app can run on the standard Android Studio emulator and common Android device architectures.

---

### 2.3 R8 / ProGuard Obfuscation

The submitted release APK enables R8 / ProGuard obfuscation. This reduces the readability of compiled bytecode and makes Java-level reverse engineering less straightforward.

The release APK contains compiled DEX files rather than readable source code:

```text
classes.dex
classes2.dex
```

R8 / ProGuard is used to:

- remove unused code;
- obfuscate class, method, and field names where possible;
- reduce the readable structure of the release build;
- make simple Java decompilation less useful to an attacker.

No commercial obfuscator is used. Only Android's standard R8 / ProGuard tooling is used.

---

### 2.4 Secure Communication with the Official Server

The app communicates directly with the official course AI server:

```text
https://ai.elliottwen.info/
```

The app does not use:

- a third-party proxy server;
- a remote key delivery server;
- a remote key fragment endpoint;
- a traffic redirection server;
- any non-course endpoint for API key handling.

This is important because the assignment states that the app must communicate directly with the official server only.

---

### 2.5 Certificate Pinning and HTTPS

The app uses HTTPS for communication with the AI server. In addition, OkHttp certificate pinning is used to reduce the risk of man-in-the-middle attacks.

Relevant file:

```text
SecurityConfig.java
```

Certificate pinning helps the app verify that it is communicating with the expected server identity. This adds protection beyond normal HTTPS certificate validation.

---

### 2.6 Network Restrictions

The app restricts outgoing API communication to the expected server and API paths. The intended API workflow is:

```text
POST /auth
POST /generate_image
```

The app does not need to contact any third-party infrastructure to generate images or obtain key fragments.

This design helps meet the academic integrity requirement that the only intended network activity is HTTPS communication with the official AI server, plus normal DNS traffic.

---

### 2.7 Runtime Protection

The app includes runtime checks to make dynamic analysis harder.

Relevant files:

```text
RuntimeGuard.java
RootDetector.java
```

The checks include indicators related to:

- debugger attachment;
- rooted or modified environments;
- Magisk-related traces;
- Frida-related traces;
- suspicious runtime behaviour;
- basic tampering indicators.

These checks cannot stop every possible attacker, but they add friction for common dynamic analysis workflows.

---

### 2.8 Logging Reduction

The submitted version avoids intentionally logging sensitive values.

The app should not log:

- the API key;
- the Authorization header;
- the signature returned by `/auth`;
- raw response bodies;
- full generated image URLs;
- key reconstruction details.

This reduces the chance that sensitive data is exposed through Logcat or debugging output.

---

### 2.9 Backup and Data Extraction Restrictions

The app disables Android backup features to reduce the risk of app data being extracted through normal backup mechanisms.

Relevant files:

```text
AndroidManifest.xml
res/xml/data_extraction_rules.xml
```

Important settings include:

```xml
android:allowBackup="false"
android:fullBackupContent="false"
```

This helps reduce unnecessary exposure of app data.

---

### 2.10 Reliability Improvements

Security is also related to reliability. An app that freezes, crashes, or mishandles network state may fail the build requirements and can also create unexpected security issues.

The app includes:

- a loading state while waiting for the server;
- error handling for failed requests;
- a Cancel function so users can stop waiting for a server response;
- a Save function so generated images can be saved to the local gallery.

The Save and Cancel flows were reviewed to improve the normal user experience and reduce unstable behaviour.

---

## 3. Implementation Steps

### Step 1: Build the Core App Functionality

We first implemented the required app features:

1. Created a text input box for image prompts.
2. Sent the prompt to the official AI server.
3. Called `/auth` to obtain a short-lived signature.
4. Called `/generate_image` with the signature and prompt.
5. Displayed the generated image in the app.
6. Added a Save button to store the generated image locally.
7. Added loading and error states to improve reliability.

---

### Step 2: Protect API Key Handling

After the basic app worked, we moved key handling away from obvious Java constants.

The protection steps included:

1. Avoiding a single plaintext API key string in Java.
2. Moving key handling support into the native layer.
3. Using `NativeKeyStore.java` as the controlled interface between Java and native code.
4. Ensuring the real API key is not written into documentation or logs.
5. Removing the public test key from the submitted version.

---

### Step 3: Add Native Components

The native component was added through CMake and packaged into the release APK.

Key files:

```text
app/src/main/cpp/CMakeLists.txt
app/src/main/cpp/native-key.c
```

The release APK includes native libraries for supported ABIs:

```text
lib/arm64-v8a/libnative-key.so
lib/armeabi-v7a/libnative-key.so
lib/x86_64/libnative-key.so
```

---

### Step 4: Enable Release Obfuscation

R8 / ProGuard was enabled for the release APK. This makes Java-level decompilation less readable and reduces the amount of useful information available from the compiled DEX files.

This was combined with native key handling so the app does not rely on only one protection layer.

---

### Step 5: Secure Network Communication

We configured the app to use HTTPS and OkHttp certificate pinning when communicating with the official AI server.

We also removed designs that would require third-party infrastructure. The final version does not use a remote key server or a remote key fragment endpoint.

---

### Step 6: Add Runtime Checks

Runtime checks were added to detect suspicious environments and common analysis tools.

The goal is not to block every possible attacker, but to make common runtime attacks less convenient.

---

### Step 7: Review Documentation and Submission Package

Before submission, the documentation was reviewed to make sure it does not contain:

- the real API key;
- the public test key;
- key fragments;
- exploit scripts;
- third-party server details;
- outdated remote key design notes.

The final submission package is intended to contain the release APK, source code, resources, README, and this Security Report.

---

## 4. Rationale

We used a layered design because there is no single perfect way to protect an API key inside a mobile app. The key has to be used by the app at runtime, so a determined attacker may still try to inspect memory, hook functions, or reverse engineer the native library. Because of this, our aim was to remove the easiest attack paths and make each remaining path take more work.

The first decision was to avoid putting the API key directly in Java code. Java bytecode can be decompiled quite easily, so a hardcoded key would be one of the first things an attacker would search for. Moving part of the key handling into native code gives the app an extra layer of protection. It does not make the key impossible to recover, but it means that a simple Java-only inspection is not enough.

We enabled R8 / ProGuard for the release APK for the same reason. Clear class and method names can guide an attacker to important parts of the app. Obfuscation makes the decompiled code harder to read and removes some unnecessary structure. It is not a complete security solution, but it is useful when combined with native key handling and runtime checks.

Network security was also important. The app sends authenticated requests to the official AI server, so we need to protect the connection as much as possible. HTTPS protects the communication channel, and certificate pinning reduces the risk of trusting a forged or unexpected certificate. This helps protect the Authorization header and the request flow from simple interception attempts.

We deliberately did not use a remote key server. It might seem attractive from a security point of view, but it would introduce another network endpoint. The assignment clearly says that the app must communicate directly with the official AI server only, so a remote key server would create a compliance problem. Keeping the final design limited to the official server was safer and more appropriate for this project.

Runtime checks were added because reverse engineering is not only static. Someone may also try to observe the app while it is running. Checks for debugging, root indicators, Frida-like traces, and other suspicious behaviour make this type of analysis less convenient. These checks can be bypassed by advanced attackers, but they still raise the effort required for common attacks.

We also reduced logging because logs are a common source of accidental data leakage. Even if the key is not visible in the source code, careless logging could expose signatures, response data, request details, or other useful information. Avoiding sensitive logs is a simple but important part of the security design.

Overall, we tried to balance security, reliability, and assignment compliance. We avoided solutions that might break the app or violate the network rules. Instead, we combined several practical protections that work together to make key extraction and misuse harder.

---

## 5. Challenges and Solutions

### Challenge 1: Protecting a Client-Side API Key

The biggest challenge was that the API key must be used by the Android app. This means it cannot be completely hidden from a powerful attacker.

**Solution:**  
We avoided storing the key as a plaintext Java string and used native-layer support for key handling. We also combined this with release obfuscation, certificate pinning, and runtime checks.

---

### Challenge 2: Avoiding Third-Party Network Communication

We considered whether remote key delivery could improve security. However, this would require contacting a server other than the official AI server.

**Solution:**  
We removed remote key delivery from the final version. The app communicates directly with the official AI server only.

---

### Challenge 3: Balancing Security and Reliability

Some security measures can make an app harder to maintain or may introduce build or runtime problems if they are applied too aggressively.

**Solution:**  
We tested the release APK on a standard Android Studio emulator and focused on protections that support both security and reliability.

---

### Challenge 4: Reducing Information Leakage

During development, logs are useful for debugging. However, they can also leak sensitive information if left in the release version.

**Solution:**  
We reviewed logging and avoided intentionally printing API keys, Authorization headers, signatures, raw responses, and key reconstruction details.

---

### Challenge 5: Explaining Security Without Revealing Too Much

The documentation needs to describe how the app was fortified, but it should not expose secret material or make reverse engineering easier.

**Solution:**  
This report explains the security approach at a high level. It does not include the real API key, public test key, key fragments, or step-by-step extraction instructions.

---

## 6. Defensive Reverse-Engineering Review

We performed a defensive reverse-engineering review to understand what another group might see when inspecting the APK.

The review focused on:

- searching for the public test key;
- checking for a plaintext API key;
- checking for remote key endpoints;
- checking for third-party server references;
- reviewing Java-level decompilation indicators;
- reviewing native library presence;
- checking for obvious sensitive logging;
- checking whether the app directly exposes key handling in high-level UI code.

The review found that the submitted APK does not include the public test key as an obvious plaintext string and does not use a remote key server or third-party key endpoint.

The review also confirmed an important limitation: because the API key must eventually be used by the app, it is still theoretically possible for a strong attacker to attempt runtime observation, native reverse engineering, or instrumentation. This is why the app uses multiple layers of protection instead of relying on one method.

No real API key, exploit script, hook code, or reproducible credential extraction procedure is included in this report.

---

## 7. Current Version Consistency Checklist

The current v1.1.6 APK and documentation are intended to be consistent with the following points:

| Item | Current Status |
|---|---|
| Release APK provided | Yes |
| Public test key removed | Yes |
| Remote key server removed | Yes |
| Third-party endpoint removed | Yes |
| Native key handling included | Yes |
| R8 / ProGuard enabled in release APK | Yes |
| HTTPS communication used | Yes |
| Certificate pinning included | Yes |
| Runtime checks included | Yes |
| Root/debug/instrumentation indicators checked | Yes |
| App backup disabled | Yes |
| Cleartext traffic disabled | Yes |
| No commercial obfuscator used | Yes |
| Documentation avoids real key disclosure | Yes |

---

## 8. Limitations

The app is more secure than a simple plaintext-key implementation, but it is not impossible to attack.

Remaining limitations include:

- a determined attacker may still inspect native libraries;
- runtime hooking may still be possible on a fully controlled device;
- certificate pinning may need updates if the server certificate changes;
- client-side API keys can never be protected as strongly as server-side secrets;
- runtime checks can be bypassed by advanced attackers.

These limitations are expected for a client-side Android application. The goal of the project is to increase the cost of attacks, not to claim perfect protection.

---

## 9. Compliance Statement

The final submitted version follows the assignment requirements:

- The app communicates directly with the official AI server.
- No third-party proxy server is used.
- No remote key delivery server is used.
- No third-party API key fragment endpoint is used.
- No commercial obfuscator is used.
- Android R8 / ProGuard is used for release obfuscation.
- The public test key is not used in the submitted version.
- The API key is not shared with other groups.
- The documentation does not contain the real API key.

---

## 10. Summary

The app was fortified using a layered approach. The key protections include native-backed API key handling, release obfuscation, HTTPS communication, certificate pinning, runtime environment checks, backup restrictions, logging reduction, and removal of third-party key delivery.

These measures do not make the API key impossible to extract, but they make simple static analysis, careless logging, network interception, and common runtime inspection more difficult. The final design also stays within the assignment rules by communicating directly with the official AI server only.
