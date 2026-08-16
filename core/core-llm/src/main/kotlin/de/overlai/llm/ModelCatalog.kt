package de.overlai.llm

// CHANGE-MARKER v0.4.0: Modell-Katalog (siehe CHANGELOG.md)
// Ein auswählbares Modell eines Providers.
data class ModelInfo(
    // exakter model-String für ChatRequest.model
    val id: String,
    // menschenlesbar; oft == id
    val displayName: String,
    // NUR wo verlässlich (OpenRouter); sonst immer false
    val free: Boolean = false,
    // Kontextfenster, wenn bekannt
    val context: Int? = null,
)

// CHANGE-MARKER: Kontingente/Guthaben (P2.5 E3, siehe CHANGELOG.md)
// Guthaben-/Verbrauchsstand eines Providers, sofern dessen API ihn hergibt (v.a. OpenRouter).
// Beträge in der Abrechnungswährung des Providers (i.d.R. USD). Felder null = unbekannt.
// KEIN Erfinden: wo ein Provider keine Guthaben-API bietet, wird gar kein CreditInfo geliefert.
data class CreditInfo(
    // Insgesamt aufgeladenes/verfügbares Guthaben (OpenRouter: total_credits).
    val totalCredits: Double? = null,
    // Bisher verbraucht (OpenRouter: total_usage).
    val totalUsage: Double? = null,
) {
    // Verbleibend, wenn beide Werte bekannt sind (nie negativ dargestellt).
    val remaining: Double?
        get() = if (totalCredits != null && totalUsage != null) (totalCredits - totalUsage).coerceAtLeast(0.0) else null
}

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
