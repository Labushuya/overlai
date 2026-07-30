package de.overlai.feature.updater

import com.google.common.truth.Truth.assertThat
import org.junit.Test

// CHANGE-MARKER v0.1.0: In-App-Updater (siehe CHANGELOG.md)
class SemVerTest {
    @Test
    fun `parses plain and v-prefixed`() {
        assertThat(SemVer.parse("1.2.3")).isEqualTo(SemVer(1, 2, 3))
        assertThat(SemVer.parse("v2.0.1")).isEqualTo(SemVer(2, 0, 1))
    }

    @Test
    fun `strips prerelease suffix`() {
        assertThat(SemVer.parse("1.4.0-beta2")).isEqualTo(SemVer(1, 4, 0))
    }

    @Test
    fun `rejects garbage`() {
        assertThat(SemVer.parse("abc")).isNull()
        assertThat(SemVer.parse("1.x.0")).isNull()
    }

    @Test
    fun `ordering is correct`() {
        assertThat(SemVer(1, 2, 3) < SemVer(1, 3, 0)).isTrue()
        assertThat(SemVer(2, 0, 0) > SemVer(1, 9, 9)).isTrue()
    }

    @Test
    fun `update available only when strictly newer`() {
        assertThat(isUpdateAvailable(current = "1.0.0", remote = "1.0.1")).isTrue()
        assertThat(isUpdateAvailable(current = "1.0.0", remote = "1.0.0")).isFalse()
        // Downgrade wird abgelehnt (T4).
        assertThat(isUpdateAvailable(current = "2.0.0", remote = "1.9.9")).isFalse()
    }
}
