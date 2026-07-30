// CHANGE-MARKER v0.1.0: Initiales Projektgrundgerüst (siehe CHANGELOG.md)
// OverlAI — Root-Build. Plugins hier nur DEKLARIERT (apply false), angewendet
// werden sie pro Modul über die Convention-Plugins aus build-logic.
// ktlint/detekt/kover werden auf alle Subprojekte verteilt (Qualitäts-Gates).

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.kover)
}

// ktlint + detekt in JEDEM Subprojekt aktivieren, damit `./gradlew ktlintCheck detekt`
// projektweit greift (CI-Gate). Kover aggregiert Coverage über die Subprojekte.
subprojects {
    apply(plugin = rootProject.libs.plugins.ktlint.get().pluginId)
    apply(plugin = rootProject.libs.plugins.detekt.get().pluginId)

    extensions.configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        config.setFrom(rootProject.files("config/detekt/detekt.yml"))
        buildUponDefaultConfig = true
        parallel = true
    }
}

dependencies {
    kover(project(":app"))
    kover(project(":core:core-llm"))
    kover(project(":core:core-security"))
    kover(project(":feature:feature-updater"))
}
