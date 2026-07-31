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
            // :free -> free
            assertThat(byId["deepseek/deepseek-chat-v3:free"]?.free).isTrue()
            // numerisch 0.00000000 -> free
            assertThat(byId["some/zero-priced"]?.free).isTrue()
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
    fun `static-fallback provider returns defaultModel without http`() =
        runTest {
            // grok hat keinen bestätigten Endpoint -> StaticModels, kein Server-Call.
            val models = catalog.list(ProviderRegistry.GROK, "sk").map { it.id }
            assertThat(models).contains(ProviderRegistry.GROK.defaultModel)
        }
}
