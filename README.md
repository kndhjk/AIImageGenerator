# AI Image Generator

AI Image Generator is an Android text-to-image application developed for the CS702 Build & Fortify assignment. The app lets users enter a text prompt, sends the prompt to the official AI server, displays the generated image, and allows users to save the image to the local gallery.

This repository contains the Android source code, resources, native code, build configuration, and security hardening implementation used for the submitted APK.

> **Important:** Client-side protection cannot make an API key impossible to extract. The goal of this project is to increase the cost of reverse engineering and reduce common leakage paths, while complying with the assignment rule that the app communicates directly with the official AI server only.

---

## 1. Version and Submission Information

- App version: `v1.1.5`
- Package name: `com.cs702.aigenerator`
- Submitted APK: `AIImageGenerator-v1.1.5-release-signed.apk`
- Minimum SDK: 24
- Target SDK: 34
- Compile SDK: 34
- NDK version: `26.1.10909125`
- CMake version: `3.22.1`
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
| Reliability | The app includes loading/error handling and request cancellation. The save function checks whether a bitmap is available before writing to the gallery. |
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

The server returns an image path or URL. The app then displays the image and allows the user to save it.

The submitted version does **not** use any third-party proxy server, remote key delivery server, or extra network service.

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

```text
AIImageGenerator-v1.1.5-release-signed.apk
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

### `ApiService.java`

Defines the Retrofit API methods for:

- `POST /auth`
- `POST /generate_image`

### `ApiModels.java`

Defines the data models used by Retrofit and Gson. `AuthResponse` represents the response returned by `/auth` and stores the short-lived signature, while `GenerateRequest` represents the JSON request body sent to `/generate_image`, including the signature and the user prompt.

### `SecurityConfig.java`

Builds the hardened OkHttp client, restricts the allowed API surface, injects the Authorization header, disables redirects, and enables certificate pinning in release builds.

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
AIImageGenerator-v1.1.5-release-signed.apk
```

### 6.4 Release Configuration

The submitted release build prioritises runtime stability. The project includes a `proguard-rules.pro` file, but release minification and resource shrinking are disabled in this version because earlier testing showed regressions when aggressive shrinking was enabled.

```gradle
release {
    minifyEnabled false
    shrinkResources false
    proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
}
```

The app instead relies on native-layer key reconstruction, certificate pinning, network restrictions, runtime checks, backup restrictions, and careful key handling for fortification.

---

## 7. Open-Source Obfuscator Declaration

No commercial obfuscator was used.

This project includes Android's standard R8/ProGuard configuration file (`proguard-rules.pro`). However, for the submitted release build, minification and resource shrinking are disabled to preserve app stability after compatibility issues were observed during testing.

| Tool / Configuration | Purpose | Status |
|---|---|---|
| R8 / ProGuard configuration | Android code shrinking and obfuscation support | Included but not enabled for submitted release minification |
| Commercial obfuscator | Not used | Not used |

---

## 8. Fortify Summary

The app uses a layered hardening strategy. The main goal is to make API key extraction and runtime tampering more difficult while keeping the app stable on a standard Android Studio emulator.

Implemented measures include:

- Native-backed API key reconstruction.
- API key is not stored as one plaintext Java string.
- Authorization header is added through the OkHttp request flow rather than being written directly into the UI code.
- HTTPS-only communication with the official AI server.
- Certificate pinning for release networking.
- Network surface restriction to the official host and expected API paths.
- Redirects disabled in the secure OkHttp client.
- No third-party proxy server or remote key server.
- Cleartext traffic disabled.
- App backup disabled.
- Data extraction rules configured to reduce backup-based data exposure.
- Runtime checks for debugging, root indicators, Frida-like traces, and tampering indicators.
- API key and Authorization header are not intentionally logged.

More details are provided in `SECURITY_REPORT.md`.

---

## 9. Testing Checklist

Before submission, the following checks should be performed:

- App installs and launches on a standard Android Studio emulator.
- A prompt can be entered in the text box.
- `/auth` request succeeds with the provided authorization key.
- `/generate_image` request succeeds with the short-lived signature.
- Generated image is displayed in the app.
- Save button stores the generated image in the Android gallery when the bitmap is available.
- Cancel button cancels the current request and hides the loading state.
- Release APK does not contain the public test key.
- Release APK does not contain a remote key server endpoint.
- App communicates directly with `https://ai.elliottwen.info/` only.

---

## 10. Academic Integrity and Network Compliance

This project follows the assignment rules:

- The app communicates directly with the official AI server.
- No third-party proxy server is used.
- No remote key delivery server is used.
- No DDoS or abusive server testing is performed.
- The public test key is not used in the submitted version.
- The API key is not shared with other groups.
- No commercial obfuscator is used.

---

## 11. Important Limitations

Client-side API key protection cannot provide perfect secrecy. A determined attacker with full control of the device may still attempt runtime hooking, memory inspection, APK patching, or native reverse engineering.

The security measures in this project are designed to increase attack cost and reduce common leakage paths, not to make extraction impossible.


## 12. Repo and Release Links

- Repository: <https://github.com/kndhjk/AIImageGenerator>
- Releases: <https://github.com/kndhjk/AIImageGenerator/releases>
- Current release target: `v1.1.5`
