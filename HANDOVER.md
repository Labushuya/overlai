# OverlAI — Session-Handover

**Stand:** 2026-10-14 · **Letzter Release:** v0.14.0 (2026-08-16) · **Repo:** `Labushuya/overlai` (public) · **Lokal:** `C:\Code\claude\apps\overlai`

Single Source of Truth für Session-Übergaben. Neue Session liest diese Datei als Erstes.

---

## 1. Was OverlAI ist

Android-Overlay-KI-Assistent (Kotlin + Jetpack Compose + Material 3, multi-modul Gradle, Kotlin DSL). **BYOK** (Bring-Your-Own-Key), kein Backend — Requests direkt Gerät→Provider. **Sideload-only** (GitHub Releases, kein Play Store), eigener In-App-Updater. Provider: OpenAI, Anthropic, OpenRouter, DeepSeek, Grok (xAI), Groq, Kimi (Moonshot), Gemini.

**Kernidee:** KI schwebend *über* der aktuell geöffneten App via `SYSTEM_ALERT_WINDOW`-Bubble. Fullscreen, Notification und Share sind gleichwertige Zugänge zum selben Chat-Kern.

**Architektur:** `feature-*` → nur `core-*`, nie feature→feature. Wiring/DI nur in `:app`. ViewModels via `simpleFactory` (kein Hilt in VMs). `core-llm` = pures JVM (lokal testbar ohne SDK). Android-Module bauen lokal via `./gradlew :app:assembleDebug` (SDK unter `C:\Users\I538150\Android\Sdk`, `local.properties` nötig, gitignored).

---

## 2. Release-Stand & abgeschlossene Phasen

### Phase 1 — abgeschlossen (v0.6.1)
M0 Pipeline/Signing, M1 core-llm/BYOK/Chat, M2 ProcessText/Share/OCR, M3 Overlay-Bubble+Panel+Touch-Fix, M5 Anthropic+Config-Provider+Live-Katalog, Provider-Bugfix-Serie (root cause: `stream:true` fehlte wegen `encodeDefaults=false` → `@EncodeDefault(ALWAYS)` Fix).

### Phase 2 — Produktausbau (läuft)

| Etappe | Version | Inhalt |
|--------|---------|--------|
| P2.1a | v0.6.x | Chat-Kern vereinheitlicht: `ConversationSession` über `ConversationEngine`, `ChatUiMessage`-Modell, dünne Adapter |
| P2.2 | v0.6.x | Bubble-Snapping+Clamping+Papierkorb+Rotation (ValueAnimator, kein neue Dep) |
| P2.1b+c | v0.7.x | Room-Persistenz Multi-Chat, Marken-Politur, D4-Icon |
| P2.4 | **v0.11.0** | Vier Entry-Points: Overlay+Fullscreen+Notification (QuickReply)+Share, Deep-Link, CodeQL False-Positive per API schließen |
| P2.5-E1 | **v0.12.0** | Warme Palette (Creme/Taupe/Greige), Shapes.kt/Spacing.kt, AppLogo D4, 3 Bottom-Tabs, Groq als neuer Provider |
| P2.5-E2 | **v0.13.0** | Modellwechsel im offenen Chat (Header-Chip → Sheet), Kostenhinweis |
| P2.5-E3 | **v0.14.0** | Guthaben-/Kontingente-Ansicht im ProviderHub (OpenRouter `/credits` + Fallback-Hinweis für andere) |
| **P2.5-E4** | **🔴 OFFEN** | Overlay Panel-Größe + IME-Fix (Tastatur überdeckt Eingabefeld) |

### Nächster Schritt: P2.5-E4 (Overlay IME-Fix)

**Branch:** `feat/overlay-ime` (lokal, rebased auf main, **NICHT committed, NICHT gepusht**)

