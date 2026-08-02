<!-- CHANGE-MARKER: Roadmap konsolidiert (Phase-1 abgeschlossen, Phase-2 Produktausbau). Siehe CHANGELOG.md -->

# Roadmap

> **Status-Legende:** `erledigt` · `teilweise` · `in Arbeit` · `geplant` · `blockiert`.
> Phase 1 (MVP-Kern + Plattform-Grundlagen) ist abgeschlossen und released (v0.6.1).
> Phase 2 ist der Produktausbau zu einem professionellen, mehrkanaligen KI-Assistenten.
> **Overlay bleibt das Kern-Feature**; Fullscreen, Notification und Share sind zusätzliche
> Zugänge zum SELBEN Chat.

---

## Phase 1 — MVP-Kern & Plattform (abgeschlossen, released v0.6.1)

| Meilenstein | Inhalt | Status |
|---|---|---|
| **M0** | Repo-Scaffold, Multi-Modul-Gradle, Version-Catalog, Convention-Plugins, CI/CD (`ci.yml`/`release.yml`/`codeql.yml`), stabiler Signing-Key, Release-Please-Automatik | `erledigt` |
| **M1** | `:core-llm` OpenAI-compat Transport + `:core-security` Keystore-BYOK + `ChatScreen` + Onboarding (Key-Eingabe) | `erledigt` |
| **M2** | `ACTION_PROCESS_TEXT` + Share-Target + ML Kit OCR + Permission-Hub-lite | `erledigt` |
| **M3.1** | `SYSTEM_ALERT_WINDOW`-Bubble + Foreground-Service (`specialUse`) + Permission-Item + Toggle | `erledigt` (v0.6.0) |
| **M3.2** | Chat im Overlay-Panel (via `ConversationEngine` + Hilt-`@EntryPoint`) | `erledigt` (v0.6.0) |
| **M3-Touch** | Touch-Fix: Tap/Drag in Compose (`pointerInput`); Honor-Schwebefenster-Recht dokumentiert | `erledigt` (v0.6.1) |
| **M5** | Anthropic-Adapter (dedizierter Seam) + Config-Provider (DeepSeek/Kimi/Grok/Gemini/OpenRouter), Live-Modell-Katalog | `erledigt` |

**Provider-Bugfix-Serie (v0.5.x):** `stream:true`-Root-Cause (`@EncodeDefault`), In-Stream-Fehler typisiert, `:free`-Preisfilter, `reasoning_content` gelesen, Updater-Install-Dialog-Fix, Provider-Hub-UI.

---

## Phase 2 — Produktausbau (aktuell)

Reihenfolge bewusst so gewählt, dass **kein Doppel-Refactoring** entsteht: erst Fundament
(Chat-Kern vereinheitlichen, Bubble solide), dann Marke, dann Oberflächen, dann Politur.

