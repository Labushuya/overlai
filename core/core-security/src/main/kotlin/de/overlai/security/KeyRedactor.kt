package de.overlai.security

// CHANGE-MARKER v0.1.0: BYOK-Key-Storage (siehe CHANGELOG.md)
// Redigiert API-Keys aus Log-Strings. Verwendung: jede Log-Ausgabe, die
// potenziell einen Key enthalten könnte, VORHER durch redact() schicken.
// Deckt die gängigen Key-Formen ab (sk-…, Bearer …, x-api-key-Werte).
object KeyRedactor {
    private const val KEEP_PREFIX = 4
    private const val MASK = "***REDACTED***"

    // Bekannte Key-Muster: OpenAI-artige sk-/xai-/etc., Bearer-Header, lange Tokens.
    private val patterns =
        listOf(
            // "sk-…", "sk-proj-…", "xai-…", "gsk_…" u.ä.: Präfix + >=16 Key-Zeichen.
            Regex("""\b([A-Za-z]{2,6}[-_])[A-Za-z0-9_-]{16,}"""),
            // "Bearer <token>"
            Regex("""(?i)(Bearer\s+)[A-Za-z0-9._-]{12,}"""),
        )

    fun redact(input: String): String {
        var out = input
        patterns.forEach { rx ->
            out =
                rx.replace(out) { m ->
                    val prefix = m.groupValues.getOrNull(1).orEmpty()
                    prefix + MASK
                }
        }
        return out
    }

    // Kürzt einen Key für eine bewusste, sichere Anzeige ("sk-…abcd" -> "sk-…").
    fun mask(key: String): String =
        if (key.length <= KEEP_PREFIX) {
            MASK
        } else {
            key.take(KEEP_PREFIX) + "…" + MASK
        }
}
