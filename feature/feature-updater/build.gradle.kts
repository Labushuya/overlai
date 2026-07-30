// :feature-updater — In-App-Updater. latest.json prüfen, APK laden, sha256 verifizieren,
// via PackageInstaller installieren. Kernlogik (SemVer, Verifier) JVM-testbar.
plugins {
    alias(libs.plugins.overlai.android.feature)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(project(":core:core-ui"))
    implementation(project(":core:core-common"))

    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.mockwebserver)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.junit)
}
