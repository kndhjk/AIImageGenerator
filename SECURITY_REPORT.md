## 1. Security Goals

The main goal of the fortification work was to protect the provided API authorization key and make the Android application harder to analyse, modify, or misuse.

The app must send requests to the official AI image generation server, so the API key eventually has to be used by the client. Because of this, client-side protection cannot make the key impossible to extract. Our aim was therefore to reduce obvious leakage, increase the cost of reverse engineering, and make common attacks harder.

The main security goals were:

- avoid storing the API key as a plain Java string;
- reduce information exposed through Java decompilation;
- protect network communication with the official AI server;
- prevent accidental leakage through logs or documentation;
- make runtime debugging and instrumentation more difficult;
- keep the app stable and usable on a standard Android Studio emulator;
- comply with the assignment rule that the app communicates directly with the official AI server only.

---

## 2. Detailed Security Measures

### 2.1 API Key Protection

The API key is not stored as a single plaintext string in the Java source code.

Instead, key reconstruction and validation are supported by the native layer. This means that a simple Java-level decompilation using tools such as JADX should not directly reveal the full key as an obvious string constant.

Relevant components:

```text
NativeKeyStore.java
native-key.c
libnative-key.so
```

The real API key is not included in:

- README files;
- Security Report;
- screenshots;
- public documentation;
- Logcat output;
- temporary scripts;
- GitHub comments or release notes.

The public test key provided in the assignment is not used in the submitted version.

---

### 2.2 Native Layer Hardening

Part of the key handling logic is moved into native C/JNI code. This increases the difficulty of simple static analysis because an attacker would need to inspect the compiled native library rather than only reading Java bytecode.

Relevant files:

```text
app/src/main/cpp/native-key.c
lib/arm64-v8a/libnative-key.so
lib/armeabi-v7a/libnative-key.so
lib/x86_64/libnative-key.so
```

The native library is packaged for multiple supported architectures so that the app can run in a standard Android Studio emulator and on common Android device architectures.

---

### 2.3 R8 / ProGuard Obfuscation

The submitted release APK enables R8 / ProGuard obfuscation. This helps reduce the readability of the compiled Java/Kotlin bytecode and makes Java-level reverse engineering less straightforward.

The release APK contains compiled DEX files rather than readable source code:

```text
classes.dex
classes2.dex
```

R8 / ProGuard is used to:

- shrink unused code;
- obfuscate class, method, and field names where possible;
- reduce readable structure in the release build;
- make simple Java decompilation less useful to an attacker.

No commercial obfuscator is used. Only Android's standard R8 / ProGuard build tooling is used.

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

Certificate pinning helps ensure that the app communicates with the expected server identity. This provides an additional layer of protection beyond standard HTTPS validation.

---

### 2.6 Network Restrictions

The app restricts outgoing API communication to the expected server and expected API paths. The intended API workflow is:

```text
POST /auth
POST /generate_image
```

The app does not need to contact third-party infrastructure to generate images or obtain key fragments.

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

These checks do not make runtime analysis impossible, but they add friction for attackers who rely on common dynamic instrumentation tools.

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

This reduces the risk of sensitive data being exposed through Logcat or debug output.

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

Security also depends on reliable behaviour. An app that freezes, crashes, or mishandles network state is easier to misuse and may fail the build requirements.

The app includes:

- a loading state while waiting for the server;
- error handling for failed requests;
- a Cancel function so users can stop waiting for a server response;
- a Save function so generated images can be saved to the local gallery.

The Save and Cancel flows were reviewed to improve the normal user experience and reduce unstable behaviour.

---

## 3. Implementation Steps

### Step 1: Build the Core App Functionality

We first implemented the required build features:

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
2. Moving key reconstruction support into the native layer.
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

This was combined with native key handling so that the app does not rely on only one protection layer.

---

### Step 5: Secure Network Communication

We configured the app to use HTTPS and OkHttp certificate pinning for communication with the official AI server.

We also removed designs that would require third-party infrastructure. In particular, the final version does not use a remote key server or a remote key fragment endpoint.

---

### Step 6: Add Runtime Checks

Runtime checks were added to detect suspicious environments and common analysis tools.

The goal is not to block every possible attacker, but to make common runtime attacks less convenient.

---

### Step 7: Review Documentation and Submission Package

Before submission, the documentation was reviewed to ensure that it does not contain:

- the real API key;
- the public test key;
- key fragments;
- exploit scripts;
- third-party server details;
- outdated remote key design notes.

The final submission package is intended to contain the release APK, source code, resources, README, and this Security Report.

---

## 4. Rationale

We chose a layered security design because no single technique can fully protect an API key inside a mobile app. Since the app must use the key to send requests, a determined attacker with enough time and control over the device may still try to recover it. For that reason, our focus was to remove easy attack paths and make the process more difficult.

The first decision was to avoid storing the API key as a plain Java string. Java code is relatively easy to inspect with tools such as JADX, so placing the key directly in Java would make extraction too simple. Moving key handling into the native layer gives the app an extra barrier. It does not make the key impossible to find, but it means an attacker has to analyse both Java code and native code.

We also enabled R8 / ProGuard for the release APK because readable class and method names can reveal how the app works. Obfuscation helps reduce this information. It is not a complete defence, but it makes quick static analysis less direct and supports the other protection layers.

For network security, we used HTTPS and certificate pinning. The API key is sent as part of requests to the official AI server, so protecting the communication channel is important. Certificate pinning helps reduce the chance that a fake or locally installed certificate can be used to intercept traffic.

We did not use a remote key server, even though remote key delivery could look attractive from a security perspective. The assignment clearly states that the app must communicate directly with the official server only. Using a third-party key server would create an extra network endpoint and could break the academic integrity rules. Therefore, the final design keeps communication limited to the official AI server.

Runtime checks were included because static protection alone is not enough. Attackers may use debugging, rooted environments, or instrumentation tools to observe the app while it runs. The runtime checks are not perfect, but they increase the effort needed for common dynamic analysis.

Finally, we reduced logging because sensitive information can easily leak during development. Even if the key is protected in code, careless logs could expose request details, signatures, or server responses. Removing these logs is a simple but important security step.

Overall, the design aims to balance security, assignment compliance, and app reliability. We avoided overly complex solutions that could break the app or violate the network rules. Instead, we used several practical protections together to make key extraction and misuse harder.

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

Some security measures can make an app harder to maintain or can introduce build/runtime problems if applied too aggressively.

**Solution:**  
We tested the release APK on a standard Android Studio emulator and kept the final design focused on protections that support both security and reliability.

---

### Challenge 4: Reducing Information Leakage

During development, logs are useful for debugging. However, they can also leak sensitive information if left in the release version.

**Solution:**  
We reviewed logging and avoided intentionally printing API keys, Authorization headers, signatures, raw responses, and key reconstruction details.

---

### Challenge 5: Explaining Security Without Revealing Too Much

The documentation needs to describe how the app was fortified, but it should not expose the exact secret material or make reverse engineering easier.

**Solution:**  
This report describes the security approach at a high level. It avoids including the real API key, public test key, key fragments, or step-by-step extraction instructions.

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