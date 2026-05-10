# AI Image Generator

AI Image Generator is an Android application developed for the CS702 Build & Fortify assignment. The app allows users to enter a text prompt, send the prompt to the official AI image generation server, display the generated image, and save the result to the local gallery.

The project also includes several client-side hardening measures designed to make API key extraction and runtime tampering more difficult.

> Important: client-side protection cannot make an API key impossible to extract. The security design in this project focuses on increasing the cost of reverse engineering, reducing accidental leakage, and defending against common static, dynamic, and network-based attacks.

---

## 1. Main Features

### Build Requirements

| Requirement | Implementation Status |
|---|---|
| Text input box | Implemented. Users can enter image-generation prompts. |
| API integration | Implemented. The app calls `/auth` first and then `/generate_image`. |
| Image display | Implemented. The returned image URL is loaded and displayed using Glide. |
| Save functionality | Implemented. The generated image can be saved to the Android gallery through MediaStore. |
| User interface | Implemented. The UI includes prompt input, generate, save, cancel, loading state, and image preview. |
| Reliability | Implemented. Loading and error handling are included. The cancel and save flows should be verified before final submission. |
| Emulator support | The app is designed to run in a standard Android Studio emulator. This must be tested again before submission. |

---

## 2. API Workflow

This application communicates directly with the AI ​​server provided in the CS702 course:

```text
https://ai.elliottwen.info/
```

The generation process follows two steps:

1. **Authentication**
   - Endpoint: `POST /auth`
   - Header: `Authorization: <provided authorization header>`
   - Response: a short-lived digital signature.

2. **Image generation**
   - Endpoint: `POST /generate_image`
   - Header: `Authorization: <provided authorization header>`
   - Body:

```json
{
  "signature": "<signature returned from /auth>",
  "prompt": "<user prompt>"
}
```

The server returns an image path or URL. The app then displays the image in the main screen.

---

## 3. Project Structure

```text
AIImageGenerator/
├── app/
│   ├── build.gradle
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── cpp/
│       │   └── native-key.c
│       ├── java/com/cs702/aigenerator/
│       │   ├── ApiClient.java
│       │   ├── ApiModels.java
│       │   ├── ApiService.java
│       │   ├── MainActivity.java
│       │   ├── NativeKeyStore.java
│       │   ├── RootDetector.java
│       │   ├── RuntimeGuard.java
│       │   └── SecurityConfig.java
│       ├── jniLibs/
│       │   ├── arm64-v8a/libnative-key.so
│       │   ├── armeabi-v7a/libnative-key.so
│       │   └── x86_64/libnative-key.so
│       └── res/
│           ├── layout/activity_main.xml
│           ├── values/strings.xml
│           └── xml/network_security_config.xml
├── build.gradle
├── gradle.properties
├── settings.gradle
├── SECURITY_REPORT.md
└── README.md
```

---

## 4. Main Code Components

### `MainActivity.java`

Responsible for the main app workflow:

- Reads the user prompt.
- Starts the authentication request.
- Sends the image-generation request.
- Displays the returned image.
- Saves the generated image to the local gallery.
- Shows loading and error states.
- Connects UI buttons to their actions.

### `ApiService.java`

Defines the Retrofit API interfaces for:

- `POST /auth`
- `POST /generate_image`

### `ApiModels.java`

Defines request and response models used by Retrofit and Gson.

### `SecurityConfig.java`

Builds the hardened OkHttp client and configures SSL certificate pinning in release builds.

### `NativeKeyStore.java` and `native-key.c`

Implement layered API key reconstruction and validation logic. The authorization key is not stored as a single plaintext string in the Java source code. Instead, it is split into fragments and reconstructed at runtime, with native-layer participation.

### `RootDetector.java`

Checks for common root, Magisk, Xposed, Substrate, and other modified-environment indicators.

### `RuntimeGuard.java`

Checks for suspicious runtime conditions such as debugging, Frida-related traces, suspicious ports, and instrumentation indicators.

---

## 5. Fortify Implementation

The app uses a defense-in-depth approach. The main purpose is to protect the API authorization key from simple extraction and reduce the success of common attacks.

### 5.1 API Key Configuration

For security reasons, the API authorization key is not stored as a single plaintext string in the Android source code.

Before release, the real API key is processed by a temporary local encoding tool and then stored in `NativeKeyStore.java` as several encoded fragments. At runtime, the app reconstructs the authorization key internally before sending requests to the AI server.

