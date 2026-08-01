package de.overlai.llm

import com.google.common.truth.Truth.assertThat
import de.overlai.llm.providers.ProviderRegistry
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

// CHANGE-MARKER v0.4.0: Modell-Katalog (siehe CHANGELOG.md)
// Testet Parsing + fail-closed Chat-Filter + free-Erkennung gegen gefälschte
// /models-Antworten (MockWebServer), ohne Emulator/echten Provider.
class ModelCatalogTest {
    private lateinit var server: MockWebServer
    private lateinit var catalog: HttpModelCatalog

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        catalog = HttpModelCatalog(OkHttpClient.Builder().build(), ProviderFactory.defaultJson())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun cfg(base: ProviderConfig) = base.copy(baseUrl = server.url("/").toString().trimEnd('/'))

    @Test
    fun `openai excludes non-chat models (fail-closed)`() =
        runTest {
            server.enqueue(
                MockResponse().setBody(
                    """{"data":[{"id":"gpt-4o"},{"id":"whisper-1"},
                       {"id":"text-embedding-3-small"},{"id":"dall-e-3"},{"id":"foo-bar-99"}]}""",
                ),
            )
            val models = catalog.list(cfg(ProviderRegistry.OPENAI), "sk-test").map { it.id }
            assertThat(models).contains("gpt-4o")
            assertThat(models).containsNoneOf("whisper-1", "text-embedding-3-small", "dall-e-3", "foo-bar-99")
        }

    @Test
    fun `openai models request uses bearer + v1 models path`() =
        runTest {
            server.enqueue(MockResponse().setBody("""{"data":[{"id":"gpt-4o"}]}"""))
            catalog.list(cfg(ProviderRegistry.OPENAI), "sk-secret")
            val rec = server.takeRequest()
            assertThat(rec.path).isEqualTo("/v1/models")
            assertThat(rec.getHeader("Authorization")).isEqualTo("Bearer sk-secret")
        }

    @Test
    fun `openrouter detects free models and text modality`() =
        runTest {
            server.enqueue(
                MockResponse().setBody(
                    """{"data":[
                      {"id":"deepseek/deepseek-chat-v3:free","name":"DeepSeek v3 (free)",
                       "context_length":64000,"architecture":{"output_modalities":["text"]},
                       "pricing":{"prompt":"0","completion":"0"}},
                      {"id":"openai/gpt-4o","name":"GPT-4o",
                       "architecture":{"output_modalities":["text"]},
                       "pricing":{"prompt":"0.0000025","completion":"0.00001"}},
                      {"id":"some/zero-priced","name":"Zero",
                       "architecture":{"output_modalities":["text"]},
                       "pricing":{"prompt":"0.00000000","completion":"0.00000000"}},
                      {"id":"deepseek/deepseek-r1:free","name":"DeepSeek R1 (free-Slug, aber paid)",
                       "architecture":{"output_modalities":["text"]},
                       "pricing":{"prompt":"0.0000004","completion":"0.0000008"}},
                      {"id":"an/image-model","name":"Img",
                       "architecture":{"output_modalities":["image"]},
                       "pricing":{"prompt":"0","completion":"0"}}
                    ]}""",
                ),
            )
            val models = catalog.list(cfg(ProviderRegistry.OPENROUTER), "sk-or")
            val byId = models.associateBy { it.id }
            // Bild-Modell (nur image-Output) ausgeschlossen.
            assertThat(byId).doesNotContainKey("an/image-model")
            // :free-Slug MIT Preis 0 -> free
            assertThat(byId["deepseek/deepseek-chat-v3:free"]?.free).isTrue()
            // numerisch 0.00000000 -> free
            assertThat(byId["some/zero-priced"]?.free).isTrue()
            // :free-Slug, aber realer Preis > 0 (OpenRouter-Abschaltung) -> NICHT free
            assertThat(byId["deepseek/deepseek-r1:free"]?.free).isFalse()
            // bezahlt -> nicht free
            assertThat(byId["openai/gpt-4o"]?.free).isFalse()
            // context durchgereicht
            assertThat(byId["deepseek/deepseek-chat-v3:free"]?.context).isEqualTo(64000)
        }

    @Test
    fun `openrouter path is v1 models not doubled api`() =
        runTest {
            server.enqueue(MockResponse().setBody("""{"data":[]}"""))
            catalog.list(cfg(ProviderRegistry.OPENROUTER), "sk-or")
            assertThat(server.takeRequest().path).isEqualTo("/v1/models")
        }

