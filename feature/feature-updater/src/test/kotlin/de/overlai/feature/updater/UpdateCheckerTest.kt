package de.overlai.feature.updater

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

// CHANGE-MARKER v0.1.0: In-App-Updater (siehe CHANGELOG.md)
// Testet den UpdateChecker gegen ein gefälschtes latest.json (MockWebServer):
// Update-verfügbar, up-to-date, Downgrade-Ablehnung, Fehlerfälle.
class UpdateCheckerTest {
    private lateinit var server: MockWebServer
    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun checker() =
        UpdateChecker(
            client = OkHttpClient(),
            json = json,
            latestJsonUrl = server.url("/latest.json").toString(),
        )

    private fun manifestJson(version: String) =
        """
        {"versionName":"$version","versionCode":10203,"minSdk":26,
         "apkUrl":"https://example/overlai-v$version.apk",
         "sha256":"abc","sizeBytes":123,"mandatory":false}
        """.trimIndent()

    @Test
    fun `reports update when remote newer`() {
        server.enqueue(MockResponse().setBody(manifestJson("1.5.0")))
        val result = checker().check(currentVersion = "1.4.0")
        assertThat(result).isInstanceOf(UpdateChecker.Result.UpdateAvailable::class.java)
        assertThat((result as UpdateChecker.Result.UpdateAvailable).manifest.versionName).isEqualTo("1.5.0")
    }

    @Test
    fun `reports up to date when equal`() {
        server.enqueue(MockResponse().setBody(manifestJson("1.4.0")))
        assertThat(checker().check("1.4.0")).isEqualTo(UpdateChecker.Result.UpToDate)
    }

    @Test
    fun `rejects downgrade`() {
        server.enqueue(MockResponse().setBody(manifestJson("1.0.0")))
        assertThat(checker().check("2.0.0")).isEqualTo(UpdateChecker.Result.UpToDate)
    }

    @Test
    fun `http error yields Error`() {
        server.enqueue(MockResponse().setResponseCode(404))
        assertThat(checker().check("1.0.0")).isInstanceOf(UpdateChecker.Result.Error::class.java)
    }

    @Test
    fun `malformed json yields Error`() {
        server.enqueue(MockResponse().setBody("{ not json"))
        assertThat(checker().check("1.0.0")).isInstanceOf(UpdateChecker.Result.Error::class.java)
    }
}
