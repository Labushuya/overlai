// :feature-chat — Chat-Screen + Streaming-ViewModel (verbindet core-llm mit UI).
plugins {
    alias(libs.plugins.overlai.android.feature)
}

dependencies {
    implementation(project(":core:core-ui"))
    implementation(project(":core:core-common"))
    implementation(project(":core:core-llm"))
    implementation(project(":core:core-security"))
    implementation(project(":core:core-data"))
    implementation(libs.coil.compose)
    implementation(libs.kotlinx.coroutines.android)
}
