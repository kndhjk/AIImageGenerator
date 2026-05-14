# CS702 Part 3 Attack Notes

## Scope

This file no longer stores any real or historical API key material.

The current build moved key reconstruction to JNI/C and removed the earlier
single-string reversal approach, so the previous static extraction walkthrough
is intentionally retired.

## Current attack surface to discuss in the report

1. **Static APK analysis**
   - Inspect `NativeKeyStore.java`
   - Inspect `app/src/main/cpp/native-key.c`
   - Explain that the app now uses custom symbol encoding + group shuffling +
     rolling byte transform + native validation

2. **Dynamic instrumentation**
   - Hook `NativeKeyStore.getApiKey(Context)` or the native JNI boundary
   - Discuss why runtime extraction is still theoretically possible on a
     sufficiently controlled device

3. **Repackaging / patching**
   - Remove `RuntimeGuard` checks
   - Patch return values
   - Re-sign APK

## Key security conclusion

No client-side secret is unextractable forever.

What this project now does is **raise the reverse-engineering cost**:
- no plaintext key in Java
- no historical key left in docs
- no prebuilt legacy native library with old logic
- runtime blocking on suspicious environments
- native validation only accepts the one intended 128-char lowercase hex key
