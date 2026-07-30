package de.overlai.feature.updater

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

// CHANGE-MARKER v0.1.0: In-App-Updater (siehe CHANGELOG.md)
class Sha256VerifierTest {
    @Test
    fun `verifies matching hash (case-insensitive)`() {
        val f = File.createTempFile("apk", ".bin").apply { writeBytes("hello".toByteArray()) }
        // sha256("hello")
        val expected = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"
        assertThat(Sha256Verifier.verify(f, expected)).isTrue()
        assertThat(Sha256Verifier.verify(f, expected.uppercase())).isTrue()
        f.delete()
    }

    @Test
    fun `rejects tampered file`() {
        val f = File.createTempFile("apk", ".bin").apply { writeBytes("tampered".toByteArray()) }
        val wrong = "0000000000000000000000000000000000000000000000000000000000000000"
        assertThat(Sha256Verifier.verify(f, wrong)).isFalse()
        f.delete()
    }
}
