# R8 rules for release builds. Library consumer rules (Room, WorkManager, Retrofit,
# kotlinx-serialization) cover the libraries; these cover what is specific to this app.

# Keep generic signatures and annotations that Retrofit and kotlinx-serialization read reflectively.
-keepattributes Signature, InnerClasses, EnclosingMethod, RuntimeVisibleAnnotations, AnnotationDefault

# Network DTOs and the Retrofit interface: field names are the JSON wire contract
# (/api/v1/gateway/* must stay backward compatible), so they must not be renamed or removed.
-keep class com.mobilemoneyfueltracker.gateway.network.** { *; }
-keepclassmembers class com.mobilemoneyfueltracker.gateway.network.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}

# Retrofit suspend functions are called through a Continuation; R8 full mode strips it otherwise.
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# OkHttp optional platform classes that are not on Android.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
