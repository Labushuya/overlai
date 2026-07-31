// CHANGE-MARKER v0.1.0: Initiales Projektgrundgerüst (siehe CHANGELOG.md)
// :app — die einzige Android-Application. Bindet alle Feature- und Core-Module,
// besitzt das gemergte Manifest und die Release-signingConfig (aus keystore.properties,
// von der CI aus einem base64-Secret rekonstruiert — siehe .github/workflows/release.yml).

import java.util.Properties

plugins {
    alias(libs.plugins.overlai.android.application)
    alias(libs.plugins.overlai.android.compose)
    alias(libs.plugins.overlai.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

// versionCode/versionName: Default für lokale Builds; die CI überschreibt
// versionName mit dem Tag (v1.2.3 -> 1.2.3) via -PversionName / manifestPlaceholders.
val appVersionName = (project.findProperty("overlaiVersionName") as String?) ?: "0.1.0"
val appVersionCode = (project.findProperty("overlaiVersionCode") as String?)?.toInt() ?: 1

android {
    defaultConfig {
        versionCode = appVersionCode
        versionName = appVersionName
        vectorDrawables { useSupportLibrary = true }
    }

    // Release-Signing: liest keystore.properties aus dem Projekt-Root, falls vorhanden.
    // Fehlt die Datei (z.B. lokaler Dev-Build), bleibt es debug-signiert — der Build
    // failt NICHT hart, aber ein so gebautes APK kann später nicht via Updater
    // aktualisiert werden (andere Signatur). Die CI legt die Datei immer an.
    val keystorePropertiesFile = rootProject.file("keystore.properties")
    val hasKeystore = keystorePropertiesFile.exists()
    val keystoreProperties =
        Properties().apply {
            if (hasKeystore) keystorePropertiesFile.inputStream().use { load(it) }
        }

    signingConfigs {
        if (hasKeystore) {
            create("release") {
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig =
                if (hasKeystore) {
                    signingConfigs.getByName("release")
                } else {
                    // Fallback: debug-Signatur (nur für lokale Builds ohne Keystore).
                    signingConfigs.getByName("debug")
                }
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }
}

dependencies {
    implementation(project(":core:core-common"))
    implementation(project(":core:core-ui"))
    implementation(project(":core:core-llm"))
    implementation(project(":core:core-security"))
    implementation(project(":core:core-data"))
    implementation(project(":core:core-conversation"))

    implementation(project(":feature:feature-onboarding"))
    implementation(project(":feature:feature-chat"))
    implementation(project(":feature:feature-permissions"))
    implementation(project(":feature:feature-share"))
    implementation(project(":feature:feature-ocr"))
    implementation(project(":feature:feature-updater"))
    implementation(project(":feature:feature-settings"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.compose.material.icons.extended)
}
