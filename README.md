# AI Image Generator

Android app for CS702 Build & Fortify. It generates images from text prompts via `https://ai.elliottwen.info/`.

---

## Current version

- Version: `v1.1.5`
- Package: `com.cs702.aigenerator`
- Release APK: GitHub Releases
- ABIs: `arm64-v8a`, `armeabi-v7a`, `x86_64`
- Native build: NDK `26.1.10909125`
- CMake: `3.22.1`
- Android Gradle Plugin: `8.13.2`
- Gradle wrapper: `8.13`

---

## What changed in the hardened line

This branch removed the old remote-fragment approach and moved to a local-only hardened design.

### Security goals

1. No third-party remote server dependency for key reconstruction
2. Only one valid API key accepted
3. Raise Frida / hook / repackaging cost significantly
4. Make common tampering paths fail closed
5. Keep the app still buildable and testable on Windows / Android Studio emulator

---

## Hardening architecture

### 1. Native-only key reconstruction

The real API key is reconstructed in `app/src/main/cpp/native-key.c`, not stored as a Java plaintext constant.

Key properties:
- custom syllable encoding: `di / da / tu / ka`
- 8 fragment groups
- shuffled group restore order
- position-based rolling mask
- bit rotation during decode
- FNV-1a based final validation
- temporary native buffer zeroing via `secure_zero()`

### 2. Java layer no longer carries the key through business code

The business flow no longer passes Authorization values around method-by-method.

Instead:
- `NativeKeyStore` exposes guarded native-backed accessors
- OkHttp interceptor injects Authorization automatically
- `MainActivity` does not hand the API key to Retrofit methods anymore

### 3. Runtime anti-hook / anti-instrumentation checks

`RuntimeGuard` blocks sensitive operations on suspicious environments.

Current checks include:
- debugger attached / waiting debugger
- `TracerPid` detection
- suspicious `/proc/self/maps` entries
- suspicious thread names
- suspicious stack frames
- suspicious environment variables (`LD_PRELOAD`, `FRIDA_*`, etc.)
- common Frida ports
- suspicious installed packages
- root / Magisk indicators

### 4. APK signing check

The app verifies the expected signing certificate SHA-256 at runtime.

### 5. Integrity verification

The app currently verifies:
- `classes.dex`
- `AndroidManifest.xml`
- `resources.arsc`
- extracted `libnative-key.so` against the packaged APK copy

This makes typical repackaging and binary patching paths much noisier.

### 6. Network surface restriction

The hardened OkHttp path only allows:
- HTTPS
- expected host
- expected port
- POST only
- allowed paths only
- no query parameters
- no redirects

### 7. Native metadata hiding

These values are no longer kept as simple Java constants:
- expected package name
- base URL
- Authorization header name
- auth path
- generate path

They are provided through native-backed accessors.

---

## Current security layers summary

1. real key reconstructed in C/JNI only
2. custom syllable encoding
3. shuffled fragments
4. rolling mask
5. bit rotation decode
6. native validation accepts only the intended 128-char lowercase hex key
7. secure zeroing of temporary native buffers
8. signer SHA-256 validation
9. `classes.dex` integrity validation
10. `AndroidManifest.xml` integrity validation
11. `resources.arsc` integrity validation
12. `libnative-key.so` integrity validation
13. Frida / hook / suspicious runtime detection
14. root / Magisk detection
15. restricted outbound API surface
16. interceptor-based Authorization injection

---

## Important limitation

This is hardening, not perfect secrecy.

If a secret must exist inside a client process at runtime, it is not mathematically impossible to extract forever. The goal here is to make extraction, repackaging, and live instrumentation much harder and much easier to trip.

---

