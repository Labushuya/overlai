// :core-security — BYOK-Key-Storage über Android Keystore/TEE (Tink AEAD).
// Android-Library, weil Keystore-APIs Android-Kontext brauchen.
plugins {
    alias(libs.plugins.overlai.android.library)
    alias(libs.plugins.overlai.android.hilt)
}

dependencies {
    implementation(project(":core:core-common"))

    implementation(libs.tink.android)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.junit)
}
