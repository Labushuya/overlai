// CHANGE-MARKER v0.1.0: Initiales Projektgrundgerüst (siehe CHANGELOG.md)
// OverlAI — Modul-Includes. build-logic ist ein eigenständiger Composite-Build
// (liefert die Convention-Plugins), damit jedes Modul nur ein Plugin anwendet
// statt Build-Config zu duplizieren.

pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "overlai"

include(":app")

include(":core:core-common")
include(":core:core-llm")
include(":core:core-security")
include(":core:core-data")
include(":core:core-ui")
include(":core:core-conversation")

include(":feature:feature-onboarding")
include(":feature:feature-chat")
include(":feature:feature-permissions")
include(":feature:feature-share")
include(":feature:feature-ocr")
include(":feature:feature-updater")
include(":feature:feature-settings")
include(":feature:feature-overlay")
