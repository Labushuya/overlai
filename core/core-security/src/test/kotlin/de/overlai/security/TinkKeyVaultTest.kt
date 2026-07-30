package de.overlai.security

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// CHANGE-MARKER v0.1.0: BYOK-Key-Storage (siehe CHANGELOG.md)
// Round-trip-Test des Keystore-basierten KeyVault via Robolectric (Tink nutzt
// unter Robolectric einen In-Memory-Master-Key). Beweist put/get/has/remove/clear.
@RunWith(RobolectricTestRunner::class)
class TinkKeyVaultTest {
    private fun vault() = TinkKeyVault(ApplicationProvider.getApplicationContext())

    @Test
    fun `put then get returns same key`() =
        runTest {
            val v = vault()
            v.putKey("openai", "sk-secret-123")
            assertThat(v.getKey("openai")).isEqualTo("sk-secret-123")
        }

    @Test
    fun `hasKey reflects presence`() =
        runTest {
            val v = vault()
            assertThat(v.hasKey("anthropic")).isFalse()
            v.putKey("anthropic", "key")
            assertThat(v.hasKey("anthropic")).isTrue()
        }

    @Test
    fun `removeKey deletes only that provider`() =
        runTest {
            val v = vault()
            v.putKey("openai", "a")
            v.putKey("grok", "b")
            v.removeKey("openai")
            assertThat(v.getKey("openai")).isNull()
            assertThat(v.getKey("grok")).isEqualTo("b")
        }

    @Test
    fun `clear removes all`() =
        runTest {
            val v = vault()
            v.putKey("openai", "a")
            v.putKey("grok", "b")
            v.clear()
            assertThat(v.hasKey("openai")).isFalse()
            assertThat(v.hasKey("grok")).isFalse()
        }
}
