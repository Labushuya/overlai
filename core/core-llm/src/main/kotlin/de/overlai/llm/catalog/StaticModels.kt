package de.overlai.llm.catalog

import de.overlai.llm.ModelInfo
import de.overlai.llm.ProviderConfig

// CHANGE-MARKER v0.4.0: Modell-Katalog (siehe CHANGELOG.md)
// Kuratierter Fallback für Provider ohne (bestätigten) /models-Endpoint. Ehrlicher
// als eine ungefilterte/kaputte Live-Antwort. defaultModel ist immer enthalten,
// damit der Picker nie leer ist.
internal object StaticModels {
    fun forProvider(config: ProviderConfig): List<ModelInfo> {
        val curated =
            when (config.id) {
                "gemini" -> listOf("gemini-2.5-flash", "gemini-2.5-pro", "gemini-2.5-flash-lite")
                "kimi" -> listOf("kimi-k2.6", "moonshot-v1-128k")
                "grok" -> listOf("grok-4.5", "grok-4.3")
                // Falls der Live-Katalog scheitert: eine echte Auswahl statt 1 Eintrag.
                "openrouter" -> listOf("openai/gpt-4o", "anthropic/claude-3.5-sonnet")
                else -> emptyList()
            }.map { ModelInfo(id = it, displayName = it) }

        return if (curated.any { it.id == config.defaultModel }) {
            curated
        } else {
            listOf(ModelInfo(config.defaultModel, config.defaultModel)) + curated
        }
    }
}
