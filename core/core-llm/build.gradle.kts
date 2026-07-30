// :core-llm — Provider-Abstraktion + OpenAI-compat Transport + Adapter + CapabilityRouter.
// BEWUSST reines Kotlin/JVM (kein Android-UI) -> vollständig mit MockWebServer testbar.
plugins {
    alias(libs.plugins.overlai.jvm.library)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(project(":core:core-common"))

    implementation(libs.okhttp)
    implementation(libs.okhttp.sse)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.mockwebserver)
}
