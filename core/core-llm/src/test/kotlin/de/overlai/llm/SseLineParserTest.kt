package de.overlai.llm

import com.google.common.truth.Truth.assertThat
import de.overlai.llm.transport.SseLineParser
import org.junit.Test

// CHANGE-MARKER v0.1.0: Provider-Abstraktion (siehe CHANGELOG.md)
class SseLineParserTest {
    @Test
    fun `data line yields Data event with json payload`() {
        val event = SseLineParser.parseLine("""data: {"choices":[]}""")
        assertThat(event).isInstanceOf(SseLineParser.Event.Data::class.java)
        assertThat((event as SseLineParser.Event.Data).json).isEqualTo("""{"choices":[]}""")
    }

    @Test
    fun `DONE sentinel yields Done event`() {
        assertThat(SseLineParser.parseLine("data: [DONE]")).isEqualTo(SseLineParser.Event.Done)
    }

    @Test
    fun `blank and comment lines are ignored`() {
        assertThat(SseLineParser.parseLine("")).isEqualTo(SseLineParser.Event.Ignore)
        assertThat(SseLineParser.parseLine(": keep-alive")).isEqualTo(SseLineParser.Event.Ignore)
        assertThat(SseLineParser.parseLine("event: message")).isEqualTo(SseLineParser.Event.Ignore)
    }

    @Test
    fun `empty data payload is ignored`() {
        assertThat(SseLineParser.parseLine("data: ")).isEqualTo(SseLineParser.Event.Ignore)
    }
}