    @Test
    fun `anthropic parses display_name and sends key headers`() =
        runTest {
            server.enqueue(
                MockResponse().setBody(
                    """{"data":[{"id":"claude-opus-5","display_name":"Claude Opus 5"}],"has_more":false}""",
                ),
            )
            val models = catalog.list(cfg(ProviderRegistry.ANTHROPIC), "sk-ant")
            assertThat(models.first { it.id == "claude-opus-5" }.displayName).isEqualTo("Claude Opus 5")
            val rec = server.takeRequest()
            assertThat(rec.getHeader("x-api-key")).isEqualTo("sk-ant")
            assertThat(rec.getHeader("anthropic-version")).isEqualTo("2023-06-01")
        }

    @Test
    fun `tolerates a broken row without losing the rest`() =
        runTest {
            server.enqueue(MockResponse().setBody("""{"data":[{"id":"gpt-4o"},{"name":"no-id-here"}]}"""))
            val models = catalog.list(cfg(ProviderRegistry.OPENAI), "sk").map { it.id }
            assertThat(models).contains("gpt-4o")
        }

    @Test
    fun `error falls back to non-empty list containing defaultModel`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"message":"bad key"}}"""))
            val models = catalog.list(cfg(ProviderRegistry.OPENAI), "sk-bad").map { it.id }
            assertThat(models).isNotEmpty()
            assertThat(models).contains(ProviderRegistry.OPENAI.defaultModel)
        }

    @Test
    fun `listOrThrow surfaces Unauthorized on 401`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"message":"bad"}}"""))
            var caught: Throwable? = null
            try {
                catalog.listOrThrow(cfg(ProviderRegistry.OPENAI), "sk-bad")
            } catch (e: Throwable) {
                caught = e
            }
            assertThat(caught).isInstanceOf(LlmError.Unauthorized::class.java)
        }

    @Test
    fun `kimi loads live models and filters non-chat`() =
        runTest {
            server.enqueue(
                MockResponse().setBody(
                    """{"data":[{"id":"kimi-k2.6"},{"id":"moonshot-v1-128k"},
                       {"id":"moonshot-v1-8k-vision-preview"},{"id":"some-embedding"}]}""",
                ),
            )
            val models = catalog.list(cfg(ProviderRegistry.KIMI), "sk-kimi").map { it.id }
            assertThat(server.takeRequest().path).isEqualTo("/v1/models")
            assertThat(models).containsAtLeast("kimi-k2.6", "moonshot-v1-128k")
            // vision-preview + embedding -> raus (Deny-Netz).
            assertThat(models).containsNoneOf("moonshot-v1-8k-vision-preview", "some-embedding")
        }

    @Test
    fun `grok loads live models with grok allow-prefix`() =
        runTest {
            server.enqueue(
                MockResponse().setBody("""{"data":[{"id":"grok-4.5"},{"id":"grok-4.3"},{"id":"random-thing"}]}"""),
            )
            val models = catalog.list(cfg(ProviderRegistry.GROK), "sk-grok").map { it.id }
            assertThat(server.takeRequest().path).isEqualTo("/v1/models")
            assertThat(models).containsAtLeast("grok-4.5", "grok-4.3")
            assertThat(models).doesNotContain("random-thing")
        }

    @Test
    fun `gemini strips models prefix and uses shim path`() =
        runTest {
            server.enqueue(
                MockResponse().setBody(
                    """{"data":[{"id":"models/gemini-2.5-flash"},{"id":"models/gemini-2.5-pro"},
                       {"id":"models/gemini-embedding-001"}]}""",
                ),
            )
            val models = catalog.list(cfg(ProviderRegistry.GEMINI), "sk-gem").map { it.id }
            // baseUrl endet auf /v1beta/openai -> Pfad nur /models.
            assertThat(server.takeRequest().path).isEqualTo("/models")
            // "models/"-Präfix gestrippt.
            assertThat(models).containsAtLeast("gemini-2.5-flash", "gemini-2.5-pro")
            assertThat(models).doesNotContain("models/gemini-2.5-flash")
            // Embedding raus.
            assertThat(models).doesNotContain("gemini-embedding-001")
        }

    @Test
    fun `catalog error falls back to non-empty static list`() =
        runTest {
            // Netzfehler bei einem Live-Provider -> StaticModels (nie leer, defaultModel drin).
            server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))
            val models = catalog.list(cfg(ProviderRegistry.KIMI), "sk").map { it.id }
            assertThat(models).isNotEmpty()
            assertThat(models).contains(ProviderRegistry.KIMI.defaultModel)
        }
}
