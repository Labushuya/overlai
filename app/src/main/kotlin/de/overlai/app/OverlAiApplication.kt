package de.overlai.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

// CHANGE-MARKER v0.1.0: Initiales Projektgrundgerüst (siehe CHANGELOG.md)
// Application-Einstiegspunkt. @HiltAndroidApp bootet den DI-Graph.
@HiltAndroidApp
class OverlAiApplication : Application()
