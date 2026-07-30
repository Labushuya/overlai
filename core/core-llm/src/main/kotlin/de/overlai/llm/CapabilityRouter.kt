package de.overlai.llm

// CHANGE-MARKER v0.1.0: Provider-Abstraktion (siehe CHANGELOG.md)
// Entscheidet, ob ein Feature beim gewählten Provider verfügbar ist — die UI
// nutzt das, um Buttons AUSZUGRAUEN statt still zu no-op'en (Design-Vorgabe).
class CapabilityRouter {
    enum class FeatureState {
        AVAILABLE, // direkt nutzbar
        NEEDS_EXTERNAL_KEY, // nutzbar, aber User muss einen externen Such-Key hinterlegen
        UNAVAILABLE, // Provider kann es nicht -> ausgrauen
    }

    fun visionState(config: ProviderConfig): FeatureState =
        if (config.supports(Capability.VISION)) FeatureState.AVAILABLE else FeatureState.UNAVAILABLE

    fun transcriptionState(config: ProviderConfig): FeatureState =
        if (config.supports(Capability.TRANSCRIPTION)) FeatureState.AVAILABLE else FeatureState.UNAVAILABLE

    // Web-Suche: nativ bevorzugt, sonst externer Key, sonst nicht verfügbar.
    fun webSearchState(
        config: ProviderConfig,
        hasExternalSearchKey: Boolean,
    ): FeatureState =
        when {
            config.supports(Capability.WEB_SEARCH_NATIVE) -> FeatureState.AVAILABLE
            config.supports(Capability.WEB_SEARCH_EXTERNAL) && hasExternalSearchKey -> FeatureState.AVAILABLE
            config.supports(Capability.WEB_SEARCH_EXTERNAL) -> FeatureState.NEEDS_EXTERNAL_KEY
            else -> FeatureState.UNAVAILABLE
        }
}
