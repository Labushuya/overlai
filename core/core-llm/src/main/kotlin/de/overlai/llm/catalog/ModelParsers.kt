package de.overlai.llm.catalog

import de.overlai.llm.ModelInfo
import kotlinx.serialization.json.Json

// CHANGE-MARKER v0.4.0: Modell-Katalog (siehe CHANGELOG.md)
// Parst /models-Antworten UND filtert auf chat-taugliche Modelle — allowlist-first,
// fail-closed: unbekannte IDs fliegen raus (nicht rein), damit Bild-/Audio-/
// Embedding-Modelle nie im Chat-Picker landen (Nutzer-Anforderung).
internal object ModelParsers {
    // Deny-Netz: Substrings, die ein Modell als Nicht-Chat kennzeichnen.
    private val DENY =
        listOf(
            "embedding", "whisper", "tts", "dall-e", "image", "audio",
            "realtime", "moderation", "transcribe", "rerank", "-search", "vision-preview",
        )

    // Erlaubte ID-Präfixe je OpenAI-Shape-Provider (fail-closed).
    private val OPENAI_ALLOW = listOf("gpt-", "o1", "o3", "o4", "chatgpt")
    private val DEEPSEEK_ALLOW = listOf("deepseek-")

    fun parse(
        providerId: String,
        body: String,
        json: Json,
    ): List<ModelInfo> =
        when (providerId) {
            "openrouter" -> parseOpenRouter(body, json)
            "anthropic" -> parseAnthropic(body, json)
            "deepseek" -> parseOpenAiShape(body, json, DEEPSEEK_ALLOW)
            else -> parseOpenAiShape(body, json, OPENAI_ALLOW) // openai
        }.distinctBy { it.id }.sortedBy { it.displayName.lowercase() }

    private fun deny(id: String): Boolean = DENY.any { id.contains(it, ignoreCase = true) }

    private fun parseOpenAiShape(
        body: String,
        json: Json,
        allow: List<String>,
    ): List<ModelInfo> {
        val resp = json.decodeFromString(OpenAiModelsResponse.serializer(), body)
        return resp.data.mapNotNull { entry ->
            val id = entry.id ?: return@mapNotNull null
            if (deny(id)) return@mapNotNull null
            if (allow.none { id.startsWith(it) }) return@mapNotNull null
            ModelInfo(id = id, displayName = id)
        }
    }

    private fun parseAnthropic(
        body: String,
        json: Json,
    ): List<ModelInfo> {
        val resp = json.decodeFromString(AnthropicModelsResponse.serializer(), body)
        return resp.data.mapNotNull { entry ->
            val id = entry.id ?: return@mapNotNull null
            if (deny(id)) return@mapNotNull null // Anthropic listet nur Chat-Modelle; Deny nur als Netz
            ModelInfo(id = id, displayName = entry.displayName ?: id)
        }
    }

    private fun parseOpenRouter(
        body: String,
        json: Json,
    ): List<ModelInfo> {
        val resp = json.decodeFromString(OpenRouterModelsResponse.serializer(), body)
        return resp.data.mapNotNull { entry ->
            val id = entry.id ?: return@mapNotNull null
            if (deny(id)) return@mapNotNull null
            // Nur Modelle, die Text ausgeben können (kein reines Bild/Embedding).
            val outputs = entry.architecture?.outputModalities ?: emptyList()
            if (outputs.isNotEmpty() && !outputs.any { it.equals("text", ignoreCase = true) }) {
                return@mapNotNull null
            }
            val free = isFree(entry)
            ModelInfo(id = id, displayName = entry.name ?: id, free = free, context = entry.contextLength)
        }
    }

    // Free = der reale Preis ist 0. Der ":free"-Slug-Suffix ALLEIN reicht NICHT:
    // OpenRouter hat viele ":free"-Slugs abgeschaltet ("unavailable for free"),
    // sie tragen dann echten Preis > 0. Nur pricing ist die Wahrheit (fail-closed:
    // fehlende Preisdaten -> NICHT als gratis markieren).
    private fun isFree(entry: OpenRouterModelEntry): Boolean {
        val prompt = entry.pricing?.prompt?.toDoubleOrNull() ?: return false
        val completion = entry.pricing?.completion?.toDoubleOrNull() ?: return false
        return prompt <= 0.0 && completion <= 0.0
    }
}
