// :feature-overlay — System-Overlay-Bubble (SYSTEM_ALERT_WINDOW) + Foreground-Service.
// M3-Skelett: Plattform-Mechanik (Service, Window-Lifecycle, Permission, Toggle) ohne
// LLM. Der Chat im Panel folgt in M3.2 über ConversationEngine (core-conversation).
plugins {
    alias(libs.plugins.overlai.android.feature)
}

dependencies {
    implementation(project(":core:core-ui"))
    implementation(project(":core:core-common"))
    // savedstate: SavedStateRegistryOwner für ComposeView im WindowManager-Overlay
    // (Compose außerhalb einer Activity braucht die ViewTree-Owner explizit).
    implementation(libs.androidx.savedstate)
    implementation(libs.kotlinx.coroutines.android)
}
