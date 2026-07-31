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
                    .setBody("data: [DONE]\n\n"),
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
            assertThat(recorded.body.readUtf8()).contains("\"model\":\"gpt-4o\"")
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
                MockResponse().setHeader("Content-Type", "text/event-stream").setBody("data: [DONE]\n\n"),
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
}
