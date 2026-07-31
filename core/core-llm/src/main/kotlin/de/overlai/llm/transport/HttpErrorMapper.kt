package de.overlai.llm.transport

import de.overlai.llm.LlmError
import kotlinx.serialization.json.Json
import okhttp3.Response

// CHANGE-MARKER v0.4.0: geteiltes HTTP-Fehler-Mapping (siehe CHANGELOG.md)
// Aus OpenAiCompatTransport extrahiert, damit der Modell-Katalog (/models)
// denselben 401/403/429/quota-Pfad nutzt. Verhalten 1:1 unverändert.
internal object HttpErrorMapper {
    private const val ERROR_SNIPPET_LEN = 300
    private const val HTTP_UNAUTHORIZED = 401
    private const val HTTP_FORBIDDEN = 403
    private const val HTTP_TOO_MANY_REQUESTS = 429

    fun map(
        response: Response,
        json: Json,
    ): LlmError {
        val bodyText = runCatching { response.body?.string().orEmpty() }.getOrDefault("")
        val error =
            runCatching {
                json.decodeFromString(OpenAiErrorEnvelope.serializer(), bodyText).error
            }.getOrNull()
        val apiMsg = error?.message ?: bodyText.take(ERROR_SNIPPET_LEN)
        val code = error?.code ?: error?.type

        return when (response.code) {
            HTTP_UNAUTHORIZED, HTTP_FORBIDDEN -> LlmError.Unauthorized(apiMsg.ifBlank { "API-Key ungültig oder fehlt" })
            HTTP_TOO_MANY_REQUESTS ->
                // 429 ist NICHT immer Rate-Limit: OpenAI nutzt es auch für
                // erschöpftes Guthaben (insufficient_quota) — die Ursache ist Billing.
                if (code == "insufficient_quota" || apiMsg.contains("quota", ignoreCase = true)) {
                    LlmError.InsufficientQuota(
                        "Kein Guthaben/Kontingent bei diesem Key. Prüfe Billing/Credits beim Provider. " +
                            "(Provider-Meldung: $apiMsg)",
                    )
                } else {
                    LlmError.RateLimited("Zu viele Anfragen — kurz warten. (Provider: $apiMsg)")
                }
            else -> LlmError.Api(response.code, apiMsg)
        }
    }
}
