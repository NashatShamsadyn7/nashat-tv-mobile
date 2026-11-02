# ========================================
# Nashat TV - ProGuard / R8 Rules
# ========================================

# Keep line numbers for crash reports but hide source file names
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ----- Model classes (used by Gson reflection) -----
-keep class com.nashat.tv.Channel { *; }
-keep class com.nashat.tv.Movie { *; }
-keep class com.nashat.tv.Episode { *; }
-keep class com.nashat.tv.Actor { *; }

# TMDB response models. Gson fills them by field name, and most fields (e.g.
# TitlePage.results, Title.title) carry no @SerializedName — once R8 renamed
# them every catalogue response parsed to an empty list, so Films and Series
# showed nothing in release builds while debug builds worked.
-keep class com.nashat.tv.tmdb.TmdbModels { *; }
-keep class com.nashat.tv.tmdb.TmdbModels$* { *; }

# ----- Gson -----
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# ----- Firebase -----
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# Keep classes used by Firebase Realtime Database via reflection
-keepclassmembers class com.nashat.tv.** {
    public <init>();
    public *** get*();
    public void set*(***);
}

# ----- ExoPlayer / Media3 -----
-keep class com.google.android.exoplayer2.** { *; }
-dontwarn com.google.android.exoplayer2.**

# ----- Google Cast -----
-keep class com.google.android.gms.cast.** { *; }

# ----- Glide -----
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule {
    <init>(...);
}
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
    **[] $VALUES;
    public *;
}

# ----- OkHttp -----
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**

# ----- WebView JavaScript Interface (NativeBridge) -----
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# ----- BuildConfig (TMDB key etc.) -----
-keep class com.nashat.tv.BuildConfig { *; }

# ----- Hide sensitive logic from easy reversing -----
# Obfuscate scraper and constants aggressively
-repackageclasses 'a'
-allowaccessmodification

# Remove logs in release
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}
