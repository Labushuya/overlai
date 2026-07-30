<!-- CHANGE-MARKER v0.1.0: Initiales Projektgrundgerüst (siehe CHANGELOG.md) -->

# Roadmap

> **Status-Legende:** `implementiert` · `teilweise` · `geplant` · `blockiert`.
> De-Risking-Reihenfolge: die Build-/Signing-/Update-Pipeline und der
> permission-freie MVP-Kern zuerst, die harten Plattform-Kämpfe (Overlay,
> Screen-Reading) bewusst danach.

| Meilenstein | Inhalt | De-risked | Status |
|---|---|---|---|
| **M0** | Repo-Scaffold, Multi-Modul-Gradle, Version-Catalog, Convention-Plugins, Hygiene-Docs + Templates, CI/CD (`ci.yml`/`release.yml`/`codeql.yml`) mit **stabilem Signing-Key**, erster `v0.1.0`-Release der installiert & self-updated | Build/Signing/Update-**Pipeline** vor jedem Feature | `teilweise` |
| **M1** | `:core-llm` OpenAI-compat Transport + `:core-security` Keystore-BYOK + `ChatScreen`, JVM-getestet mit MockWebServer; Onboarding (Key-Eingabe) | Provider/Vision/BYOK-Kette, null Permissions | `geplant` |
| **M2** | `ACTION_PROCESS_TEXT` + Share-Target + ML Kit OCR + Permission-Hub-lite | Echte Entry-Points, null Policy-Risiko → **Phase-1-MVP fertig** | `geplant` |
| **M3** | `SYSTEM_ALERT_WINDOW`-Bubble + Foreground-Service (`specialUse`) + Permission Hub (full) | Erster echter Platform-Fight, isoliert | `geplant` |
| **M4** | `AccessibilityService` Node-Read + `MediaProjection`-Fallback | Screen-Reading, sideload-only gerechtfertigt | `geplant` |
| **M5** | Anthropic-Adapter (beweist den Seam) → Config-Provider (DeepSeek/Kimi/Grok/Gemini/OpenRouter) | Multi-Provider-Abstraktion am schwersten Adapter validiert | `geplant` |
| **M6** | Web-Search-Router (native Grounding + externes RAG) + Whisper-Transcription (`microphone`-FGS) | Capability-gated Features zuletzt | `geplant` |

## Bewusste Design-Entscheidungen

- **Sideload-only, kein Play Store.** Ermöglicht Overlay, Accessibility-Reader und
  In-App-Updater legal. Konsequenz: Play-Protect-Warnungen, „unbekannte Quellen"-Grant.
- **Eigener In-App-Updater.** `latest.json` → SemVer-Vergleich → `PackageInstaller`.
  Der OS-Install-Dialog pro Update ist unvermeidbar und bewusst ins UX integriert.
- **Kein On-Device-Modell.** Rein Provider-API-getrieben.
- **Bubble = `SYSTEM_ALERT_WINDOW`**, nicht die Notification-Bubbles-API (die kann
  systemweit deaktiviert sein → unsichtbar). Der Permission Hub stellt das klar.
