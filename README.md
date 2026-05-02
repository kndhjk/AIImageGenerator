# AI Image Generator

Android app for CS702 — Build & Fortify assignment. Generates AI images from text prompts using the `ai.elliottwen.info` API.

## Features

- **Text-to-Image**: Enter a prompt, tap Generate, view the result
- **Cancel Button**: Stop a pending request at any time
- **Save to Gallery**: Download generated images to your device's photo gallery
- **Dark Theme UI**: Modern Material Design dark theme

## Security (Fortify — Part 2)

The app protects its API key against decompilation by peers:

- **String Reversal Obfuscation**: The Authorization token is stored reversed as a plain ASCII string — not in plaintext anywhere in the bytecode
- **Decoy Methods**: Two public methods (`getFakeApiKey`, `isKeyValid`) contain fake Base64 strings that look like real keys but are never called at runtime — they confuse decompilers
- **ProGuard/R8 Hardening**: All private static fields (`_rev`, `_fake1`, `_fake2`) are explicitly preserved; class/method names are renamed; logging is stripped in release builds
- **Runtime Validation**: At decode time, the first character must be `'c'` — any tampering causes the key to return empty, silently failing authentication

## API Flow

```
1. POST /auth  (Authorization: <api_key>)
   ← {"signature": "..."}
2. POST /generate_image  (Authorization: <api_key>, body: {"signature": "...", "prompt": "..."})
   ← "images/xxx.jpg"  (plain string)
3. GET /images/xxx.jpg  → display
```

## Tech Stack

- **Language**: Java 17 / Android API 34
- **Networking**: Retrofit 2 + OkHttp 4.12 + Gson
- **Image Loading**: Glide 4.16
- **UI**: Material Components + ViewBinding
- **Build**: Gradle 8.4, minSdk 24

## Build

```bash
./gradlew assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

## CI / Release

GitHub Actions automatically builds and publishes a debug APK on every push to `main`.

## Disclaimer

The bundled API key is a course placeholder. Replace it before submission.