# Proguard rules for WalkTalk

# WebRTC (Native JNI and reflection)
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**

# Firebase Data Models & Annotations
-keepattributes *Annotation*
-keepattributes Signature
-keepclassmembers class com.walktalk.data.firebase.** {
    <fields>;
    public <init>();
}
-keep class com.walktalk.data.firebase.** { *; }

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Java-WebSocket
-keep class org.java_websocket.** { *; }

# Google Play Services (Location & Maps)
-keep class com.google.android.gms.location.** { *; }
-keep class com.google.android.gms.maps.** { *; }
-dontwarn com.google.android.gms.**

# Jetpack Glance
-keep class androidx.glance.** { *; }
