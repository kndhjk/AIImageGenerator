# AI Image Generator

AI Image Generator is an Android text-to-image application developed for the CS702 Build & Fortify assignment. The app allows users to enter a text prompt, send the prompt to the official AI server, display the generated image, and save the image to the local gallery.

This repository contains the Android source code, resources, native code, build configuration, and security hardening implementation used for the submitted APK.

> **Important:** Client-side protection cannot make an API key impossible to extract. The aim of this project is to make extraction more difficult, reduce common leakage paths, and comply with the assignment rule that the app communicates directly with the official AI server only.

---

## 1. Version and Submission Information

- App version: `v1.1.6`
- Package name: `com.cs702.aigenerator`
- Submitted APK: `AIImageGenerator-v1.1.6-release-signed.apk`
- Minimum SDK: 24
- Target SDK: 34
- Compile SDK: 34
- NDK version: `26.1.10909125`
- CMake version: `3.22.1`
- Android Gradle Plugin: `8.13.2`
- Gradle wrapper: `8.13`
- Build type: Release

---

## 2. Main Features

| Requirement | Implementation |
|---|---|
| Text input box | Users can enter image-generation prompts. |
| API integration | The app calls `/auth` first and then `/generate_image`. |
| Image display | The generated image is displayed in the app using Glide. |
| Save functionality | The generated image can be saved to the Android gallery through MediaStore. |
| User interface | The app includes prompt input, generate, save, cancel, loading state, and image preview. |
| Reliability | The app includes loading and error handling, request cancellation, and safer image-saving logic. |
| Emulator support | The app is designed to run on a standard Android Studio emulator. |

---

## 3. API Workflow

The app communicates directly with the official AI server:

```text
https://ai.elliottwen.info/
```

The image-generation process uses two official API calls:

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

The server returns an image path or URL. The app then displays the generated image and allows the user to save it.

The final submitted version does **not** use any third-party proxy server, remote key delivery server, or extra network service.

---

## 4. Project Structure

### 4.1 Source Project Structure

```text
AIImageGenerator/
├── app/
│   ├── build.gradle
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── cpp/
│       │   ├── CMakeLists.txt
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
│       └── res/
│           ├── layout/activity_main.xml
│           ├── values/
│           │   ├── colors.xml
│           │   ├── strings.xml
│           │   └── themes.xml
│           └── xml/
│               ├── data_extraction_rules.xml
│               └── network_security_config.xml
├── gradle/wrapper/
├── build.gradle
├── gradle.properties
├── settings.gradle
├── gradlew
├── gradlew.bat
├── README.md
└── SECURITY_REPORT.md
```

### 4.2 APK Structure

The release APK contains compiled bytecode, Android resources, native libraries, and signing metadata. The native security component is packaged as `libnative-key.so` for the supported emulator/device architectures.

```text
AIImageGenerator-v1.1.6-release-signed.apk
├── AndroidManifest.xml
├── classes.dex / classes2.dex
├── resources.arsc
├── lib/
│   ├── arm64-v8a/libnative-key.so
│   ├── armeabi-v7a/libnative-key.so
│   └── x86_64/libnative-key.so
├── res/
├── assets/
├── kotlin/
├── okhttp3/
└── META-INF/
```

---

## 5. Main Code Components

### `MainActivity.java`

Handles the main user workflow:

- Reads the user prompt.
- Starts the authentication and image-generation requests.
- Displays loading and error states.
- Displays the generated image.
- Saves the generated image to the local gallery.
- Supports request cancellation.

### `ApiClient.java`

Builds and provides the Retrofit client used for server communication.

### `ApiService.java`

Defines the Retrofit API methods for:

- `POST /auth`
- `POST /generate_image`

### `ApiModels.java`

Defines the data models used by Retrofit and Gson. `AuthResponse` represents the response returned by `/auth` and stores the short-lived signature, while `GenerateRequest` represents the JSON request body sent to `/generate_image`, including the signature and the user prompt.

### `SecurityConfig.java`

Builds the hardened OkHttp clients, restricts the allowed API surface, injects the Authorization header, and enables certificate pinning in release builds.

### `NativeKeyStore.java` and `native-key.c`

