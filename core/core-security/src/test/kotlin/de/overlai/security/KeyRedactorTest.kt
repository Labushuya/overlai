package de.overlai.security

import com.google.common.truth.Truth.assertThat
import org.junit.Test

// CHANGE-MARKER v0.1.0: BYOK-Key-Storage (siehe CHANGELOG.md)
// Kritischer Sicherheitstest (Threat T1): API-Keys dürfen nie im Klartext im Log
// landen. Reiner JVM-Test.
class KeyRedactorTest {
    @Test
    fun `redacts sk-style key but keeps prefix`() {
        val log = "Calling OpenAI with key sk-proj-ABCDEF1234567890abcdefXYZ done"
        val out = KeyRedactor.redact(log)
        assertThat(out).doesNotContain("ABCDEF1234567890")
        assertThat(out).contains("REDACTED")
    }

    @Test
    fun `redacts bearer token`() {
        val out = KeyRedactor.redact("Authorization: Bearer abcdef1234567890TOKEN")
        assertThat(out).doesNotContain("abcdef1234567890TOKEN")
        assertThat(out).contains("Bearer")
        assertThat(out).contains("REDACTED")
    }

    @Test
    fun `leaves normal text untouched`() {
        val text = "Just a normal log line with no secrets."
        assertThat(KeyRedactor.redact(text)).isEqualTo(text)
    }

    @Test
    fun `mask shows only short prefix`() {
        val masked = KeyRedactor.mask("sk-verysecretkeyvalue")
        assertThat(masked).startsWith("sk-")
        assertThat(masked).doesNotContain("verysecretkeyvalue")
    }
}
