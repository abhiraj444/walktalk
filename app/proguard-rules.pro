# Proguard rules for WalkTalk

# WebRTC
-keep class org.webrtc.** { *; }

# Firebase
-keepattributes *Annotation*

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Java-WebSocket
-keep class org.java_websocket.** { *; }
