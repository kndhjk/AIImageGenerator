# AI Image Generator

Android app for CS702 — Build & Fortify assignment. Generates AI images from text prompts using the `ai.elliottwen.info` API.

## Features

- **Text-to-Image**: Enter a prompt, tap Generate, view the result
- **Cancel Button**: Stop a pending request at any time
- **Save to Gallery**: Download generated images to your device's photo gallery
- **Dark Theme UI**: Modern Material Design dark theme

## Security (Fortify — Part 2)

### 8-Layer API Key Protection

| Layer | Technique | Purpose |
|-------|-----------|---------|
| L1 | API key stored as **reversed hex string** in bytecode | Not readable as-is |
| L2 | **JNI + native .so library** (libnative-key.so) | SHUFFLE table + XOR seed embedded in compiled ARM machine code — invisible to Java decompilers |
| L3 | `getNativeKey(reversed)` → **undo-shuffle + undo-XOR** in native code | Key only reconstructed at runtime |
| L4 | **Decoy methods** `getFakeApiKey()` + `isKeyValid()` | Return fake strings, never called — mislead decompilers |
| L5 | **Runtime VALIDATE_CHAR** check | Detects bytecode tampering |
| L6 | **ProGuard/R8 minification** | Removes logging, obfuscates class/field names |
| L7 | **`android:fullBackupContent="false"`** + data extraction rules | Prevents cloud backup extraction |
| L8 | **`android:debuggable="false"`** | Prevents adb data dir access in release |

### SSL Certificate Pinning (OkHttp)

- Pins to **SPKI SHA-256** of `ai.elliottwen.info` certificate
- Disabled in debug builds for emulator testing
- Pin: `JchgWAvcRYiIxf8gVP+SWeD5PCqwJVYGxQd2YqbSrz4=` (SubjectPublicKeyInfo hash)

### Root Detection

- Detects root/Magisk/Xposed at runtime
- Shows a security warning (does not block usage)

## Tech Stack

- **Language**: Java 17 / Android API 34
- **Networking**: Retrofit 2 + OkHttp 4.12 + Gson
- **Image Loading**: Glide 4.16
- **UI**: Material Components + ViewBinding
- **Native**: Android NDK r27 (ARM cross-compilation)
- **Build**: Gradle 8.4, minSdk 24

## API Flow

```
1. POST /auth  (Authorization: <api_key>)
   ← {"signature": "..."}
2. POST /generate_image  (Authorization: <api_key>, body: {"signature": "...", "prompt": "..."})
   ← "images/xxx.jpg"  (plain string)
3. GET /images/xxx.jpg  → display
```

## Build

```bash
./gradlew assembleDebug        # Debug APK (no minification)
./gradlew assembleRelease       # Release APK (ProGuard minification, signed with debug keystore)
```

APK output: `app/build/outputs/apk/debug/app-debug.apk` or `app/build/outputs/apk/release/app-release-unsigned.apk`

## CI / Release

GitHub Actions automatically builds and publishes a debug APK on every push to `main`.

## Disclaimer

The bundled API key is a course placeholder. Replace it before submission.
