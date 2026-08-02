# OverlAI — Session-Handover

**Stand:** 2026-08-02 · **Letzter Release:** v0.6.1 (live; Overlay-Bubble). **Phase 1 abgeschlossen; Phase 2 (Produktausbau) läuft — siehe ROADMAP.md.**
- **P2.1a gemergt** (PR #29, `main`): Chat-Kern vereinheitlicht (ConversationSession) — Fundament für Multi-Chat/Entry-Points. Am Gerät verhaltensgleich verifiziert. **Nicht released** (nur main; Verhalten für Nutzer unverändert).
- **PR #28 (M3.3-Robustheit) geschlossen/verworfen** — war am Gerät unbrauchbar; Snapping/Papierkorb kommen in P2.2 neu (auf Basis des vereinheitlichten Kerns).
- Offen nur noch Dependabot-/CI-Bump-PRs (→ P2.7).

Repo: `Labushuya/overlai` (public) · Lokal: `C:\Code\claude\apps\overlai`

Dieses Dokument ist der Übergabepunkt zwischen Sessions. Es beschreibt den **aktuellen Stand**, die **gelösten und offenen Probleme** und die **Roadmap**. Nach einer Kontextbereinigung hier ansetzen.

---

## 1. Was OverlAI ist

Android-Overlay-KI-Assistent (Kotlin + Jetpack Compose, Material 3, multi-modul Gradle). BYOK (Bring-Your-Own-Key), keine Backend-Infrastruktur — Requests gehen direkt Gerät→Provider. **Sideload-only** (GitHub Releases, kein Play Store), eigener In-App-Updater. Provider: OpenAI, Anthropic, OpenRouter, DeepSeek, Grok, Kimi (Moonshot), Gemini.

Architektur: `feature-*` hängen nur an `core-*`, nie aneinander; Wiring/DI nur in `:app`. ViewModels via `simpleFactory` (kein Hilt in VMs). `core-llm` ist pures JVM (lokal testbar); Android-Module (`:app`, `feature-*`, `core-ui`, `core-data`, `core-security`) bauen **nur in CI** (kein lokales Android-SDK auf dieser Maschine → lokal geht nur ktlint/detekt + `:core-llm:test`).

---

## 2. Aktueller Stand (nach dieser Session)

**Alle in v0.5.0 enthalten, CI-grün, released:**

### Gelöste Bugs (Provider/Chat)
- **ROOT CAUSE aller „leere Antwort/204"-Fehler:** `stream:true` fiel wegen `encodeDefaults=false` aus dem Request-Body → Provider antwortete non-streaming → SSE-Reader sah nie `data:` → 204. Fix: `@EncodeDefault(ALWAYS)` auf `OpenAiChatRequest.stream` (`core-llm/.../transport/OpenAiDtos.kt`). Traf ALLE OpenAI-kompatiblen Provider seit v0.1.0. **Das war die eigentliche Ursache** hinter den vorher vermuteten provider-spezifischen Symptomen.
- In-Stream-Fehler wurden verschluckt (leere Bubble) → jetzt als typisierter `LlmError` gemeldet (OpenAiCompat **und** Anthropic-Adapter).
- OpenRouter `:free` „204 bei allen": `error.code` kam als Zahl, DTO erwartete String → Chunk-Deser kippte → Fehler verschluckt. Fix: `OpenAiError.code` = `JsonElement`.
- `:free`-Katalog: markierte tote Slugs als gratis (nur Suffix geprüft) → jetzt nach echtem Preis (`pricing==0`, fail-closed).
- „Model not found" Kimi/Grok/Gemini: hartkodierte veraltete IDs → jetzt **Live-Katalog** (`/v1/models`, Gemini-Shim `/models`, `models/`-Präfix-Strip, Allow-Präfixe je Provider).
- Reasoning-Modelle (Kimi k2-thinking, DeepSeek-R1) streamen Text in `reasoning_content`/`reasoning` statt `content` → jetzt gelesen; Leer-Meldung neutral (Provider+Modell, kein „kostenlos"-Bias).
- **Anthropic hatte denselben latenten `stream`-Bug (nach dieser Session gefixt, noch nicht released):** `AnthropicRequest.stream = true` war ein Serialisierungs-Default ohne `@EncodeDefault(ALWAYS)` → fiel wegen `encodeDefaults=false` aus dem Body → Anthropic hätte non-streaming geantwortet → falsche „Leere Antwort". Fix analog OpenAiCompat + Regressionstest (`AnthropicProviderTest` prüft jetzt `"stream":true` im Body, wie der OpenAiCompat-Test). Der Unit-Test war grün, weil er nur die *Response* mockte, nie den *Request*-Body auf `stream` prüfte. Lokal verifiziert (`:core:core-llm:test`).

### Gelöste Bugs (Updater/Release)
- In-App-Updater „Installieren" reagierte nicht: `PackageInstaller.commit()` zeigt den Dialog nicht direkt, sondern per `STATUS_PENDING_USER_ACTION`-Broadcast → es fehlte der Receiver. Fix: dynamischer `BroadcastReceiver` (`RECEIVER_NOT_EXPORTED`), startet `EXTRA_INTENT` (`FLAG_ACTIVITY_NEW_TASK`), meldet Erfolg/Fehler via StateFlow. **`EXTRA_INTENT` liegt auf `Intent`, nicht `PackageInstaller`** (CI-Compile-Falle).
- Release-Flow: Release-Please-Manifest hing auf 0.1.0 (manuell getaggt bis 0.4.0) → auf echten Stand gesetzt. Zwei load-bearing Repo-Settings aktiviert: `default_workflow_permissions=write` + `can_approve_pull_request_reviews=true`.
- **Release-Automatik:** `release.yml` triggert jetzt via `workflow_run` nach „Release Please" (nicht mehr `push: tags` — ein GITHUB_TOKEN-Tag triggert keinen Folge-Workflow). **Kein manueller Tag-Repush mehr nötig** (funktioniert seit 0.4.4).

### UI-Redesign (v0.5.0, das feat das den Minor-Bump auslöste)
- **Provider-Hub:** ein Akkordeon-Screen (`feature-onboarding/ProviderHubScreen.kt` + `ProviderHubViewModel.kt`) ersetzt die alte dreigeteilte UI (Radio-Liste + Key-Block + separater Modell-Screen). Pro Provider aufklappbare Karte (genau eine offen), darin: Key-Verwaltung (maskiert `••••••••1234` + Ändern/Löschen bzw. Eingabe+Speichern), Capability-Badges, live Modell-Katalog (lazy beim Aufklappen, Kontextfenster + Free sichtbar). Modell-Tap = Modell + Provider aktiv.
- `KeyStatusBadge` in `core-ui/components/` (einheitlicher Status statt 4 Varianten).
- Entfernt: `OnboardingScreen`, `OnboardingViewModel`, `ModelCatalogScreen`, `ModelCatalogViewModel`, `SettingsRoutes.MODELS`.

---

## 3. Offene Probleme / Ungeklärt

- ~~Nutzer meldet „keine APK bereitgestellt" bei v0.5.0~~ **ERLEDIGT (2026-08-01):** APK war vorhanden und wurde am Gerät getestet — funktional. Keine Diskrepanz, kein weiterer Handlungsbedarf.
- **Kein Unit-Test für `ProviderHubViewModel`:** `SettingsStore` ist konkrete DataStore/Android-Klasse (kein Interface) → im JVM-Test nicht fakebar. Ein echter VM-Test erfordert ein `SettingsStore`-Interface-Refactoring (eigener Scope). Aktuell nur CI-Compile + Gerätetest. Katalog-Kernlogik ist durch `ModelCatalogTest` (core-llm) abgedeckt.
- **13 offene Dependabot-PRs** (#1-14, u.a. AGP 8.7→9.3 #9, okhttp 4→5 #12/#14, Kotlin-Gruppe #7, ktlint 12→14 #13) — teils Major-Bumps mit Breaking-Changes, nicht abgearbeitet.
- **Verifikations-Lücke Fallback-Modell-IDs:** `kimi-k2.6`/`grok-4.5`/`gemini-2.5-flash` sind Doku-abgeleitete Fallbacks, nicht gegen echte Konten verifiziert. Irrelevant solange der Live-Katalog lädt (dann kommen echte IDs), aber der Fallback greift bei Netz-/Auth-Fehlern.

---

## 4. Roadmap → siehe `ROADMAP.md` (Single Source of Truth)

Die vollständige, konsolidierte Roadmap steht in **`ROADMAP.md`** (Phase 1 abgeschlossen/released, Phase 2 Produktausbau, Phase 3 später). Kurzfassung des relevanten Stands:

**Phase 1 erledigt & released (v0.6.1):** M0 (Pipeline/CI/Signing), M1 (core-llm/BYOK/Chat), M2 (ProcessText/Share/OCR), M3.1+M3.2 (Overlay-Bubble + Panel-Chat), M3-Touch-Fix, M5 (Anthropic + Config-Provider + Live-Katalog), Provider-Bugfix-Serie.

**M3-Touch-Fix (v0.6.1, am Gerät verifiziert):** Bubble war sichtbar aber tot. Zwei Ursachen (per adb gefunden): (1) Honor/EMUI verlangt zusätzlich ein verstecktes „Schwebefenster"-Recht (App-Info), sonst Overlay unsichtbar trotz `SYSTEM_ALERT_WINDOW`. (2) `ComposeView` konsumiert `ACTION_DOWN` selbst → `View.OnTouchListener` am Root feuert nie → Touch in Compose via `Modifier.pointerInput`.

**⚠️ M3.3-Robustheit (Branch `feat/overlay-robustness`, PR #28) — am Gerät UNBRAUCHBAR:** Snapping greift nicht, IME schiebt das Panel kaputt, kein Papierkorb-Schließen. **NICHT mergen.** Wird in **P2.2** von Grund auf überarbeitet (Android-Standard-Snapping + Papierkorb mittig-unten + feste Panel-Größe). Code auf dem Branch dient nur als Referenz/Ausgangspunkt.

**Phase 2 (aktuell) — Reihenfolge in ROADMAP.md:** P2.0 Doku (dieser Schritt) · P2.1 Chat-Kern vereinheitlichen + Multi-Chat-Sessions (Fundament, verhindert Doppel-Refactoring) · P2.2 Bubble-Snapping/Papierkorb (überarbeitet) · P2.3 Logo/Marke (Konzepte vorschlagen, Name OverlAI bleibt) · P2.4 vier Entry-Points (Overlay=Kern, +Fullscreen/Notification/Share, alle → selber Chat-Kern) · P2.5 UI-Redesign (Multi-Chat, Modellanzeige, Kontingente) · P2.6 Updater wie Wickelfinder (GitHub-Releases-API + SemVer, nativen PackageInstaller behalten) · P2.7 Politur + Dependabot + Grok-Test.

**Phase 3 (später):** M4 (AccessibilityService/MediaProjection Screen-Read — KI „sieht" die App), M6 (Web-Search-Router + Transcription).

---

## 5. Betriebswissen (nicht neu herleiten)

- **Release cutten:** Release-Please-PR („chore(main): release X.Y.Z") **mergen** (`gh pr merge <n> --squash`). Dann läuft alles automatisch: Tag → `release.yml` via `workflow_run` → signierte APK + Release + `latest.json` auf gh-pages. **Kein Tag-Repush.** Nutzer merged selbst (outward-facing). **Kein `--delete-branch`** anhängen (Nutzer-Feedback).
- **Verifikation Release:** `gh release view vX.Y.Z --json assets`; `curl -s https://raw.githubusercontent.com/Labushuya/overlai/gh-pages/latest.json`.
- **Lokal baubar:** nur `:core-llm:test`, `ktlintCheck`, `detekt`, `ktlintFormat`. Android-Module → CI. `.kotlin/` ist gitignored.
- **Lint lokal IMMER modulübergreifend prüfen:** `./gradlew ktlintCheck detekt --continue` (nicht nur die berührten Module). Import-Ordering-Fehler in `:app` (z.B. neuer `de.overlai.feature.*`-Import an falscher alphabetischer Stelle) fallen sonst erst in CI auf. `ktlintFormat` sortiert sie automatisch. (Einmal in M3.1 gebissen.)
- **CI beobachten:** `gh run list --workflow=CI --branch=main`, `gh run watch <id> --exit-status`. Node-20-Deprecation-Annotation ist harmlos.
- **Signing-Key ist load-bearing:** base64-Secret `ANDROID_KEYSTORE_BASE64` (+3 weitere). Muss stabil bleiben, sonst `INSTALL_FAILED_UPDATE_INCOMPATIBLE`.
- **Bump-Regel:** `fix:` → Patch, `feat:` → Minor (Conventional Commits).
- **Verhalten ggü. Nutzer:** deutschsprachig, direkt. Bei Provider-Bugs: **echten curl-Datenpunkt** holen statt raten (mehrfach der Durchbruch gewesen). Keine Behauptungen ohne Beleg; keine Zuschreibungen an den Nutzer, die man nicht belegen kann. **Release-PR erst nach ausdrücklicher Freigabe mergen** (outward-facing) — nicht aus einem „endlich"/„ok" ableiten.
- **Lokaler Android-Build + Gerätetest GEHT doch** (entgegen älterer Handover-Annahme „nur CI"): Das SDK liegt unter `C:\Users\I538150\Android\Sdk`, es fehlte nur `local.properties` (`sdk.dir=...`, gitignored). Dann `./gradlew :app:assembleDebug` lokal + `adb install -r app/build/outputs/apk/debug/app-debug.apk`. adb ist nicht im PATH: `export PATH="$PATH:$USERPROFILE/Android/Sdk/platform-tools"`. Debug-Variante = Package `de.overlai.app.debug` (parallel zur Release-App, überschreibt sie nicht). **Gerätenahe Bugs so verifizieren statt blind zu releasen.**
- **Gerätediagnose (Honor VER-N49):** `adb logcat`, `adb shell dumpsys window windows`, Screenshot: `MSYS_NO_PATHCONV=1 adb shell "screencap -d <id> -p /sdcard/x.png"` + `adb pull` (Gerät hat 2 Displays; ID via `dumpsys SurfaceFlinger --display-id`, das aktive nutzen — sonst schwarzes Bild). Auf dieser ROM meldet der WindowManager-Dump für FAST JEDES Fenster `frame=0x0`/`alpha=0.0` im `mInputWindowHandle` → **kein verlässlicher Render-Indikator**, Screenshot ist die Wahrheit.
- **Honor/EMUI-Overlay-Falle:** `SYSTEM_ALERT_WINDOW` allein reicht nicht — es gibt ein separates verstecktes „Schwebefenster"/„Pop-up"-Recht in der App-Info. Ohne das bleibt jedes Overlay unsichtbar.
- **Compose-Touch im Overlay:** Ein `ComposeView` konsumiert `ACTION_DOWN` selbst → `View.OnTouchListener` am Root feuert NIE. Touch immer in Compose (`Modifier.pointerInput`) lösen, nicht per View-Listener. Siehe [[overlai-compose-in-overlay-lifecycle]].

Verwandte Memories: `overlai-stream-default-dropped`, `overlai-packageinstaller-pending-user-action`, `overlai-release-please-manifest`, `overlai-compose-in-overlay-lifecycle`, `overlai-local-build-and-device-test`.
