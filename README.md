# AI Image Generator

AI Image Generator is an Android text-to-image application developed for the CS702 Build & Fortify assignment. The app lets a user enter a text prompt, sends the prompt to the official AI image server, displays the generated image, and allows the image to be saved to the local gallery.

This repository contains the Android source code, resources, native code, build configuration, and documentation used for the submitted release APK.

> Client-side protection cannot make an API key impossible to extract. Our aim is to make simple extraction harder, reduce accidental leakage, and keep the app compliant with the assignment rule that it communicates directly with the official AI server only.

---

## 1. Version and Submission Information

- Release package: `AIImageGenerator-v1.1.6-release-signed.apk`
- Package name: `com.cs702.aigenerator`
- Minimum SDK: 24
- Target SDK: 34
- Compile SDK: 34
- NDK version: `26.1.10909125`
- CMake version: `3.22.1`
- Android Gradle Plugin: `8.13.2`
- Gradle wrapper: `8.13`

---

## 2. Main Features

| Requirement | Implementation |
|---|---|
| Text input box | Users can enter image-generation prompts. |
| API integration | The app calls `/auth` first and then `/generate_image`. |
| Image display | The generated image is loaded and displayed in the app using Glide. |
| Save functionality | The generated image can be saved to the Android gallery through MediaStore. |
| User interface | The app includes prompt input, generate, save, cancel, loading state, and image preview. |
| Reliability | The app includes loading states, error messages, request cancellation, and basic save checks. |
| Emulator support | The app is designed to run on a standard Android Studio emulator. |

---

## 3. API Workflow

The app communicates directly with the official AI server:

```text
https://ai.elliottwen.info/
```

The image generation process uses two official API calls:

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

The submitted version does **not** use a third-party proxy server, remote key delivery server, or extra network endpoint.

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

The release APK contains compiled bytecode, Android resources, native libraries, and signing metadata:

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

Handles the user-facing workflow. It reads the prompt, starts the authentication and image-generation requests, shows loading and error states, displays the image, supports cancellation, and saves the generated bitmap to the local gallery.

### `ApiService.java`

Defines the Retrofit API methods for the two official endpoints:

- `POST /auth`
- `POST /generate_image`

### `ApiModels.java`

Defines the data models used by Retrofit and Gson. `AuthResponse` represents the response returned by `/auth` and stores the short-lived signature, while `GenerateRequest` represents the JSON body sent to `/generate_image`, including the signature and the user prompt.

### `SecurityConfig.java`

Builds the hardened OkHttp client. It restricts requests to the official HTTPS server, disables redirects, adds the authorization header, and enables certificate pinning in release builds.

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

The submitted APK file is:

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

The app uses a layered hardening strategy. The main goal is to make API key extraction and runtime tampering more difficult without breaking normal app use.

Implemented protections include:

- Native-backed API key reconstruction
- R8/ProGuard release obfuscation and resource shrinking
- HTTPS-only communication with the official AI server
- OkHttp certificate pinning in release builds
- Request restrictions for host, method, path, and query usage
- Root, debugger, Frida-like, and tampering checks
- App backup disabled
- Cleartext traffic disabled
- Debug-oriented logging not enabled in release builds

More detail is provided in `SECURITY_REPORT.md`.

---

## 9. Testing Checklist

Before submission, the following items should be checked:

- The release APK installs on a standard Android Studio emulator.
- The app launches without crashing.
- A user can enter a prompt and generate an image.
- The app calls `/auth` and `/generate_image` successfully.
- The generated image is displayed.
- The generated image can be saved to the gallery.
- The Cancel button stops waiting for the active request.
- The public test key is not present in the submitted version.
- No remote key server or third-party endpoint is used.
- The submitted ZIP includes the release APK and the complete project source code.

---

## 10. Academic Integrity and Network Compliance

The submitted version follows the assignment restrictions:

- The app communicates directly with the official AI server only.
- No third-party proxy server is used.
- No remote key delivery server is used.
- No DDoS or abusive server testing is performed.
- The API key is not shared with other groups.
- The public test key is not used in the submitted APK.
- No commercial obfuscator is used.

---

## 11. Important Limitations

Client-side protection cannot make API key extraction impossible. A determined attacker with full control of a device may still attempt native reverse engineering, runtime hooking, or memory inspection.

The purpose of the fortification is to raise the effort required, reduce simple leakage paths, and show that the app has been designed with security in mind.

---

## 13. Repository

- Repository: <https://github.com/kndhjk/AIImageGenerator>
- Releases: <https://github.com/kndhjk/AIImageGenerator/releases>
- Current release target: `v1.1.6`
