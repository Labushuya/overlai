package de.overlai.llm

// CHANGE-MARKER v0.1.0: Provider-Abstraktion (siehe CHANGELOG.md)
// Fähigkeiten, die ein Provider haben KANN. Der CapabilityRouter nutzt diese,
// um Features in der UI auszugrauen statt still zu no-op'en (Design-Vorgabe).
enum class Capability {
    CHAT,
    VISION,
    WEB_SEARCH_NATIVE, // Provider hat eingebautes Grounding (kein extra Key)
    WEB_SEARCH_EXTERNAL, // braucht externen Such-Key (Brave/Tavily) + RAG-Loop
    TRANSCRIPTION,
    TOOL_USE,
}

// Wo der System-Prompt im Request landet: als message-Rolle (OpenAI-Stil) oder
// als Top-Level-Feld (Anthropic Messages API).
enum class SystemPlacement {
    MESSAGE_ROLE,
    TOP_LEVEL,
}

// Authentifizierungs-Schema des Providers.
sealed interface AuthScheme {
    // Authorization: Bearer <key>  (OpenAI, DeepSeek, Grok, Kimi, OpenRouter, Gemini-Shim)
    data object Bearer : AuthScheme

    // x-api-key: <key> (+ zusätzliche statische Header, z.B. anthropic-version) — Anthropic
    data class ApiKeyHeader(
        val headerName: String,
        val extraHeaders: Map<String, String> = emptyMap(),
    ) : AuthScheme
}

// Statische Beschreibung eines Providers. Neue OpenAI-kompatible Provider sind
// reine Config-Einträge; nur Anthropic braucht einen eigenen Adapter.
data class ProviderConfig(
    val id: String,
    val displayName: String,
    val baseUrl: String,
    val authScheme: AuthScheme,
    val capabilities: Set<Capability>,
    val defaultModel: String,
    val chatPath: String = "/v1/chat/completions",
    val transcribePath: String? = null,
    val requiresMaxTokens: Boolean = false,
    val systemPlacement: SystemPlacement = SystemPlacement.MESSAGE_ROLE,
    // Cert-Pins (SHA-256 der SubjectPublicKeyInfo, Format "sha256/…") für den Host.
    val certPins: List<String> = emptyList(),
) {
    fun supports(capability: Capability): Boolean = capability in capabilities
}
