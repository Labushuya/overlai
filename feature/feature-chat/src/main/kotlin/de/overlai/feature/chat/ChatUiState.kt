package de.overlai.feature.chat

import de.overlai.conversation.ChatUiMessage
import de.overlai.llm.ModelInfo

// CHANGE-MARKER: Chat-Kern vereinheitlicht (P2.1a, siehe CHANGELOG.md)
// UI-State des Chat-Screens. `messages` nutzt jetzt das gemeinsame ChatUiMessage
// (core-conversation), das alle Oberflächen teilen — die frühere eigene UiMessage-
// Klasse (strukturgleich) ist entfallen. Streaming/Verlauf kommen aus der
// ConversationSession; hier bleiben nur die screen-spezifischen Felder.
data class ChatUiState(
    val messages: List<ChatUiMessage> = emptyList(),
    val input: String = "",
    val isStreaming: Boolean = false,
    val error: String? = null,
    val providerName: String = "",
    // P3: Modell der Session für den Anbieter/Modell-Chip im Header (null = Provider-Default).
    val modelId: String? = null,
    // E3b: aktueller Titel (für den Umbenennen-Dialog).
    val title: String = "",
    val hasApiKey: Boolean = true,
    // E3: Token-Usage + Kontextfenster. contextLimit null = unbekannt (nur Verbrauch zeigen).
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val contextLimit: Int? = null,
    // Generierter Handover-Text zur Kontroll-Vorschau (null = kein Dialog offen).
    val handoverPreview: String? = null,
    val handoverLoading: Boolean = false,
    // Einmaliger Auto-Vorschlag, wenn der Kontext ~voll ist (bis Nutzer ihn wegwischt/nutzt).
    val suggestHandover: Boolean = false,
    // E2: Modell-Wechsel-Sheet (null = zu; sonst offen, ggf. mit gewähltem Anbieter + Modellen).
    val modelSwitch: ModelSwitchState? = null,
    // E2: kurzer Kosten-/Kontexthinweis nach einem Modellwechsel (null = keiner).
    val costHint: String? = null,
) {
    // Anteil des Kontextfensters, das der aktuelle Prompt belegt (0..1); null wenn unbekannt.
    val contextFraction: Float?
        get() = contextLimit?.takeIf { it > 0 }?.let { promptTokens.toFloat() / it }
}

// E2: Zustand des Modell-Wechsel-Sheets. Erst Anbieter (nur mit Key wählbar), dann dessen
// lazy geladene Modelle. models null = lädt noch; leer + modelsError = Fehler/kein Key.
data class ModelSwitchState(
    val open: Boolean = false,
    val keyPresentFor: Set<String> = emptySet(),
    val selectedProviderId: String? = null,
    val models: List<ModelInfo>? = null,
    val modelsError: String? = null,
)
