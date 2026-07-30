package de.overlai.llm.transport

// CHANGE-MARKER v0.1.0: Provider-Abstraktion (siehe CHANGELOG.md)
// Minimaler SSE-Zeilen-Parser für OpenAI-kompatible Streams. Reagiert auf
// "data: {json}"-Zeilen und das Sentinel "data: [DONE]". Bewusst zustandslos
// pro Zeile -> gut unit-testbar.
object SseLineParser {
    private const val DATA_PREFIX = "data:"
    const val DONE_SENTINEL = "[DONE]"

    // Ergebnis einer geparsten SSE-Zeile.
    sealed interface Event {
        data class Data(
        val json: String,
        ) : Event

        data object Done : Event

        // Kommentar/leer/anderes Feld -> ignorieren.
        data object Ignore : Event
    }

    fun parseLine(line: String): Event {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return Event.Ignore
        if (!trimmed.startsWith(DATA_PREFIX)) return Event.Ignore
        val payload = trimmed.removePrefix(DATA_PREFIX).trim()
        return when {
            payload == DONE_SENTINEL -> Event.Done
            payload.isEmpty() -> Event.Ignore
            else -> Event.Data(payload)
        }
    }
}
