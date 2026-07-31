// :core-ui — geteiltes Material-3-Theme (dynamic color, dark mode), Design-Tokens,
// wiederverwendbare Compose-Komponenten (Badges/Pills, Status-Chips).
plugins {
    alias(libs.plugins.overlai.android.library)
    alias(libs.plugins.overlai.android.compose)
}

dependencies {
    implementation(project(":core:core-common"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.coil.compose)
}
