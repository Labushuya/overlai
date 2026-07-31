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
                "gemini" -> listOf("gemini-2.0-flash", "gemini-1.5-pro", "gemini-1.5-flash")
                "kimi" -> listOf("moonshot-v1-8k", "moonshot-v1-32k", "moonshot-v1-128k")
                "grok" -> listOf("grok-2-latest", "grok-2-vision-latest")
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
