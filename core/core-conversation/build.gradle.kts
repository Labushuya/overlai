// :core-conversation — gemeinsame Chat-/Quick-Action-Engine (mapError, aktiver
// Provider, Stream-Schleife). Von feature-chat und feature-share genutzt, damit
// die Logik nicht dupliziert. Android-Library (braucht Context via SettingsStore-Kette).
plugins {
    alias(libs.plugins.overlai.android.library)
}

dependencies {
    implementation(project(":core:core-common"))
    implementation(project(":core:core-llm"))
    implementation(project(":core:core-security"))
    implementation(project(":core:core-data"))
    implementation(libs.kotlinx.coroutines.android)
}