## Project structure

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
│           └── xml/
│               ├── data_extraction_rules.xml
│               └── network_security_config.xml
├── .github/workflows/
└── README.md
```

---

## Build environment

### Local Android / Termux note

The repository can be edited from Termux, but reliable APK packaging for this project was validated using Windows Android SDK / Android Studio tooling.

### Windows environment used in validation

- Host: Windows 10 machine
- Java: JDK 17
- Android SDK: `%LOCALAPPDATA%\Android\Sdk`
- Emulator available and tested via `adb`
- NDK installed: `26.1.10909125`
- CMake installed: `3.22.1`

---

## How to build the app

### 1. Open in Android Studio

Open the project root in Android Studio.

Android Studio should detect:
- Gradle project
- external native build via CMake
- NDK requirement

If NDK/CMake are missing, install:
- `NDK (Side by side) 26.1.10909125`
- `CMake 3.22.1`

### 2. Command line build

From the project root:

```bash
./gradlew clean assembleDebug
./gradlew assembleRelease
```

On Windows:

```bat
gradlew.bat clean assembleDebug
gradlew.bat assembleRelease
```

### 3. Output paths

Debug APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Unsigned release APK:

```text
app/build/outputs/apk/release/app-release-unsigned.apk
```

Signed release APK used in this project line:

```text
app/build/outputs/apk/release/AIImageGenerator-v1.1.5-release-signed.apk
```

---

## How release signing was done

The release artifact in this repo line was signed with the Windows debug keystore for assignment delivery/testing convenience.

Typical steps:

### 1. zipalign

```bat
%LOCALAPPDATA%\Android\Sdk\build-tools\35.0.0\zipalign.exe -f -p 4 app-release-unsigned.apk aligned.apk
```

### 2. apksigner

```bat
%LOCALAPPDATA%\Android\Sdk\build-tools\35.0.0\apksigner.bat sign ^
  --ks %USERPROFILE%\.android\debug.keystore ^
  --ks-pass pass:android ^
  --key-pass pass:android ^
  --ks-key-alias androiddebugkey ^
  --out AIImageGenerator-v1.1.5-release-signed.apk ^
  aligned.apk
```

### 3. verify signature

```bat
%LOCALAPPDATA%\Android\Sdk\build-tools\35.0.0\apksigner.bat verify -v AIImageGenerator-v1.1.5-release-signed.apk
```

---

## How to put the APK into an emulator

### ADB install

With an emulator/device already running:

```bat
%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe devices
%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe -s emulator-5554 install -r app\build\outputs\apk\release\AIImageGenerator-v1.1.5-release-signed.apk
```

### Launch the app

```bat
%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe -s emulator-5554 shell monkey -p com.cs702.aigenerator -c android.intent.category.LAUNCHER 1
```

---

## How Windows emulator testing was done

The tested flow was:

1. Build release APK on Windows
2. Sign release APK
3. Install with `adb install -r`
4. Launch with `adb shell monkey -p com.cs702.aigenerator -c android.intent.category.LAUNCHER 1`
5. Confirm app starts successfully on emulator

This was repeated across the hardened releases to make sure the new protections did not immediately lock the legitimate app path.

---

## GitHub release process

### 1. Push code

```bash
git add -A
git commit -m "security: ..."
git push origin main
```

### 2. Create release with GitHub CLI

Example:

```bash
gh release create v1.1.5 \
  ./AIImageGenerator-v1.1.5-release-signed.apk#AIImageGenerator-v1.1.5-release-signed.apk \
  --title "v1.1.5" \
  --notes "Release notes here"
```

### 3. View release

```bash
gh release view v1.1.5
```

---

## Technical notes about encryption / obfuscation design

### Why not just store the key in Java?

Because plain Java string constants are trivial to recover through:
- jadx / CFR / fernflower
- strings search in dex
- Frida hook on return values
- smali patching

### Why C/JNI?

C/JNI is still reversible, but it increases the work factor:
- attackers must inspect `.so`
- reconstruction logic is split away from Java business flow
- native environment checks can fail closed earlier

### Why the custom syllable encoding?

The `di / da / tu / ka` representation is not cryptography. It is structured obfuscation meant to:
- remove obvious long hex literals
- make quick string-grep extraction less useful
- require custom decode understanding

### Why integrity checks on APK entries?

Repackaging attacks often change:
- `classes.dex`
- `AndroidManifest.xml`
- `resources.arsc`
- native libraries

By hashing and re-checking these entries at runtime, common patch/rebuild workflows become more fragile.

### Why interceptor-based Authorization injection?

This reduces how often the API key appears in normal app business code.

Without it, many hooks can target:
- Activity methods
- Retrofit service call sites
- helper functions carrying the key as a parameter

With the interceptor model, that value is injected closer to the actual request path.

---

## Known limitations

- Client-side secrets are never absolutely safe forever
- A sufficiently determined analyst with full process control can still work toward extraction
- This project focuses on layered hardening for coursework, not claiming impossible security guarantees

---

## Repo and release links

- Repository: <https://github.com/kndhjk/AIImageGenerator>
- Releases: <https://github.com/kndhjk/AIImageGenerator/releases>
- Current release target: `v1.1.5`
