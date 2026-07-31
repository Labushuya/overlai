package de.overlai.llm

// CHANGE-MARKER v0.4.0: Modell-Katalog (siehe CHANGELOG.md)
// Ein auswählbares Modell eines Providers.
data class ModelInfo(
    val id: String, // exakter model-String für ChatRequest.model
    val displayName: String, // menschenlesbar; oft == id
    val free: Boolean = false, // NUR wo verlässlich (OpenRouter); sonst immer false
    val context: Int? = null, // Kontextfenster, wenn bekannt
)

// Lädt den Modell-Katalog eines Providers. Die Standard-Implementierung
// (HttpModelCatalog) lädt live mit dem BYOK-Key und filtert auf chat-taugliche
// Modelle; wo ein Provider keinen (bestätigten) Endpoint hat, kommt eine
// kuratierte Fallback-Liste.
interface ModelCatalog {
    // Fängt Fehler intern und liefert immer eine nicht-leere Liste (mind. defaultModel).
    suspend fun list(
        config: ProviderConfig,
        apiKey: String,
    ): List<ModelInfo>
}
