// :core-common — reines Kotlin/JVM (Result-Typen, Dispatchers, SemVer, Redacting-Logger).
// Keine Android-Deps -> schnellste Tests.
plugins {
    alias(libs.plugins.overlai.jvm.library)
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)
}