The temporary encoding tool is not included in the submitted project, and the real API key is not written in the README, logs, screenshots, or documentation.

### 5.2 API Key Obfuscation

Implemented in:

```text
NativeKeyStore.java
native-key.c
```

Protection measures include:

- Splitting the key material into several fragments.
- Reordering and reconstructing the fragments at runtime.
- Applying a reverse transformation before native validation.
- Using a native library to participate in key validation.
- Including decoy strings to make static analysis less straightforward.

### 5.3 Native Layer Protection

The project includes compiled native libraries:

```text
app/src/main/jniLibs/arm64-v8a/libnative-key.so
app/src/main/jniLibs/armeabi-v7a/libnative-key.so
app/src/main/jniLibs/x86_64/libnative-key.so
```

Moving part of the key-handling logic into native code increases the effort required for reverse engineering compared with storing all logic only in Java bytecode.

### 5.4 SSL Certificate Pinning

Implemented in:

```text
SecurityConfig.java
```

The release build uses OkHttp `CertificatePinner` to pin the server certificate/public key and reduce the risk of man-in-the-middle attacks.

Current pin value used in the code:

```text
sha256/JchgWAvcRYiIxf8gVP+SWeD5PCqwJVYGxQd2YqbSrz4=
```

Debug builds relax pinning to support emulator testing and development.

### 5.5 Runtime Protection

Implemented in:

```text
RuntimeGuard.java
RootDetector.java
MainActivity.java
```

The app checks for:

- Debugger attachment.
- Suspicious `TracerPid` values.
- Frida-related ports and memory-map traces.
- Suspicious packages.
- Root and Magisk-related indicators.
- Xposed/Substrate-related indicators.

In release builds, suspicious runtime conditions can block sensitive operations.

### 5.6 R8 / ProGuard Hardening

Release builds enable shrinking and obfuscation:

```gradle
release {
    minifyEnabled true
    shrinkResources true
    proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
}
```

The project uses Android's built-in R8/ProGuard configuration. No commercial obfuscator is used.

R8 helps with:

- Removing unused code.
- Obfuscating class, method, and field names.
- Reducing APK size.
- Removing selected logging calls in release builds according to `proguard-rules.pro`.

R8 does not provide absolute string encryption, so the app also uses custom key splitting and native-layer validation.

### 5.7 Network Security Configuration

Implemented in:

```text
AndroidManifest.xml
res/xml/network_security_config.xml
```

The app disables cleartext HTTP traffic:

```xml
android:usesCleartextTraffic="false"
```

The network security configuration also sets `cleartextTrafficPermitted="false"`.

### 5.8 Backup and Data Extraction Restrictions

Implemented in:

```text
AndroidManifest.xml
res/xml/data_extraction_rules.xml
```

The manifest disables app backup:

```xml
android:allowBackup="false"
android:fullBackupContent="false"
```

This reduces the risk of sensitive app data being extracted through Android backup mechanisms.

---

## 6. Build Instructions

### Requirements

- Android Studio
- Android Gradle Plugin compatible with the project configuration
- Android SDK 34
- Minimum SDK: 24
- Java 8 compatibility

### Debug Build

```bash
./gradlew assembleDebug
```

### Release Build

```bash
./gradlew assembleRelease
```

The generated APK can usually be found under:

```text
app/build/outputs/apk/release/
```

---

## 7. Open-Source Obfuscator Declaration

This project uses the following free and open-source Android tooling:

| Tool | Purpose | License/Status |
|---|---|---|
| R8 / ProGuard | Code shrinking, optimization, and obfuscation | Built into Android Gradle Plugin / open-source Android toolchain |

No commercial obfuscator is used.

---

## 8. Known Limitations

- Client-side API key protection cannot provide perfect secrecy against a determined attacker.
- Runtime detection can be bypassed by advanced attackers.
- Certificate pinning can break if the server certificate or public key changes, so the pin should be checked before final release.
- Debug builds are less strict than release builds to support development and emulator testing.
- The cancel and save flows should be tested again after final code changes.

---

## 9. Academic Integrity Notes

- The app communicates directly with the official AI server.
- No third-party proxy server is used.
- No DDoS or abusive server testing is performed.
- The API key must not be shared with other groups.
- The public test key must not be used in the submitted version.

---
