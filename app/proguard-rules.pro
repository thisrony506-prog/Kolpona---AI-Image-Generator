# Kolpona release rules
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable
-keep class com.kolpona.app.** { *; }

# OkHttp / Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# Coroutines
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# Room
-keep class androidx.room.** { *; }
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Coil
-keep class coil.** { *; }
-dontwarn coil.**

# Start.io
-keep class com.startapp.** { *; }
-keep interface com.startapp.** { *; }
-dontwarn com.startapp.**
-keep class com.startapp.sdk.** { *; }
-keep class com.startapp.android.** { *; }

# Play Services ads identifier (optional, used by some ad SDKs)
-dontwarn com.google.android.gms.**

# DataStore
-keep class androidx.datastore.** { *; }

# Keep BuildConfig fields (values stay in the APK; do not log them)
-keep class com.kolpona.app.BuildConfig { *; }

-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
