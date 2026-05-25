# Preserve Signal Protocol JNI and Models
-keep class org.signal.libsignal.** { *; }
-keepclassmembers class org.signal.libsignal.** { *; }

# Preserve WebRTC native bindings
-keep class org.webrtc.** { *; }
-keepclassmembers class org.webrtc.** { *; }
-keep class J.* { *; }

# Preserve Kotlinx Serialization models
-keepattributes *Annotation*, InnerClasses
-keep,allowoptimization,allowobfuscation @kotlinx.serialization.Serializable class *
-keepclassmembers @kotlinx.serialization.Serializable class * {
    <fields>;
}

# Preserve Supabase Gotrue / Postgrest Network models
-keep class io.github.jan.supabase.** { *; }
-keepclassmembers class io.github.jan.supabase.** { *; }
