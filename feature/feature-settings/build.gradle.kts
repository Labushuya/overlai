// :feature-settings — Einstellungs-Hub: Übersichtsliste, Darstellung (Theme),
// Über&Datenschutz. Reine core-Deps; Komposition der anderen Screens passiert in :app.
plugins {
    alias(libs.plugins.overlai.android.feature)
}

dependencies {
    implementation(project(":core:core-ui"))
    implementation(project(":core:core-common"))
    implementation(project(":core:core-data"))
}
