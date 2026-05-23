# Preserve the underlying native JNI library references from code shrinking
-keep class org.signal.libsignal.** { *; }
-keep class org.whispersystems.libsignal.** { *; }
-keep class org.webrtc.** { *; }

# Prevent R8 from stripping or renaming native JNI code entry points
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# Keep native library loading signatures intact
-keepclassmembers class * {
    *** loadLibrary(...);
}

# Preserve kotlinx.serialization metadata and deserializers
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keepclassmembers class * {
    *** Companion;
}
-keep class kotlinx.serialization.json.** { *; }
-keep class com.enclave.app.network.** { *; }
-keep class com.enclave.app.crypto.** { *; }
-keep class com.enclave.app.webrtc.** { *; }
-keep class com.enclave.app.models.** { *; }
-keep class com.enclave.app.data.local.** { *; }
-keep class com.enclave.app.data.local.* { *; }

# Ignore warnings from slf4j and other transitive third-party dependencies during R8/ProGuard processing
-dontwarn org.slf4j.**
-dontwarn okio.**
-dontwarn io.ktor.**
-dontwarn org.signal.**
-dontwarn org.webrtc.**

