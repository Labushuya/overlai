# CHANGE-MARKER v0.1.0: Initiales Projektgrundgerüst (siehe CHANGELOG.md)
# OverlAI — ProGuard/R8-Regeln (Release, isMinifyEnabled=true).

# kotlinx.serialization: @Serializable-Klassen + generierte Serializer behalten.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class de.overlai.**$$serializer { *; }
-keepclassmembers class de.overlai.** {
    *** Companion;
}
-keepclasseswithmembers class de.overlai.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Tink (Reflection auf Krypto-Provider)
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**
