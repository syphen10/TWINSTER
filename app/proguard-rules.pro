# Twinster release ProGuard/R8 rules.
# isMinifyEnabled + isShrinkResources are on for release builds — anything reflective
# or that relies on class/method names surviving needs an explicit keep rule here.

# ---- General ----
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions
-keepattributes InnerClasses
-keepattributes SourceFile,LineNumberTable

# ---- kotlinx.serialization ----
# The serialization compiler plugin generates a synthetic $serializer companion for every
# @Serializable class; R8 will strip it (or the fields it reflects on) without these rules.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keep,includedescriptorclasses class kotlinx.serialization.**{ *; }
-keepclassmembers class kotlinx.serialization.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep the generated serializer for every @Serializable class in the app (Genius API response
# models and the shared Taste Twin payload), plus their companions and the classes themselves so
# field names/order used by the (de)serializer survive.
-keepclassmembers @kotlinx.serialization.Serializable class com.twinster.app.** {
    static **$Companion Companion;
    static kotlinx.serialization.KSerializer serializer(...);
    *** Companion;
}
-keepclassmembers class com.twinster.app.**$$serializer {
    *** INSTANCE;
    kotlinx.serialization.KSerializer[] childSerializers(...);
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class com.twinster.app.**$$serializer { *; }
-keep @kotlinx.serialization.Serializable class com.twinster.app.** { *; }

# ---- Retrofit ----
# Retrofit's interface methods are called via dynamic proxy / reflection on the declared
# return types (Call<T>, suspend -> T). Keep the API interface(s) and their signatures.
-keepattributes Signature, Exceptions
-keep,allowobfuscation interface com.twinster.app.data.GeniusApi { *; }
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>

# Retrofit platform detection / OkHttp reflective bits (per Retrofit's own consumer rules,
# duplicated here defensively since we're pinning an explicit ProGuard file list).
-dontwarn retrofit2.Platform$Java8
-dontwarn org.codehaus.mojo.animal_sniffer.*
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
-keepclassmembers,allowshrinking,allowobfuscation class kotlin.coroutines.Continuation

# OkHttp / Okio (defensive — both already ship consumer rules, but we pin explicit files here)
-dontwarn okhttp3.**
-dontwarn okio.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# ---- androidx.security (security-crypto / Tink) used for encrypted local storage ----
# Tink does reflective classloading of its primitive/key-manager implementations.
-keep class com.google.crypto.tink.** { *; }
-keep interface com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**
-keepclassmembers class * extends com.google.crypto.tink.shaded.protobuf.GeneratedMessageLite {
    <fields>;
}

# ---- Coil (image loading) ----
# Coil ships its own consumer-proguard-rules.pro (keeps its decoders/fetchers via ServiceLoader),
# these are defensive extras only.
-dontwarn coil.**
-keep class coil.** { *; }

# ---- ZXing (QR code generation for share cards) ----
-dontwarn com.google.zxing.**
-keep class com.google.zxing.** { *; }

# ---- App's own model / data classes reached via reflection-adjacent APIs ----
# (MusicProfile and friends are read by kotlinx.serialization above; this additionally
# protects enum valueOf()/values() usage which R8 can otherwise break under aggressive
# optimization when enums are (de)serialized by name.)
-keepclassmembers enum com.twinster.app.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ---- Jetpack Compose / kotlin metadata ----
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**

# ---- TensorFlow Lite / Task Library (on-device YAMNet genre detection) ----
# The interpreter and Task Library resolve native methods and a handful of AutoValue-generated
# classes by name; the GPU/NNAPI delegate classes are referenced reflectively even though this app
# never enables them. Keep the whole surface defensively rather than chase individual R8 warnings.
-keep class org.tensorflow.lite.** { *; }
-keep interface org.tensorflow.lite.** { *; }
-dontwarn org.tensorflow.lite.**
