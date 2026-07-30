// CHANGE-MARKER v0.1.0: Initiales Projektgrundgerüst (siehe CHANGELOG.md)
// build-logic — eigenständiger Composite-Build, der die OverlAI-Convention-Plugins
// bereitstellt. Kapselt AGP/Kotlin/Compose/Hilt-Setup, damit Module nicht duplizieren.

plugins {
    `kotlin-dsl`
}

group = "de.overlai.buildlogic"

// Toolchain auf JDK 21 (matcht CI setup-java 21).
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
}

// Registriert die Convention-Plugins unter stabilen IDs (siehe libs.versions.toml [plugins]).
gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "overlai.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "overlai.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = "overlai.android.compose"
            implementationClass = "AndroidComposeConventionPlugin"
        }
        register("androidFeature") {
            id = "overlai.android.feature"
            implementationClass = "AndroidFeatureConventionPlugin"
        }
        register("jvmLibrary") {
            id = "overlai.jvm.library"
            implementationClass = "JvmLibraryConventionPlugin"
        }
        register("androidHilt") {
            id = "overlai.android.hilt"
            implementationClass = "AndroidHiltConventionPlugin"
        }
    }
}
