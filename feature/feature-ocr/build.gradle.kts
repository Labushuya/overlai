// :feature-ocr — ML Kit Text Recognition (gebündeltes Modell, offline, kein GMS-Zwang).
plugins {
    alias(libs.plugins.overlai.android.library)
    alias(libs.plugins.overlai.android.hilt)
}

dependencies {
    implementation(project(":core:core-common"))
    implementation(libs.mlkit.text.recognition)
    implementation(libs.kotlinx.coroutines.android)
}
