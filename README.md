# AI Image Generator

Android app for CS702 — Build & Fortify assignment. Generates AI images from text prompts using the `ai.elliottwen.info` API.

## Features

- **Text-to-Image**: Enter a prompt, tap Generate, view the result
- **Cancel Button**: Stop a pending request at any time
- **Save to Gallery**: Download generated images to your device's photo gallery
- **Dark Theme UI**: Modern Material Design dark theme

## Security (Fortify — Part 2)

- **API Key Obfuscation**: Authorization token is stored using split-string + reverse + Base64 encoding — not stored in plaintext anywhere in the bytecode
- **SSL Certificate Pinning**: OkHttp client pins to the `ai.elliottwen.info` certificate SHA-256 fingerprint (disabled in debug builds for emulator testing)
- **ProGuard/R8 Minification**: Release builds apply code shrinking, symbol renaming, and logging removal
- **Root Detection Warning**: App detects root/Magisk/Xposed and shows a security warning (does not block usage)

## Tech Stack

- **Language**: Java 17 / Android API 34
- **Networking**: Retrofit 2 + OkHttp 4.12 + Gson
- **Image Loading**: Glide 4.16
- **UI**: Material Components + ViewBinding
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
./gradlew assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

## CI / Release

GitHub Actions automatically builds and publishes a debug APK on every push to `main`.

## Disclaimer

The bundled API key is a course placeholder. Replace it before submission.