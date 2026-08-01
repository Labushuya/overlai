package de.overlai.llm

import com.google.common.truth.Truth.assertThat
import de.overlai.llm.providers.ProviderRegistry
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

// CHANGE-MARKER v0.1.0: Provider-Abstraktion (siehe CHANGELOG.md)
// End-to-End-Test der Chat-Kette gegen einen gefälschten OpenAI-SSE-Stream.
// Beweist: Request-Serialisierung, SSE-Parsing, ChatDelta-Flow, Fehler-Mapping —
// alles ohne Emulator/echten Provider.
class OpenAiCompatProviderTest {
    private lateinit var server: MockWebServer
    private lateinit var factory: ProviderFactory

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        factory =
            ProviderFactory(
                client = OkHttpClient.Builder().build(),
                json = ProviderFactory.defaultJson(),
            )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    // Config, die auf den MockWebServer statt api.openai.com zeigt.
    private fun mockConfig() = ProviderRegistry.OPENAI.copy(baseUrl = server.url("/").toString().trimEnd('/'))

    @Test
    fun `streams deltas and terminates on DONE`() =
        runTest {
            val sse =
                buildString {
                    append("""data: {"choices":[{"delta":{"content":"Hal"}}]}""").append("\n\n")
                    append("""data: {"choices":[{"delta":{"content":"lo"}}]}""").append("\n\n")
                    append("""data: {"choices":[{"delta":{"content":"!"},"finish_reason":"stop"}]}""").append("\n\n")
                    append("data: [DONE]").append("\n\n")
                }
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "text/event-stream")
                    .setBody(sse),
            )

            val provider = factory.create(mockConfig())
            val request =
                ChatRequest(
                    model = "gpt-4o",
                    messages = listOf(ChatMessage(Role.USER, "Sag Hallo")),
                )

            val deltas = provider.chat(request, apiKey = "sk-test").toList()

            val text = deltas.filter { !it.done }.joinToString("") { it.text }
            assertThat(text).isEqualTo("Hallo!")
            assertThat(deltas.last().done).isTrue()
        }

    @Test
    fun `sends bearer auth and correct path`() =
        runTest {
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "text/event-stream")
                    .setBody("""data: {"choices":[{"delta":{"content":"hi"}}]}""" + "\n\n" + "data: [DONE]\n\n"),
            )

            val provider = factory.create(mockConfig())
            provider
                .chat(
                    ChatRequest(model = "gpt-4o", messages = listOf(ChatMessage(Role.USER, "hi"))),
                    apiKey = "sk-secret",
                ).toList()

            val recorded = server.takeRequest()
            assertThat(recorded.path).isEqualTo("/v1/chat/completions")
            assertThat(recorded.getHeader("Authorization")).isEqualTo("Bearer sk-secret")
            val body = recorded.body.readUtf8()
            assertThat(body).contains("\"model\":\"gpt-4o\"")
            // Root-Cause-Regression: stream:true MUSS im Body sein, sonst antwortet
            // der Provider non-streaming -> leerer SSE-Reader -> falscher 204.
            assertThat(body).contains("\"stream\":true")
        }

    @Test
    fun `401 maps to Unauthorized`() =
        runTest {
            server.enqueue(
                MockResponse()
                    .setResponseCode(401)
                    .setBody("""{"error":{"message":"bad key"}}"""),
            )

            val provider = factory.create(mockConfig())
            var caught: Throwable? = null
            try {
                provider
                    .chat(
                        ChatRequest(model = "gpt-4o", messages = listOf(ChatMessage(Role.USER, "hi"))),
                        apiKey = "sk-bad",
                    ).toList()
            } catch (e: Throwable) {
                caught = e
            }
            assertThat(caught).isInstanceOf(LlmError.Unauthorized::class.java)
        }

    @Test
    fun `429 maps to RateLimited`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(429).setBody("slow down"))

            val provider = factory.create(mockConfig())
            var caught: Throwable? = null
            try {
                provider
                    .chat(
                        ChatRequest(model = "gpt-4o", messages = listOf(ChatMessage(Role.USER, "hi"))),
                        apiKey = "sk",
                    ).toList()
            } catch (e: Throwable) {
                caught = e
            }
            assertThat(caught).isInstanceOf(LlmError.RateLimited::class.java)
        }

    @Test
    fun `429 with insufficient_quota maps to InsufficientQuota (not RateLimited)`() =
        runTest {
            // OpenAI schickt bei erschöpftem Guthaben 429 mit code=insufficient_quota.
            server.enqueue(
                MockResponse()
                    .setResponseCode(429)
                    .setBody(
                        """{"error":{"message":"You exceeded your current quota","type":"insufficient_quota",""" +
                            """"code":"insufficient_quota"}}""",
                    ),
            )

            val provider = factory.create(mockConfig())
            var caught: Throwable? = null
            try {
                provider
                    .chat(
                        ChatRequest(model = "gpt-4o", messages = listOf(ChatMessage(Role.USER, "hi"))),
                        apiKey = "sk",
                    ).toList()
            } catch (e: Throwable) {
                caught = e
            }
            assertThat(caught).isInstanceOf(LlmError.InsufficientQuota::class.java)
        }

    @Test
    fun `vision request serializes image as data url part`() =
        runTest {
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "text/event-stream")
                    .setBody("""data: {"choices":[{"delta":{"content":"ok"}}]}""" + "\n\n" + "data: [DONE]\n\n"),
            )

            val provider = factory.create(mockConfig())
            val img = ImageRef(bytes = byteArrayOf(1, 2, 3), mimeType = "image/png")
            provider
                .chat(
                    ChatRequest(
                        model = "gpt-4o",
                        messages = listOf(ChatMessage(Role.USER, "Was ist das?", images = listOf(img))),
                    ),
                    apiKey = "sk",
                ).toList()

            val body = server.takeRequest().body.readUtf8()
            assertThat(body).contains("\"type\":\"image_url\"")
            assertThat(body).contains("data:image/png;base64,")
        }

    @Test
    fun `in-stream error object is surfaced, not swallowed (empty bubble bug)`() =
        runTest {
            // OpenRouter-Muster: HTTP 200, aber Fehler als data-Zeile im Stream —
            // MIT NUMERISCHEM code (429), wie OpenRouter/OpenAI es real schicken.
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "text/event-stream")
                    .setBody("""data: {"error":{"message":"rate-limited upstream","code":429}}""" + "\n\n"),
            )
            val provider = factory.create(mockConfig())
            var caught: Throwable? = null
            try {
                provider
                    .chat(ChatRequest(model = "gpt-4o", messages = listOf(ChatMessage(Role.USER, "hi"))), "sk")
                    .toList()
            } catch (e: Throwable) {
                caught = e
            }
            // Numerischer code darf die Deser NICHT kippen -> muss als RateLimited ankommen,
            // NICHT als generische 204-Leermeldung (das war der :free-Bug).
            assertThat(caught).isInstanceOf(LlmError.RateLimited::class.java)
        }

    @Test
    fun `stream with zero content deltas fails with neutral message (no free bias)`() =
        runTest {
            // Nur ein role-Delta ohne content, dann DONE -> keine leere Bubble, aber
            // NEUTRALE Meldung (kein "kostenlos"-Bias, trifft auch bezahlte Provider).
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "text/event-stream")
                    .setBody("""data: {"choices":[{"delta":{}}]}""" + "\n\n" + "data: [DONE]\n\n"),
            )
            val provider = factory.create(mockConfig())
            var caught: Throwable? = null
            try {
                provider
                    .chat(ChatRequest(model = "gpt-4o", messages = listOf(ChatMessage(Role.USER, "hi"))), "sk")
                    .toList()
            } catch (e: Throwable) {
                caught = e
            }
            assertThat(caught).isInstanceOf(LlmError.Api::class.java)
            val msg = (caught as LlmError.Api).message
            assertThat(msg).doesNotContain("kostenlos")
            assertThat(msg).contains("gpt-4o")
        }

    @Test
    fun `reasoning_content is surfaced as text (Kimi k2-thinking, DeepSeek-R1)`() =
        runTest {
            // Reasoning-Modelle streamen den Text in reasoning_content statt content.
            // Vor dem Fix -> Stream "leer" -> falscher 204. Jetzt: sichtbarer Text.
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "text/event-stream")
                    .setBody(
                        """data: {"choices":[{"delta":{"reasoning_content":"denke... "}}]}""" + "\n\n" +
                            """data: {"choices":[{"delta":{"content":"Hallo"}}]}""" + "\n\n" +
                            "data: [DONE]\n\n",
                    ),
            )
            val provider = factory.create(mockConfig())
            val deltas =
                provider
                    .chat(ChatRequest(model = "kimi-k2", messages = listOf(ChatMessage(Role.USER, "hi"))), "sk")
                    .toList()
            val text = deltas.filter { !it.done }.joinToString("") { it.text }
            assertThat(text).isEqualTo("denke... Hallo")
            assertThat(deltas.last().done).isTrue()
        }

    @Test
    fun `openrouter request carries HTTP-Referer and X-Title`() =
        runTest {
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "text/event-stream")
                    .setBody("""data: {"choices":[{"delta":{"content":"hi"}}]}""" + "\n\n" + "data: [DONE]\n\n"),
            )
            val config = ProviderRegistry.OPENROUTER.copy(baseUrl = server.url("/").toString().trimEnd('/'))
            factory
                .create(config)
                .chat(ChatRequest(model = "x", messages = listOf(ChatMessage(Role.USER, "hi"))), "sk-or")
                .toList()
            val rec = server.takeRequest()
            assertThat(rec.getHeader("HTTP-Referer")).isNotEmpty()
            assertThat(rec.getHeader("X-Title")).isEqualTo("OverlAI")
        }

    @Test
    fun `openrouter free-unavailable (numeric 404 in stream) is surfaced honestly`() =
        runTest {
            // Realer OpenRouter-:free-Fall: HTTP 200, im Stream {"error":{...,"code":404}}
            // mit "unavailable for free". Vor dem Fix kippte der numerische code die Deser
            // -> verschluckt -> generische 204. Jetzt: klare Api-Meldung, kein Verschlucken.
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "text/event-stream")
                    .setBody(
                        """data: {"error":{"message":"This model is unavailable for free.",""" +
                            """"code":404},"user_id":"u_1"}""" + "\n\n",
                    ),
            )
            val provider = factory.create(mockConfig())
            var caught: Throwable? = null
            try {
                provider
                    .chat(
                        ChatRequest(
                            model = "deepseek/deepseek-r1:free",
                            messages = listOf(ChatMessage(Role.USER, "hi")),
                        ),
                        "sk",
                    ).toList()
            } catch (e: Throwable) {
                caught = e
            }
            assertThat(caught).isInstanceOf(LlmError.Api::class.java)
            assertThat((caught as LlmError.Api).message).contains("nicht (mehr) verfügbar")
        }

    @Test
    fun `numeric error code deserializes without throwing`() {
        val json = ProviderFactory.defaultJson()
        // Vor dem Fix (code: String?) warf das eine SerializationException und der
        // ganze Chunk (inkl. error) ging verloren -> stiller Verlust -> 204.
        val chunk =
            json.decodeFromString(
                de.overlai.llm.transport.OpenAiStreamChunk.serializer(),
                """{"error":{"message":"rate-limited upstream","code":429}}""",
            )
        assertThat(chunk.error).isNotNull()
        assertThat(chunk.error?.message).isEqualTo("rate-limited upstream")
    }
}
