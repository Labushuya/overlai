package de.overlai.llm

// CHANGE-MARKER: Kontextfenster/Usage (Phase 3 E3, siehe CHANGELOG.md)
// Kuratierte Kontextfenster-Größen (in Tokens) für gängige Modelle. Live liefert nur
// OpenRouter ein verlässliches context_length (ModelInfo.context); für alle anderen Provider
// dient diese Tabelle als Quelle. Auflösung: live-Wert zuerst, sonst Prefix-Match hier, sonst
// null (UI zeigt dann nur verbrauchte Tokens ohne Limit). Prefix-Match, weil Modell-IDs oft
// datierte/versionierte Suffixe tragen (z.B. "gpt-4o-2024-08-06").
object ModelContextTable {
    // Reihenfolge: spezifischere Prefixe zuerst. Werte konservativ nach offiziellen Angaben.
    private val CURATED: List<Pair<String, Int>> =
        listOf(
            // OpenAI
            "gpt-4o-mini" to 128_000,
            "gpt-4o" to 128_000,
            "gpt-4.1" to 1_047_576,
            "gpt-4-turbo" to 128_000,
            "gpt-4" to 8_192,
            "gpt-3.5" to 16_385,
            "o1" to 200_000,
            "o3" to 200_000,
            // Anthropic
            "claude-3-5" to 200_000,
            "claude-3" to 200_000,
            "claude-sonnet" to 200_000,
            "claude-opus" to 200_000,
            "claude-haiku" to 200_000,
            "claude" to 200_000,
            // Google Gemini
            "gemini-2.5" to 1_048_576,
            "gemini-2.0" to 1_048_576,
            "gemini-1.5-flash" to 1_048_576,
            "gemini-1.5-pro" to 2_097_152,
            "gemini" to 1_048_576,
            // xAI Grok
            "grok-4" to 256_000,
            "grok-3" to 131_072,
            "grok-2" to 131_072,
            "grok" to 131_072,
            // DeepSeek
            "deepseek-reasoner" to 65_536,
            "deepseek-chat" to 65_536,
            "deepseek" to 65_536,
            // Moonshot Kimi
            "kimi-k2" to 262_144,
            "kimi" to 131_072,
        )

    // Löst das Kontextfenster auf: live-Wert (OpenRouter) hat Vorrang; sonst kuratierter
    // Prefix-Match auf die Modell-ID; sonst null.
    fun resolve(
        modelId: String?,
        liveContext: Int?,
    ): Int? {
        if (liveContext != null && liveContext > 0) return liveContext
        val id = modelId?.lowercase() ?: return null
        // Bei OpenRouter-Style "openai/gpt-4o" den Teil nach dem "/" prüfen.
        val bare = id.substringAfterLast('/')
        return CURATED.firstOrNull { (prefix, _) -> bare.startsWith(prefix) || id.startsWith(prefix) }?.second
    }
}