**Was bereits implementiert ist (70 Zeilen Diff in `OverlayWindowController.kt`, unverified):**
- `panelRestY`/`panelRestHeight`/`imeVisibleHeight` als State-Felder
- `SOFT_INPUT_ADJUST_RESIZE` auf dem Panel-WindowManager-Fenster
- `ViewCompat.setOnApplyWindowInsetsListener` → `onImeHeightChanged(ime: Int)`
- Panel nach oben schieben + verkleinern wenn Tastatur auf; Restore auf Ruhe-Geometrie wenn zu
- `screenWidth`/`screenHeight` als Properties statt Funktionen (TooManyFunctions-Grenze)

**Status:** Implementierung liegt lokal uncommitted. Noch kein Commit, kein CI, kein Gerätetest. Neue Session: committen, CI prüfen, am Gerät testen, PR erstellen.

**Plan-Datei:** `~/.claude/plans/refactored-brewing-cake.md` → Abschnitt „Etappe 4"

---

## 3. Wichtige Architektur-Details (nicht neu herleiten)

### Chat-Kern
- `ConversationEngine` (zustandslos, `core-conversation`) ← `ConversationSession` (zustandsbehaftet, pro Chat)
- `SessionRepository` (Room, `core-data`): `ChatSessionEntity` + `ChatMessageEntity`, `active_session_id` in SettingsStore
- Alle vier Entry-Points (Overlay, Fullscreen, Notification, Share) nutzen denselben Session-Store
- `HandoverGenerator` (core-conversation): one-shot `complete()`, generiert Ziel/Erledigt/Offen/Probleme/Nächster-Schritt, `primeAndRespond()` für Autostart neuer Fortsetzungssessions

### Overlay
- `OverlayService` (FGS `specialUse`) → `OverlayWindowController` → `OverlayBubble` + `OverlayPanel`
- Touch in Compose via `Modifier.pointerInput` (nie `View.OnTouchListener` — `ComposeView` konsumiert `ACTION_DOWN` selbst)
- **Honor/EMUI-Falle:** `SYSTEM_ALERT_WINDOW` allein reicht nicht — separates „Schwebefenster"-Recht in App-Info nötig
- `OverlayLifecycleOwner` setzt manuell ViewTree-Owner (Lifecycle/ViewModelStore/SavedState) — ohne das crasht `ComposeView` im WindowManager

### Provider/LLM
- `OpenAiCompatTransport` treibt alle non-Anthropic Provider (OpenAI, DeepSeek, Groq, Grok, Kimi, Gemini-Shim, OpenRouter)
- `AnthropicProvider` dedizierter Adapter (systemPlacement=TOP_LEVEL, ApiKeyHeader, eigene SSE-Events)
- **Kritischer Fix (ewig merken):** `@EncodeDefault(ALWAYS)` auf `stream`-Feld in BEIDEN DTOs (`OpenAiChatRequest` + `AnthropicRequest`) — `encodeDefaults=false` würde den Default-Wert `true` sonst weglassen → Provider antwortet non-streaming → alle Chats stumm
- Live-Modell-Katalog: `/v1/models` für OpenAI/Anthropic/OpenRouter/DeepSeek/Kimi/Grok, `/models` für Gemini-Shim, StaticModels-Fallback für Groq
- `LlmProvider.fetchCredits()` (optional, default null): OpenRouter implementiert (`/api/v1/credits`), alle anderen → Fallback-Hinweis

### Design-System (core-ui)
- `OverlAiTheme` immer mit Marken-Palette (Dynamic Color komplett entfernt)
- Warme Palette: `EDD9CA` Creme (background), `F6ECE3` (surface), `B9A8A6` Taupe (primary), `D4C9C8` Greige (surfaceVariant), `A5735C` Terracotta (secondary), `3A2F2A` WarmInk (onBackground)
- `OverlaiShapes` + `OverlaiSpacing` + `AppLogo` (D4 Canvas-Composable) in core-ui
- 3 Bottom-Tabs: Chats / Provider / Einstellungen

---

## 4. Roadmap nach P2.5-E4

