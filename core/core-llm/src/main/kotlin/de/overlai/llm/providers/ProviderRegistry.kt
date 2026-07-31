package de.overlai.llm.providers

import de.overlai.llm.AuthScheme
import de.overlai.llm.Capability
import de.overlai.llm.ProviderConfig
import de.overlai.llm.SystemPlacement

// CHANGE-MARKER v0.1.0: Provider-Abstraktion (siehe CHANGELOG.md)
// Die Provider-Tabelle. Neue OpenAI-kompatible Provider sind reine Einträge hier
// (base_url + capability-flags). Anthropic bekommt in M5 einen eigenen Adapter
// (systemPlacement=TOP_LEVEL, ApiKeyHeader) — der Eintrag ist schon vorbereitet.
//
// WICHTIG (aus Feasibility-Skeptiker): Modellnamen + einzelne Capabilities sind
// VOR dem Ship gegen die Live-Doku zu verifizieren. Web-Search ist NICHT
// einheitlich; Werte hier sind der aktuelle Kenntnisstand, kein Vertrag.
object ProviderRegistry {
    val OPENAI =
        ProviderConfig(
            id = "openai",
            displayName = "OpenAI",
            baseUrl = "https://api.openai.com",
            authScheme = AuthScheme.Bearer,
            capabilities =
                setOf(
                    Capability.CHAT,
                    Capability.VISION,
                    Capability.WEB_SEARCH_NATIVE,
                    Capability.TRANSCRIPTION,
                    Capability.TOOL_USE,
                ),
            defaultModel = "gpt-4o",
            transcribePath = "/v1/audio/transcriptions",
        )

    val ANTHROPIC =
        ProviderConfig(
            id = "anthropic",
            displayName = "Anthropic (Claude)",
            baseUrl = "https://api.anthropic.com",
            authScheme =
                AuthScheme.ApiKeyHeader(
                    headerName = "x-api-key",
                    extraHeaders = mapOf("anthropic-version" to "2023-06-01"),
                ),
            capabilities =
                setOf(
                    Capability.CHAT,
                    Capability.VISION,
                    Capability.WEB_SEARCH_NATIVE,
                    Capability.TOOL_USE,
                ),
            defaultModel = "claude-sonnet-5",
            chatPath = "/v1/messages",
            requiresMaxTokens = true,
            systemPlacement = SystemPlacement.TOP_LEVEL,
        )

    val DEEPSEEK =
        ProviderConfig(
            id = "deepseek",
            displayName = "DeepSeek",
            baseUrl = "https://api.deepseek.com",
            authScheme = AuthScheme.Bearer,
            // Mainline text-only; kein natives Web-Search -> externer Key nötig.
            capabilities = setOf(Capability.CHAT, Capability.WEB_SEARCH_EXTERNAL, Capability.TOOL_USE),
            defaultModel = "deepseek-chat",
        )

    val GROK =
        ProviderConfig(
            id = "grok",
            displayName = "xAI Grok",
            baseUrl = "https://api.x.ai",
            authScheme = AuthScheme.Bearer,
            capabilities =
                setOf(Capability.CHAT, Capability.VISION, Capability.WEB_SEARCH_NATIVE, Capability.TOOL_USE),
            defaultModel = "grok-2-latest",
        )

    val KIMI =
        ProviderConfig(
            id = "kimi",
            displayName = "Moonshot Kimi",
            baseUrl = "https://api.moonshot.ai",
            authScheme = AuthScheme.Bearer,
            capabilities = setOf(Capability.CHAT, Capability.VISION, Capability.TOOL_USE),
            defaultModel = "moonshot-v1-8k",
        )

    val OPENROUTER =
        ProviderConfig(
            id = "openrouter",
            displayName = "OpenRouter",
            baseUrl = "https://openrouter.ai/api",
            authScheme = AuthScheme.Bearer,
            capabilities = setOf(Capability.CHAT, Capability.VISION, Capability.TOOL_USE),
            defaultModel = "openai/gpt-4o",
        )

    // Google Gemini über den OpenAI-kompatiblen Shim-Endpoint (/v1beta/openai/).
    // Kostenloser Tier mit großzügigem Kontingent (Flash-Modelle) — daher für den
    // "kostenlos testen"-Fokus interessant. Vor Ship verifizieren (Feasibility §4).
    val GEMINI =
        ProviderConfig(
            id = "gemini",
            displayName = "Google Gemini (free tier)",
            baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai",
            authScheme = AuthScheme.Bearer,
            capabilities =
                setOf(Capability.CHAT, Capability.VISION, Capability.WEB_SEARCH_NATIVE, Capability.TOOL_USE),
            defaultModel = "gemini-2.0-flash",
        )

    // IDs von Providern mit kostenlosem/gratis-Tier — im Onboarding markiert.
    val FREE_TIER_IDS: Set<String> = setOf("gemini", "openrouter")

    // Reihenfolge = Anzeige-Reihenfolge im Provider-Picker. OpenAI zuerst (MVP),
    // dann die kostenlosen Optionen (Gemini/OpenRouter) prominent.
    val all: List<ProviderConfig> = listOf(OPENAI, GEMINI, OPENROUTER, ANTHROPIC, GROK, DEEPSEEK, KIMI)

    fun byId(id: String): ProviderConfig? = all.firstOrNull { it.id == id }
}
