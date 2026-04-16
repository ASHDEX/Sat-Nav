# ============================================================
# Satnav – ProGuard / R8 rules
# ============================================================

# ---------- Kotlin ----------
-keepattributes *Annotation*, InnerClasses, Signature, SourceFile, LineNumberTable
-keep class kotlin.Metadata { *; }
-keep class kotlin.reflect.** { *; }
-dontwarn kotlin.reflect.jvm.internal.**

# ---------- Kotlinx Serialization (@Serializable classes) ----------
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
# Keep serializers
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclassmembers class kotlinx.serialization.internal.** { *** Companion; }
# Keep all @Serializable classes and their properties
-keep @kotlinx.serialization.Serializable class * {
    *;
}
-keepclasseswithmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}
# Keep generated serializers
-keep class *Serializer { *; }
-keep class *$$serializer { *; }
# Keep KSerializer implementations
-keep class * implements kotlinx.serialization.KSerializer { *; }
-dontwarn kotlinx.serialization.**

# ---------- Hilt / Dagger ----------
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }
-keepclassmembers class * {
    @javax.inject.Inject <init>(...);
    @javax.inject.Inject <fields>;
}

# ---------- Hilt ViewModels ----------
-keep class androidx.hilt.** { *; }
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}
-keepclassmembers class * extends androidx.lifecycle.AndroidViewModel {
    <init>(android.app.Application);
}

# ---------- Jetpack Compose ----------
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# ---------- MapLibre Android SDK ----------
-keep class org.maplibre.** { *; }
-keep class com.mapbox.** { *; }
-keep class com.google.gson.** { *; }
-keepattributes Signature, InnerClasses, EnclosingMethod
# Keep MapLibre native methods
-keepclasseswithmembers class org.maplibre.** {
    native <methods>;
}
# Keep MapLibre style classes
-keep class * implements org.maplibre.android.style.layers.Layer { *; }
-keep class * implements org.maplibre.android.style.sources.Source { *; }
-dontwarn org.maplibre.**
-dontwarn com.mapbox.**
-dontwarn com.google.gson.**

# ---------- GraphHopper Core (lots of reflection in its JSON/graph loading) ----------
-keep class com.graphhopper.** { *; }
-keep class org.locationtech.jts.** { *; }
-keep class org.slf4j.** { *; }
-keep class ch.qos.logback.** { *; }
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepclassmembers class com.graphhopper.** {
    *** get*(...);
    *** set*(...);
    *** is*(...);
}
# Keep GraphHopper configuration classes
-keep class com.graphhopper.config.** { *; }
# Keep GraphHopper JSON serialization classes
-keep class * implements com.graphhopper.util.JsonFeature { *; }
-keepclassmembers class * {
    @com.fasterxml.jackson.annotation.** *;
}
-dontwarn com.graphhopper.**
-dontwarn org.locationtech.**
-dontwarn org.slf4j.**
-dontwarn ch.qos.logback.**

# ---------- NanoHTTPD ----------
-keep class fi.iki.elonen.** { *; }
-keep class org.nanohttpd.** { *; }
# Keep NanoHTTPD server classes
-keepclassmembers class fi.iki.elonen.NanoHTTPD {
    public *;
    protected *;
}
-keepclassmembers class fi.iki.elonen.NanoHTTPD$* {
    public *;
    protected *;
}
-dontwarn fi.iki.elonen.**
-dontwarn org.nanohttpd.**

# ---------- Play Services Location ----------
-keep class com.google.android.gms.location.** { *; }
-dontwarn com.google.android.gms.**

# ---------- Domain models (kept for data serialisation) ----------
-keep class com.jayesh.satnav.domain.model.** { *; }

# ---------- Security: strip internal detail from error responses ----------
# Exception messages are not exposed to callers in release; R8 removes them
# from stack traces naturally. No extra rule needed — do NOT use
# -dontobfuscate or -keepattributes Exceptions broadly.

# ---------- Remove logging in release ----------
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static int wtf(...);
}