Vollständige Roadmap in `ROADMAP.md`. Kurzfassung:

- **P2.6** — Updater/Installer wie Wickelfinder: GitHub-Releases-API statt `latest.json`, SemVer-Comparator, nativer `PackageInstaller.Session`-Flow behalten
- **P2.7** — Politur + Dependabot (teils Major-Bumps: AGP 9, okhttp 5, Kotlin-Gruppe) + Grok-Gerätetest
- **Phase 3** — M4 AccessibilityService/MediaProjection (KI „sieht" App), M6 Web-Search-Router + Transcription

---

## 5. Betriebswissen

### Build & CI
```bash
# Lokal (kein SDK nötig):
./gradlew :core:core-llm:test ktlintCheck detekt --continue

# Debug-APK lokal:
./gradlew :app:assembleDebug
export PATH="$PATH:$USERPROFILE/Android/Sdk/platform-tools"
adb install -r app/build/outputs/apk/debug/app-debug.apk

# CI beobachten:
gh run list --workflow=CI --branch=main
gh run watch <id> --exit-status
```
- Node-20-Deprecation-Annotation in CI = harmlos
- **Lint-Falle:** IMMER `--continue` nutzen und alle Module prüfen, nicht nur betroffene (`ktlintFormat` sortiert Import-Ordering automatisch)

### Release-Flow
```bash
# Release-Please-PR mergen (KEIN --delete-branch):
gh pr merge <n> --squash

# Verifikation nach Release:
gh release view vX.Y.Z --json assets
curl -s https://raw.githubusercontent.com/Labushuya/overlai/gh-pages/latest.json
```
- `release.yml` triggert via `workflow_run` nach „Release Please" → signierte APK + `latest.json` automatisch
- Signing-Key: `ANDROID_KEYSTORE_BASE64` + 3 weitere Secrets — stabil halten (sonst `INSTALL_FAILED_UPDATE_INCOMPATIBLE`)
- `fix:` → Patch-Bump, `feat:` → Minor-Bump (Conventional Commits)

### Gerätediagnose (Honor VER-N49)
```bash
adb logcat
adb shell dumpsys window windows
# Screenshot (Gerät hat 2 Displays — aktives via dumpsys SurfaceFlinger --display-id):
MSYS_NO_PATHCONV=1 adb shell "screencap -d <id> -p /sdcard/x.png" && adb pull /sdcard/x.png
```
- `frame=0x0`/`alpha=0.0` im WindowManager-Dump = KEIN verlässlicher Render-Indikator → Screenshot ist Wahrheit
- CodeQL False-Positive (FLAG_MUTABLE für RemoteInput): `gh api PATCH repos/Labushuya/overlai/code-scanning/alerts/<n> -f state=dismissed -f dismissed_reason="false positive"`

### Verhaltensregeln (Nutzer)
- Deutsch, direkt; Release-PR erst nach ausdrücklicher Freigabe mergen
- Kein `--delete-branch` anhängen (Nutzer-Feedback)
- Bei Provider-Bugs: echten `curl`-Datenpunkt holen statt raten
- Fertig + verifiziert = zeitnah nach main mergen, keine Branches stapeln

---

## 6. Wo eine neue Session ansetzt

```bash
cd C:/Code/claude/apps/overlai
git checkout feat/overlay-ime
git status  # zeigt 1 modifizierte Datei: OverlayWindowController.kt
```

**Aufgabe P2.5-E4:**
1. `OverlayWindowController.kt`-Änderungen reviewen (70 Zeilen IME-Fix, `git diff main`)
2. Committen + PR erstellen
3. CI grün machen
4. Gerätetest: Overlay öffnen, Eingabefeld antippen → Tastatur darf das Feld nicht verdecken
5. Nach Freigabe mergen → Release-Please-PR → Release v0.15.0

**Danach: P2.6** (Updater-Umbau auf GitHub-Releases-API, Plan in `ROADMAP.md`).
