// :feature-chat — Chat-Screen + dünner Adapter-ViewModel über ConversationSession.
plugins {
    alias(libs.plugins.overlai.android.feature)
}

dependencies {
    implementation(project(":core:core-ui"))
    implementation(project(":core:core-common"))
    implementation(project(":core:core-llm"))
    implementation(project(":core:core-security"))
    implementation(project(":core:core-data"))
    // P2.1a: gemeinsamer Chat-Kern (ConversationSession + ChatUiMessage).
    implementation(project(":core:core-conversation"))
    implementation(libs.coil.compose)
    implementation(libs.kotlinx.coroutines.android)
}
