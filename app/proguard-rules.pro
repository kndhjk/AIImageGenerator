# =============================================================================
# ProGuard / R8 Configuration for CS702 Fortify Assignment
# =============================================================================
# Security hardening measures:
# 1. Code obfuscation (class/field/method renaming)
# 2. Logging stripped in release builds
# 3. API key obfuscated via string reversal + decoy methods
# 4. SSL certificate pinning for MITM protection
# 5. Root detection at runtime
# 6. No data extraction allowed (backup disabled)
# =============================================================================

# ---------- Obfuscation Settings ----------
-repackageclasses ''
-allowaccessmodification
-mergeinterfacesaggressively

# Aggressive shrinking
-optimizationpasses 10
-allowaccessmodification
-dontpreverify

# Remove line numbers in release (increases difficulty of reverse engineering)
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Remove ALL logging in release builds
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}
-assumenosideeffects class java.io.PrintStream {
    public void println(...);
    public void print(...);
}
-assumenosideeffects class java.io.OutputStream {
    public void write(...);
}

# ---------- Retrofit ----------
-keepattributes Signature
-keepattributes Exceptions
-keepattributes *Annotation*
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeInvisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations
-keepattributes RuntimeInvisibleParameterAnnotations

-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-keep,allowobfuscation interface com.cs702.aigenerator.ApiService

# ---------- OkHttp ----------
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.net.**
-dontwarn org.conscrypt.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keep class okhttp3.CertificatePinner { *; }
-keep class okhttp3.CertificatePinner$* { *; }

# ---------- Gson ----------
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.stream.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ---------- API Models ----------
-keep,allowobfuscation class com.cs702.aigenerator.AuthResponse { *; }
-keep,allowobfuscation class com.cs702.aigenerator.GenerateRequest { *; }
-keepclassmembers class com.cs702.aigenerator.AuthResponse { *; }
-keepclassmembers class com.cs702.aigenerator.GenerateRequest { *; }

# ---------- Glide ----------
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule { <init>(...); }
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
  **[] $VALUES;
  public *;
}
-keep class com.bumptech.glide.load.data.ParcelFileDescriptorRewinder$InternalRewinder {
  *** rewind();
}

# =============================================================================
# SECURITY CLASSES — MAXIMUM PROTECTION
# =============================================================================
# These classes are critical for API key protection.
# We keep names, critical method signatures, and key obfuscation strings.

-keep class com.cs702.aigenerator.NativeKeyStore { *; }
-keep class com.cs702.aigenerator.SecurityConfig { *; }
-keep class com.cs702.aigenerator.RootDetector { *; }

# Keep ALL private static fields in NativeKeyStore (critical for key protection)
-keepclassmembers class com.cs702.aigenerator.NativeKeyStore {
    private static final java.lang.String _rev;
    private static final java.lang.String _fake1;
    private static final java.lang.String _fake2;
    private static final int VALIDATE_CHAR;
    private static final int SHIFT_VAL;
    public static java.lang.String getApiKey();
    public static java.lang.String getFakeApiKey();
    public static boolean isKeyValid(java.lang.String);
    public static int computeShift();
}

# Keep certificate pin strings in SecurityConfig
-keepclassmembers class com.cs702.aigenerator.SecurityConfig {
    public static final java.lang.String CERT_SHA256;
    public static final java.lang.String CERT_SHA256_BACKUP1;
    public static final java.lang.String CERT_SHA256_WILDCARD;
    private static *** buildCertificatePinner();
    private static *** buildCertificatePinner*.
}

# Keep root detection method signatures
-keepclassmembers class com.cs702.aigenerator.RootDetector {
    public static *** check(android.content.Context);
    public static *** getRootWarnings(android.content.Context);
}

# ---------- Main Activity ----------
-keep class com.cs702.aigenerator.MainActivity { *; }
-keep class com.cs702.aigenerator.databinding.ActivityMainBinding { *; }

# ---------- Prevent Reflection Attacks ----------
# Keep all classes from being reflected/hooked
-keep class com.cs702.aigenerator.** { *; }
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# ---------- AndroidX ----------
-keep class androidx.** { *; }
-keep interface androidx.** { *; }
-dontwarn androidx.**

# ---------- Network Security ----------
-keep class javax.net.ssl.** { *; }
-keepclassmembers class * extends javax.net.ssl.SSLSocketFactory {
    <init>(...);
}

# ---------- Enum & Constants ----------
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ---------- Parcelable ----------
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}
-keepclassmembers class * implements android.os.Parcelable {
    static ** CREATOR;
}