### P2.0 — Doku-Konsolidierung `in Arbeit`
Diese Roadmap + HANDOVER an den echten Stand angleichen; Widersprüche raus (ROADMAP-Status
war komplett veraltet; M3.3 war fälschlich als „fertig" markiert, obwohl am Gerät kaputt).

### P2.1 — Chat-Kern vereinheitlichen (Fundament, KEIN sichtbares Feature) `geplant`
**Warum zuerst:** Es gibt zwei parallele Chat-Codepfade — `feature-chat/ChatViewModel`
(direkt über `ProviderFactory`) und der Overlay über `core-conversation/ConversationEngine`.
Für „ein Chat, vier Zugänge" muss **`ConversationEngine` die einzige Streaming-Quelle für
alle Oberflächen** werden. Außerdem: Session-Modell für **Multi-Chat** einführen (mehrere
Konversationen, je mit eigenem Provider/Modell) — heute existiert nur eine implizite Session.
- `feature-chat` auf `ConversationEngine` umstellen (ChatViewModel entschlacken).
- Session-/Konversations-Store in `core-conversation` (Liste von Chats, aktive Auswahl,
  je Chat: Provider+Modell+Verlauf). Persistenz (Room o.ä.) — Verläufe überleben Neustart.
- Ziel: Overlay, Fullscreen, Notification, Share greifen alle auf denselben Session-Store.

### P2.2 — Bubble-Snapping & -Verhalten (refactoring-fest) `geplant`
Die aktuelle M3.3-Umsetzung (PR #28) ist am Gerät **unbrauchbar** (kein Snapping, IME
schiebt Panel kaputt, kein Papierkorb) und wird **überarbeitet, nicht nur gemergt**.
So gebaut, dass das folgende UI-Redesign sie nicht erneut anfassen muss:
- **Snapping wie Android-Standard** (Messenger/HONOR): Bubble klebt an der Kante, animiert.
- **Papierkorb zum Schließen:** beim Drag erscheint mittig-unten eine Schließzone; Bubble
  dort loslassen → Overlay aus (HONOR Magic V2 / Android-Bordmittel-Verhalten).
- **Panel feste Größe** + korrekte Positionsberechnung (nicht dynamisch verschoben).
- **IME:** setzt Snapping/feste Größe voraus — Panel klappt vollständig sichtbar auf.
- **Rotation:** vertikale Anordnung auf horizontal „mappen" mit Fallbacks.

### P2.3 — Logo & Marke `Design entschieden, Umsetzung mit P2.5 gebündelt`
Ersetzt das alte, nie abgestimmte Material-Chat-Icon. **Entschieden (2026-08-02):** Konzept
**„D4"** — Monogramm-**O** (Gold-Ring = O/App-Ebene) + schwebender Paper-Punkt mit Gold-Funke
(KI liegt *über* der App = Overlay). Palette: Gold `#D0BB3E`, Slate `#485956`, Paper `#EBEFEE`
(Figma-Generator). Responsive: volle Version mit Funke (Icon/Splash), reduziert ohne Funke
(kleine Bubble). Referenz: `brand/logo-concept-D-variants.png`, `brand/logo-D4-size-test.png`;
Details in Memory `overlai-logo-design`. Name **„OverlAI" bleibt**.
**Asset-Umsetzung** (Adaptive-Icon-Vektor foreground/background, Bubble in `OverlayBubble.kt`
statt Material-Icon, Splash, Theme-Palette angleichen) **bewusst in P2.5 gebündelt** — dort wird
das Theme ohnehin angefasst, kein doppeltes Refactoring.

### P2.4 — Vier Entry-Points (alle → selber Chat-Kern) `geplant`
- **(a) Overlay-Bubble** — vorhanden (P2.2).
- **(b) Fullscreen** — vorhanden (MainActivity), ins neue UI integrieren.
- **(c) Benachrichtigungs-Panel** — persistente Notification mit Quick-Reply/Aktion,
  öffnet Chat direkt aus der Notification-Zeile (neu).
- **(d) Share** — `ProcessTextActivity`/`ShareReceiverActivity` vorhanden, an neuen
  Session-Store + UI anpassen (geteilter Text/Bild landet in einem Chat).
- Ein **separater Toggle je Zugang** + Einstellung fürs App-Verhalten (Permission
  rechtfertigt NICHT automatisches Auftauchen der Bubble — Nutzer-Feedback).

### P2.5 — UI-Redesign: Multi-Chat, Modelle, Kontingente `geplant`
Intuitiv & professionell nach gängigen Standards (Material 3, mehr als 2 Bottom-Tabs):
- **Multi-Chat-Übersicht:** mehrere Chats, je mit Anbieter/Modell erkennbar, wechseln.
- **Modellanzeige & -wahl** klar pro Chat; Wechsel mit Hinweis (Kontext-/Kostenfolge).
- **API-Kontingente einsehen** aus der App (soweit Provider-APIs das hergeben — pro
  Provider prüfen; Fallback: Hinweis statt Fehlanzeige).
- **Klares Verhalten + Hinweise** (z. B. „mit weiterer Nutzung entstehen Kosten").
- Alles Nötige aus der App heraus (Keys, Modelle, Kontingente, Verhalten).
- **Logo-Assets (aus P2.3) hier mit umsetzen:** Adaptive-Icon (Vektor), Bubble in
  `OverlayBubble.kt`, Splash, Theme-Palette auf Gold/Slate/Paper umstellen — Theme wird
  hier ohnehin angefasst.

### P2.6 — Updater/Installer wie Wickelfinder `geplant`
Vorbild: `Labushuya/wickelfinder`. Übernehmen:
- **Update-Check über GitHub-Releases-API** (`/releases/latest`), nicht `latest.json` —
  liest `tag_name` (Version) + `body` (Changelog) + erstes `.apk`-Asset. **Achtung:**
  gegen Release-Please-Tags prüfen; bei mehreren Assets präzises Matching (nicht „erstes").
- **SemVer-Comparator** (Prerelease-Precedence) — aus Wickelfinder nach Kotlin portieren,
  unit-getestet.
- **UI:** markengestyltes BottomSheet mit Version + Changelog + Progress + „Jetzt
  aktualisieren"; Auto-Check beim Start (still) + manueller Menüpunkt.
- **Install:** OverlAIs bestehenden nativen `PackageInstaller.Session`-Flow BEHALTEN
  (mächtiger als Wickelfinders `open_filex` — Status-Callbacks, `PENDING_USER_ACTION`).

### P2.7 — UI-Politur & Restfehler `geplant`
Feinschliff nach den großen Blöcken: „Alle Keys löschen", Modell-Suchfeld, Kosmetik,
Dependabot-PRs (13 offen, teils Major), Grok-Gerätetest.

---

## Phase 3 — Erweiterte Fähigkeiten (später)

| Meilenstein | Inhalt | Status |
|---|---|---|
| **M4** | `AccessibilityService` Node-Read + `MediaProjection`-Fallback — die KI „sieht" die App unter der Bubble (das eigentliche Overlay-Alleinstellungsmerkmal) | `geplant` |
| **M6** | Web-Search-Router (native Grounding + externes RAG) + Whisper-Transcription (`microphone`-FGS) | `geplant` |

---

## Bewusste Design-Entscheidungen

- **Sideload-only, kein Play Store.** Ermöglicht Overlay, Accessibility-Reader und
  In-App-Updater legal. Konsequenz: Play-Protect-Warnungen, „unbekannte Quellen"-Grant.
- **Eigener In-App-Updater über die GitHub-Releases-API** (nicht `latest.json`) → SemVer-
  Vergleich → nativer `PackageInstaller.Session`. Der OS-Install-Dialog pro Update ist
  unvermeidbar und bewusst ins UX integriert. (Korrigiert die frühere `latest.json`-Annahme
  — Vorbild Wickelfinder.)
- **Kein On-Device-Modell.** Rein Provider-API-getrieben (BYOK).
- **Bubble = `SYSTEM_ALERT_WINDOW`**, nicht die Notification-Bubbles-API (die kann
  systemweit deaktiviert sein → unsichtbar). HONOR/EMUI verlangt ZUSÄTZLICH ein verstecktes
  „Schwebefenster"-Recht (App-Info) — sonst bleibt das Overlay unsichtbar.
- **Ein Chat-Kern (`ConversationEngine`) für alle vier Zugänge** — keine parallelen
  Chat-Implementierungen (P2.1 räumt die bestehende Doppelung auf).
- **Overlay ist der Produkt-Kern**, die anderen Zugänge sind gleichwertige Türen zum
  selben Chat, nicht eigene Feature-Silos.
