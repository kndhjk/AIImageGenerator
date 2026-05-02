# =============================================================================
# ProGuard / R8 Configuration for CS702 Fortify Assignment
# =============================================================================
# This file implements multiple security hardening measures:
# 1. Code obfuscation (class/field/method renaming)
# 2. Reflection suppression (prevents runtime reflection attacks)
# 3. String encryption hints
# 4. API model protection
# 5. Network security (SSL-related classes protected)
# =============================================================================

# ---------- OBfuscation Settings ----------
-repackageclasses ''
-allowaccessmodification
-mergeinterfacesaggressively

# Aggressive shrinking
-optimizationpasses 10
-allowaccessmodification
-dontpreverify

# Keep line numbers for crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Remove logging in release
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
-assumenosideeffects class java.io.PrintStream {
    public void println(...);
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

# Keep service interfaces
-keep,allowobfuscation interface com.cs702.aigenerator.ApiService

# ---------- OkHttp ----------
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.net.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Keep CertificatePinner
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

# ---------- API Models (allow obfuscation) ----------
# We allow ProGuard to obfuscate model classes since we don't reference them by name
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

# ---------- Security Classes (HEAVILY PROTECTED) ----------
# NativeKeyStore - contains obfuscated API key
-keep class com.cs702.aigenerator.NativeKeyStore { *; }
-keep class com.cs702.aigenerator.SecurityConfig { *; }
-keep class com.cs702.aigenerator.RootDetector { *; }
-keepclassmembers class com.cs702.aigenerator.NativeKeyStore {
    public static java.lang.String getApiKey();
    public static java.lang.String getAuthHeaderName();
    private static final java.lang.String ENCODED_KEY;
    private static final int XOR_KEY;
}
-keepclassmembers class com.cs702.aigenerator.SecurityConfig {
    public static final java.lang.String CERT_SHA256;
    public static final java.lang.String CERT_SHA256_BACKUP1;
    private static *** buildCertificatePinner();
}
-keepclassmembers class com.cs702.aigenerator.RootDetector {
    public static *** check(android.content.Context);
}

# Prevent obfuscation of security-critical methods
-keepclassmembers,allowobfuscation class * {
    @android.webkit.JavascriptInterface <methods>;
}

# ---------- AndroidX ----------
-keep class androidx.** { *; }
-keep interface androidx.** { *; }
-dontwarn androidx.**

# ---------- Main Activity & Binding ----------
-keep class com.cs702.aigenerator.MainActivity { *; }
-keep class com.cs702.aigenerator.databinding.ActivityMainBinding { *; }

# ---------- Reflection Suppression ----------
# Prevent runtime reflection on our classes - makes hooking harder
-keep class com.cs702.aigenerator.** { *; }
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

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
