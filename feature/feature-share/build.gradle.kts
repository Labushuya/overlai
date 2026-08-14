// :feature-share — Entry-Points ACTION_PROCESS_TEXT + ACTION_SEND (Text/Bild).
// Der MVP-Kern: null Runtime-Permissions, null Policy-Risiko.
plugins {
    alias(libs.plugins.overlai.android.feature)
}

dependencies {
    implementation(project(":core:core-ui"))
    implementation(project(":core:core-common"))
    implementation(project(":core:core-llm"))
    implementation(project(":core:core-security"))
    implementation(project(":core:core-data"))
    implementation(project(":feature:feature-ocr"))
    // P2.4: Share startet neue persistente Chats über den NewChatSheet-Flow (Provider/Modell-Wahl).
    implementation(project(":feature:feature-chat"))
}
