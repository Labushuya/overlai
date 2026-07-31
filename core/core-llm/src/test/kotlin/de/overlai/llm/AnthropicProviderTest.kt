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

// CHANGE-MARKER v0.1.0: Anthropic-Adapter (siehe CHANGELOG.md)
// Beweist, dass der Anthropic-Adapter die Abstraktion trägt: neutrale ChatRequest
// rein, ChatDelta-Flow raus — gegen einen gefälschten Anthropic-SSE-Stream.
class AnthropicProviderTest {
    private lateinit var server: MockWebServer
    private lateinit var factory: ProviderFactory

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        factory = ProviderFactory(OkHttpClient.Builder().build(), ProviderFactory.defaultJson())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun mockConfig() = ProviderRegistry.ANTHROPIC.copy(baseUrl = server.url("/").toString().trimEnd('/'))

    @Test
    fun `streams text deltas from anthropic sse events`() =
        runTest {
            val sse =
                buildString {
                    append("""data: {"type":"message_start","message":{}}""").append("\n\n")
                    append("""data: {"type":"content_block_start","index":0}""").append("\n\n")
                    append("""data: {"type":"content_block_delta","delta":{"type":"text_delta","text":"Hal"}}""")
                        .append("\n\n")
                    append("""data: {"type":"content_block_delta","delta":{"type":"text_delta","text":"lo"}}""")
                        .append("\n\n")
                    append("""data: {"type":"message_delta","delta":{"stop_reason":"end_turn"}}""").append("\n\n")
                    append("""data: {"type":"message_stop"}""").append("\n\n")
                }
            server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody(sse))

            val provider = factory.create(mockConfig())
            val deltas =
                provider
                    .chat(
                        ChatRequest(model = "claude-opus-5", messages = listOf(ChatMessage(Role.USER, "Sag Hallo"))),
                        apiKey = "sk-ant-test",
                    ).toList()

            val text = deltas.filter { !it.done }.joinToString("") { it.text }
            assertThat(text).isEqualTo("Hallo")
            assertThat(deltas.last().done).isTrue()
        }

    @Test
    fun `sends x-api-key and anthropic-version headers, system hoisted, max_tokens present`() =
        runTest {
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "text/event-stream")
                    .setBody("""data: {"type":"message_stop"}""" + "\n\n"),
            )

            val provider = factory.create(mockConfig())
            provider
                .chat(
                    ChatRequest(
                        model = "claude-opus-5",
                        system = "Du bist knapp.",
                        messages = listOf(ChatMessage(Role.USER, "hi")),
                        maxTokens = 512,
                    ),
                    apiKey = "sk-ant-secret",
                ).toList()

            val recorded = server.takeRequest()
            assertThat(recorded.path).isEqualTo("/v1/messages")
            assertThat(recorded.getHeader("x-api-key")).isEqualTo("sk-ant-secret")
            assertThat(recorded.getHeader("anthropic-version")).isEqualTo("2023-06-01")
            val body = recorded.body.readUtf8()
            assertThat(body).contains("\"system\":\"Du bist knapp.\"")
            assertThat(body).contains("\"max_tokens\":512")
            // temperature/top_p dürfen NICHT im Body sein (400 auf neuen Modellen).
            assertThat(body).doesNotContain("temperature")
        }

    @Test
    fun `401 maps to Unauthorized`() =
        runTest {
            server.enqueue(
                MockResponse()
                    .setResponseCode(401)
                    .setBody("""{"error":{"type":"authentication_error","message":"bad key"}}"""),
            )
            val provider = factory.create(mockConfig())
            var caught: Throwable? = null
            try {
                provider
                    .chat(
                        ChatRequest(model = "claude-opus-5", messages = listOf(ChatMessage(Role.USER, "hi"))),
                        apiKey = "sk-ant-bad",
                    ).toList()
            } catch (e: Throwable) {
                caught = e
            }
            assertThat(caught).isInstanceOf(LlmError.Unauthorized::class.java)
        }
}
