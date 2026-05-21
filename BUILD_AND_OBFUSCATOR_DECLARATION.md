# Build and Open-Source Obfuscator Declaration

## Project

**AI Image Generator**  
Version: **v1.1.6**  
Course: **CS702 Build & Fortify Assignment**

---

## 1. APK Build Overview

The submitted APK was generated from the Android Studio project using the Gradle wrapper included in the repository.

The release APK was built from the `release` build type, with R8 / ProGuard and resource shrinking enabled. The project also includes native code built through CMake for the `libnative-key.so` component.

## 2. Build Environment

The APK was generated using:

- Android Studio
- Android Gradle Plugin
- Android SDK 34
- Minimum SDK: 24
- Target SDK: 34
- Java 8 compatibility
- NDK version: 26.1.10909125
- CMake version: 3.22.1

## 3. Build Command

The release APK can be generated using the Gradle wrapper.

On Windows:

```bash
gradlew.bat clean assembleRelease
```

On macOS/Linux:

```bash
./gradlew clean assembleRelease
```

## 4. APK Output Location

After the build completes, the APK is generated under:

```text
app/build/outputs/apk/release/
```

The submitted APK file is:

```text
AIImageGenerator-v1.1.6-release-signed.apk
```

## 5. Release Build Configuration

The release build enables R8 / ProGuard and resource shrinking:

```gradle
release {
    minifyEnabled true
    shrinkResources true
    proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
}
```

The native component is built through CMake using:

```gradle
externalNativeBuild {
    cmake {
        path file('src/main/cpp/CMakeLists.txt')
        version '3.22.1'
    }
}
```

The APK includes native libraries for the supported ABIs:

```text
arm64-v8a
armeabi-v7a
x86_64
```

---

## 6. Open-Source Obfuscator Declaration

This project uses Android's built-in **R8 / ProGuard** tooling for release build code shrinking, optimization, resource shrinking, and obfuscation.

No commercial obfuscator was used in this project.

## 7. Obfuscation Tool Used

| Tool | Purpose | License / Status |
|---|---|---|
| R8 / ProGuard | Code shrinking, optimization, resource shrinking, and obfuscation | Included in the Android Gradle Plugin and Android build toolchain |

## 8. Reason for Using R8 / ProGuard

R8 / ProGuard was used to make static reverse engineering more difficult by reducing readable class, method, and field names in the release APK. It also removes unused code and reduces the final APK size.

This protection is not the only security measure. It is combined with other fortification techniques, including native-layer key reconstruction, certificate pinning, HTTPS-only communication, runtime checks, and logging reduction.

## 9. Commercial Obfuscator Statement

We confirm that:

- No paid obfuscator was used.
- No commercial Android obfuscation product was used.
- No closed-source third-party obfuscation service was used.
- Only Android's standard R8 / ProGuard tooling was used.

## 10. Final Statement

The submitted APK was generated using the Gradle release build process described above. All obfuscation used in this project is based on free/open-source Android build tooling. The submitted APK does not use any disallowed commercial obfuscator.
