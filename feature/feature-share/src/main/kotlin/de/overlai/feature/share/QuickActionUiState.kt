package de.overlai.feature.share

// CHANGE-MARKER v0.1.0: Entry-Points (siehe CHANGELOG.md)
// UI-State der kurzlebigen Quick-Action-Surface (PROCESS_TEXT / Share).
data class QuickActionUiState(
    val sourceText: String = "",
    val resultText: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val hasApiKey: Boolean = true,
    // Bei PROCESS_TEXT: kann das Ergebnis in die Host-App zurückgeschrieben werden?
    val canReplaceInHost: Boolean = false,
)

// Die Schnellaktionen, die aus einer Selektion/Share heraus angeboten werden.
enum class QuickAction(
    val label: String,
    val promptPrefix: String,
) {
    TRANSLATE("Übersetzen", "Übersetze den folgenden Text ins Deutsche (nur die Übersetzung):\n\n"),
    SUMMARIZE("Zusammenfassen", "Fasse den folgenden Text in 2-3 Sätzen zusammen:\n\n"),
    EXPLAIN("Erklären", "Erkläre kurz und verständlich:\n\n"),
    ASK("Fragen", ""),
    ;

    fun buildPrompt(text: String): String = promptPrefix + text
}
