package de.overlai.conversation

import com.google.common.truth.Truth.assertThat
import de.overlai.llm.ChatMessage
import de.overlai.llm.Role
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

// CHANGE-MARKER: Chat-Kern vereinheitlicht (P2.1a, siehe CHANGELOG.md)
// Erstes Test-Sicherheitsnetz für den Chat-Kern (bisher nur core-llm getestet).
// Deckt die zuvor 3-fach duplizierte Session-Logik zentral ab.
@OptIn(ExperimentalCoroutinesApi::class)
class ConversationSessionTest {
    // Fake-Streamer: gibt eine vorgegebene Event-Folge zurück, ohne echten Provider.
    private class FakeStreamer(
        private val events: List<ConversationEngine.Event>,
        private val hasKeyValue: Boolean = true,
    ) : ConversationSession.Streamer {
        var lastHistory: List<ChatMessage>? = null

        override fun stream(messages: List<ChatMessage>): Flow<ConversationEngine.Event> {
            lastHistory = messages
            return flow { events.forEach { emit(it) } }
        }

        override suspend fun providerDisplayName(): String = "TestProvider"

        override suspend fun hasKey(): Boolean = hasKeyValue
    }

    @Test
    fun `send akkumuliert Deltas in die letzte Assistant-Bubble und beendet streaming`() =
        runTest {
            val streamer =
                FakeStreamer(
                    listOf(
                        ConversationEngine.Event.Delta("Hal"),
                        ConversationEngine.Event.Delta("lo"),
                        ConversationEngine.Event.Done,
                    ),
                )
            val session = ConversationSession(streamer, this)

            session.send("Sag Hallo")
            advanceUntilIdle()

            val s = session.state.value
            assertThat(s.messages).hasSize(2)
            assertThat(s.messages[0].role).isEqualTo(Role.USER)
            assertThat(s.messages[0].text).isEqualTo("Sag Hallo")
            assertThat(s.messages[1].role).isEqualTo(Role.ASSISTANT)
            assertThat(s.messages[1].text).isEqualTo("Hallo")
            assertThat(s.messages[1].streaming).isFalse()
            assertThat(s.isStreaming).isFalse()
        }

    @Test
    fun `Verlauf an die Engine enthaelt die User-Nachricht, nicht die leere Assistant-Bubble`() =
        runTest {
            val streamer = FakeStreamer(listOf(ConversationEngine.Event.Done))
            val session = ConversationSession(streamer, this)

            session.send("Frage")
            advanceUntilIdle()

            // Die an stream() gereichte History: nur die abgeschlossene User-Nachricht.
            assertThat(streamer.lastHistory).isNotNull()
            assertThat(streamer.lastHistory!!).hasSize(1)
            assertThat(streamer.lastHistory!![0].role).isEqualTo(Role.USER)
            assertThat(streamer.lastHistory!![0].content).isEqualTo("Frage")
        }

    @Test
    fun `Failed-Event landet als Fehlertext in der Assistant-Bubble`() =
        runTest {
            val streamer = FakeStreamer(listOf(ConversationEngine.Event.Failed("Kein Guthaben")))
            val session = ConversationSession(streamer, this)

            session.send("hi")
            advanceUntilIdle()

            val s = session.state.value
            assertThat(s.messages.last().text).isEqualTo("Kein Guthaben")
            assertThat(s.messages.last().streaming).isFalse()
            assertThat(s.isStreaming).isFalse()
        }

    @Test
    fun `leere Eingabe ist ein No-Op`() =
        runTest {
            val session = ConversationSession(FakeStreamer(emptyList()), this)
            session.send("   ")
            assertThat(session.state.value.messages).isEmpty()
        }

    @Test
    fun `reset leert den Verlauf`() =
        runTest {
            val session = ConversationSession(FakeStreamer(listOf(ConversationEngine.Event.Done)), this)
            session.send("hi")
            advanceUntilIdle()
            assertThat(session.state.value.messages).isNotEmpty()

            session.reset()

            assertThat(session.state.value.messages).isEmpty()
            assertThat(session.state.value.isStreaming).isFalse()
        }

    @Test
    fun `showKeyHintIfMissing blendet Hinweis ein wenn kein Key`() =
        runTest {
            val session = ConversationSession(FakeStreamer(emptyList(), hasKeyValue = false), this)
            session.showKeyHintIfMissing()
            advanceUntilIdle()
            assertThat(session.state.value.messages).hasSize(1)
            assertThat(session.state.value.messages[0].text).contains("Kein API-Key")
            assertThat(session.state.value.messages[0].text).contains("TestProvider")
        }

    // P2.1c: Reaktivität — eine Änderung am persistierten Verlauf (Room-Flow) muss LIVE in den
    // State propagieren (ohne raus/rein). Fake-Persistenz mit steuerbarem observeHistory-Flow.
    @Test
    fun `observeHistory-Aenderung propagiert live in den State`() =
        runTest {
            val history = MutableStateFlow<List<ChatUiMessage>>(emptyList())
            val persistence =
                object : ConversationSession.Persistence {
                    override fun observeHistory(): Flow<List<ChatUiMessage>> = history

                    override suspend fun onUserMessage(text: String) = Unit

                    override suspend fun onAssistantMessage(text: String) = Unit
                }
            val session = ConversationSession(FakeStreamer(emptyList()), this, persistence)
            advanceUntilIdle()
            assertThat(session.state.value.messages).isEmpty()

            // Verlauf ändert sich in der DB → State folgt sofort (im Ruhezustand ist die DB Wahrheit).
            history.value = listOf(ChatUiMessage(Role.USER, "hallo"), ChatUiMessage(Role.ASSISTANT, "hi"))
            advanceUntilIdle()

            assertThat(session.state.value.messages).hasSize(2)
            assertThat(session.state.value.messages[0].text).isEqualTo("hallo")
            assertThat(session.state.value.messages[1].text).isEqualTo("hi")

            // Die endlose observeHistory-Collect-Coroutine beenden, sonst meckert runTest.
            coroutineContext.cancelChildren()
        }
}
