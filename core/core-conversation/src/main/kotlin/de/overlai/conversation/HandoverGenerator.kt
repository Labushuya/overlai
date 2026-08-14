package de.overlai.conversation

import de.overlai.core.data.chat.SessionRepository
import de.overlai.llm.ChatMessage
import de.overlai.llm.Role

// CHANGE-MARKER: Kontext-Handover (Phase 3 E3, siehe CHANGELOG.md)
// Erzeugt einen FAKTISCHEN Handover eines Chats (kein bloßes Summary wie /compact): fasst
// Ziel/Plan, Erledigtes, Offenes und v.a. Probleme/Fehler strukturiert zusammen, damit eine
// neue Session desselben Chats nahtlos weiterarbeiten kann. Läuft über den Provider/das Modell
// der Session (plattformübergreifend via ConversationEngine.complete). Zwei Schritte:
// generate() liefert den Text zur Kontroll-Vorschau, apply() startet die neue Session damit.
class HandoverGenerator(
    private val engine: ConversationEngine,
    private val repo: SessionRepository,
) {
    // System-Instruktion — bewusst faktisch/strukturiert statt verdichtend. Fehlerkontext
    // muss erhalten bleiben (der Hauptmangel von reinem Summarizing).
    private val instruction =
        """
        Du erstellst ein HANDOVER-Dokument für die nahtlose Fortsetzung dieses Chats in einer
        neuen Session. KEIN bloßes Summary, KEINE Verdichtung, die Fehlerkontext verliert.
        Gib es exakt in diesen Abschnitten (Markdown-Überschriften), faktisch und konkret:

        ## Ziel & Plan
        Worum geht es, was war die Absicht/der Plan.
        ## Erledigt
        Was wurde bereits gemacht/entschieden (mit relevanten Details).
        ## Offen
        Was steht noch aus, nächste konkrete Schritte.
        ## Probleme & Fehler
        Aufgetretene Fehler, Sackgassen, verworfene Ansätze — inkl. Ursache/Kontext, damit sie
        nicht wiederholt werden. Wenn keine: "keine".
        ## Nächster Schritt
        Der unmittelbar sinnvollste nächste Schritt.

        Antworte NUR mit dem Handover-Dokument, ohne Vorrede.
        """.trimIndent()

    // Generiert den Handover-Text aus dem vollständigen Verlauf der Session (one-shot).
    suspend fun generate(sessionId: String): String {
        val session = repo.getSession(sessionId) ?: error("Session nicht gefunden: $sessionId")
        val history = repo.messages(sessionId)
        require(history.isNotEmpty()) { "Leerer Chat — kein Handover möglich." }
        val messages =
            history.map { ChatMessage(role = it.role, content = it.text) } +
                ChatMessage(role = Role.USER, content = "Erzeuge jetzt das Handover wie instruiert.")
        return engine.complete(
            providerId = session.providerId,
            modelId = session.modelId,
            messages = messages,
            system = instruction,
        )
    }

    // Übernimmt den (ggf. vom Nutzer geprüften) Handover-Text in eine NEUE Session desselben
    // Chats: gleicher Provider/Modell/Projekt, Handover als erste (Assistant-)Nachricht, sodass
    // das LLM direkt weiterarbeiten kann. Alte Session bleibt als Verlauf erhalten. Gibt die
    // neue sessionId zurück.
    suspend fun apply(
        sessionId: String,
        handoverText: String,
        newSessionId: String,
    ): String {
        val old = repo.getSession(sessionId) ?: error("Session nicht gefunden: $sessionId")
        val now = System.currentTimeMillis()
        repo.createSession(
            id = newSessionId,
            title = old.title + " (Fortsetzung)",
            providerId = old.providerId,
            modelId = old.modelId,
            now = now,
        )
        old.projectId?.let { repo.moveChatToProject(newSessionId, it, now) }
        // Handover als Assistant-Nachricht sichtbar in den neuen Verlauf legen (Kontext für
        // Modell UND Nutzer). Folgefragen des Nutzers hängen daran an.
        repo.appendMessage(newSessionId, Role.ASSISTANT, handoverText, now)
        return newSessionId
    }
}
