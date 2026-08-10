package de.overlai.core.data.chat

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import de.overlai.llm.Role
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// CHANGE-MARKER: Multi-Chat-Persistenz (P2.1b, siehe CHANGELOG.md)
// Verifiziert das Room-Schema + SessionRepository gegen eine in-memory-DB (Robolectric,
// weil Room einen Android-Context braucht). Deckt: anlegen, Nachrichten anhängen/laden,
// Provider/Modell pro Session, Löschen mit CASCADE.
@RunWith(RobolectricTestRunner::class)
class SessionRepositoryTest {
    private lateinit var db: OverlaiDatabase
    private lateinit var repo: SessionRepository

    @Before
    fun setUp() {
        db =
            Room.inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext(),
                OverlaiDatabase::class.java,
            ).allowMainThreadQueries().build()
        repo = SessionRepository(db.chatDao(), db.projectDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `createSession + appendMessage werden geladen und behalten Reihenfolge`() =
        runTest {
            repo.createSession("s1", "Erster Chat", "anthropic", "claude-sonnet-5", now = 1000)
            repo.appendMessage("s1", Role.USER, "Hallo", now = 1001)
            repo.appendMessage("s1", Role.ASSISTANT, "Hi!", now = 1002)

            val msgs = repo.messages("s1")
            assertThat(msgs).hasSize(2)
            assertThat(msgs[0].role).isEqualTo(Role.USER)
            assertThat(msgs[0].text).isEqualTo("Hallo")
            assertThat(msgs[1].role).isEqualTo(Role.ASSISTANT)

            val session = repo.getSession("s1")
            assertThat(session).isNotNull()
            assertThat(session!!.providerId).isEqualTo("anthropic")
            assertThat(session.modelId).isEqualTo("claude-sonnet-5")
        }

    @Test
    fun `mehrere Sessions sind unabhaengig, je eigener Provider`() =
        runTest {
            repo.createSession("a", "A", "openai", null, now = 1)
            repo.createSession("b", "B", "deepseek", "deepseek-chat", now = 2)
            repo.appendMessage("a", Role.USER, "in A", now = 3)
            repo.appendMessage("b", Role.USER, "in B", now = 4)

            assertThat(repo.messages("a").single().text).isEqualTo("in A")
            assertThat(repo.messages("b").single().text).isEqualTo("in B")
            assertThat(repo.getSession("a")!!.providerId).isEqualTo("openai")
            assertThat(repo.getSession("b")!!.providerId).isEqualTo("deepseek")
        }

    @Test
    fun `deleteSession entfernt Session und ihre Nachrichten (CASCADE)`() =
        runTest {
            repo.createSession("s", "S", "kimi", null, now = 1)
            repo.appendMessage("s", Role.USER, "x", now = 2)

            repo.deleteSession("s")

            assertThat(repo.getSession("s")).isNull()
            assertThat(repo.messages("s")).isEmpty()
        }

    @Test
    fun `observeSessions liefert Sessions nach updatedAt absteigend`() =
        runTest {
            repo.createSession("old", "Alt", "openai", null, now = 100)
            repo.createSession("new", "Neu", "openai", null, now = 200)

            val sessions = repo.observeSessions().first()
            assertThat(sessions.map { it.id }).containsExactly("new", "old").inOrder()
        }

    // --- Projekte/Gruppen (E2) ---

    @Test
    fun `Projekt anlegen, Chat verschieben und wieder herausloesen`() =
        runTest {
            repo.createProject("p1", "Arbeit", now = 1)
            repo.createSession("s1", "Chat", "openai", null, now = 2)

            repo.moveChatToProject("s1", "p1", now = 3)
            assertThat(repo.getSession("s1")!!.projectId).isEqualTo("p1")

            repo.moveChatToProject("s1", null, now = 4)
            assertThat(repo.getSession("s1")!!.projectId).isNull()
        }

    @Test
    fun `deleteProject laesst Chats erhalten und nullt projectId (SET NULL)`() =
        runTest {
            repo.createProject("p1", "Projekt", now = 1)
            repo.createSession("s1", "Chat", "openai", null, now = 2)
            repo.moveChatToProject("s1", "p1", now = 3)

            repo.deleteProject("p1")

            val session = repo.getSession("s1")
            assertThat(session).isNotNull()
            assertThat(session!!.projectId).isNull()
            assertThat(repo.observeProjects().first()).isEmpty()
        }

    @Test
    fun `renameProject aendert den Namen`() =
        runTest {
            repo.createProject("p1", "Alt", now = 1)
            repo.renameProject("p1", "Neu", now = 2)

            assertThat(repo.observeProjects().first().single().name).isEqualTo("Neu")
        }
}
