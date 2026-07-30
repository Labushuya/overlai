// :feature-permissions — Permission Hub (lite -> full). Live-Status je Berechtigung
// + Deep-Link-"Fix"-Buttons. Löst den Nutzer-Schmerzpunkt "Bubble unsichtbar".
plugins {
    alias(libs.plugins.overlai.android.feature)
}

dependencies {
    implementation(project(":core:core-ui"))
    implementation(project(":core:core-common"))
    implementation(project(":core:core-security"))
}
