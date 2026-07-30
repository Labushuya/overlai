package de.overlai.feature.updater

import kotlinx.serialization.Serializable

// CHANGE-MARKER v0.1.0: In-App-Updater (siehe CHANGELOG.md)
// Schema der latest.json (von release.yml auf gh-pages publiziert). Muss mit dem
// dort erzeugten JSON übereinstimmen.
@Serializable
data class LatestManifest(
    val versionName: String,
    val versionCode: Int,
    val minSdk: Int = 0,
    val apkUrl: String,
    val sha256: String,
    val sizeBytes: Long = 0,
    val releaseNotes: String? = null,
    val mandatory: Boolean = false,
)