Provide native-backed API key reconstruction and validation. The API key is not stored as one plaintext Java string.

### `RuntimeGuard.java` and `RootDetector.java`

Perform runtime checks for suspicious environments, including debugging, root indicators, Frida-like traces, and tampering indicators.

---

## 6. Build Document

### 6.1 Build Environment

The project is intended to be built with Android Studio or the included Gradle wrapper.

Recommended environment:

- Android Studio with JDK 17
- Android SDK Platform 34
- NDK side-by-side `26.1.10909125`
- CMake `3.22.1`
- Windows, macOS, or Linux with Gradle wrapper support

### 6.2 Build Commands

Windows:

```bat
gradlew.bat clean assembleRelease
```

macOS/Linux:

```bash
./gradlew clean assembleRelease
```

### 6.3 APK Output

The release APK is generated under:

```text
app/build/outputs/apk/release/
```

The submitted APK is:

```text
AIImageGenerator-v1.1.6-release-signed.apk
```

### 6.4 Release Configuration

The release build enables R8/ProGuard and resource shrinking:

```gradle
release {
    minifyEnabled true
    shrinkResources true
    proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
}
```

---

## 7. Open-Source Obfuscator Declaration

This project uses Android's built-in R8/ProGuard for release build shrinking, optimization, obfuscation, and resource shrinking.

| Tool | Purpose | License / Status |
|---|---|---|
| R8 / ProGuard | Code shrinking, optimization, obfuscation, and resource shrinking | Included in the Android Gradle Plugin / Android build toolchain |

No commercial obfuscator was used.

---

## 8. Fortify Summary

The app uses a layered hardening strategy. The main goal is to make API key extraction and runtime tampering more difficult.

The API authorization key is not written in the README, logs, screenshots, or documentation, and it is not stored as a single plaintext Java string in the source code. Native-backed reconstruction is used to make simple Java-level static analysis less effective.

Implemented measures include:

- Native-backed API key reconstruction.
- API key is not stored as one plaintext Java string.
- R8/ProGuard release obfuscation and resource shrinking.
- HTTPS-only communication with the official AI server.
- Certificate pinning for release networking.
- Network surface restriction to the official host and expected API paths.
- No third-party proxy server or remote key server.
- Cleartext traffic disabled.
- App backup disabled.
- Data extraction rules configured to reduce backup-based data exposure.
- Runtime checks for debugging, root indicators, Frida-like traces, and tampering indicators.
- Sensitive logs removed or reduced in release builds.
- Save and Cancel reliability improvements.

More details are provided in `SECURITY_REPORT.md`.

---

## 9. Testing Checklist

Before submission, the app should be tested on a standard Android Studio emulator:

- App launches successfully.
- User can enter a prompt.
- App can call `/auth`.
- App can call `/generate_image`.
- Generated image is displayed.
- Save button saves the generated image to the gallery.
- Cancel button stops waiting for the current request.
- App does not freeze or crash during normal use.
- Release APK does not contain the public test key.
- Release APK does not contact third-party servers.

---

## 10. Submission Package Contents

The submitted ZIP package should contain:

- `AIImageGenerator-v1.1.6-release-signed.apk`
- Complete Android source code
- All required resources and native files
- `README.md`
- `SECURITY_REPORT.md`

The package should not include temporary key-generation tools, build caches, Git metadata, or the public test key.

---

## 11. Academic Integrity and Network Compliance

- The app communicates directly with the official AI server only.
- No third-party proxy server is used.
- No remote key delivery server is used.
- No DDoS or abusive server testing is performed.
- The API key is not shared with other groups.
- The public test key is not used in the submitted version.
- No commercial obfuscator is used.

---

## 12. Important Limitations

Client-side hardening cannot provide perfect secrecy. Since the API key must eventually be used by the app to send requests, a determined attacker with full control of the device may still attempt runtime hooking, memory inspection, or native reverse engineering.

The protections in this project are designed to increase the difficulty and cost of extraction compared with storing the key directly in plaintext.

---

## 13. Repo and Release Links

- Repository: <https://github.com/kndhjk/AIImageGenerator>
- Releases: <https://github.com/kndhjk/AIImageGenerator/releases>
- Current release target: `v1.1.5`
