# OverlAI — Session-Handover

**Stand:** 2026-08-02 · **Letzter Release:** v0.6.1 (live; Overlay-Bubble mit funktionierendem Touch). **Offen:** M3.3-Robustheit in PR #28 (`feat/overlay-robustness`, CI-grün, NICHT gemergt — Gerätetest steht aus, Gerät war beim Bauen ab) · **Repo:** `Labushuya/overlai` (public) · **Lokal:** `C:\Code\claude\apps\overlai`

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

## 4. Roadmap (Reihenfolge zu klären mit Nutzer)

**Sofort:**
1. ~~v0.5.0-APK-Sichtbarkeit klären~~ **ERLEDIGT** — APK vorhanden, v0.5.0 am Gerät getestet, funktional.
2. ~~v0.5.0/v0.5.1 Gerätetest~~ **ERLEDIGT (2026-08-01):** Alle Provider **außer Grok** mit bezahltem API-Zugang am Gerät getestet — funktional (inkl. Anthropic mit dem v0.5.1-`stream`-Fix, OpenRouter, DeepSeek). Der `stream`-Fix hält über alle Adapter. **Offen: nur Grok** (xAI) noch ungetestet — Registry-Eintrag da (`baseUrl=https://api.x.ai`, `defaultModel=grok-4.5` = Doku-Fallback), aber ohne bezahltes Konto nicht verifiziert.

**Feinschliff (klein):**
- „Alle Keys löschen" in Einstellungen (`KeyVault.clear()` existiert, kein Button).
- Suchfeld in langen Modelllisten (der alte Katalog hatte Suche; im neuen Hub bewusst weggelassen — bei Bedarf nachrüsten).
- Dependabot-PRs abarbeiten (Major-Bumps einzeln + CI prüfen).

**M3: Overlay-Bubble** (`SYSTEM_ALERT_WINDOW`) — **RELEASED in v0.6.0 (PR #24 + #25 gemergt, live, verifiziert).** Modul `:feature:feature-overlay`:
  - **M3.1 Plattform-Mechanik:** `OverlayService` (Foreground, `foregroundServiceType=specialUse`, NotificationChannel, `START_NOT_STICKY`, `canDrawOverlays`-Guard), `OverlayWindowController` (Bubble + Panel als `TYPE_APPLICATION_OVERLAY`, Drag/Tap per Touch-Slop), `OverlayLifecycleOwner` (ViewTree-Owner für ComposeView ohne Activity, siehe Memory), Permission-Hub-Item + Toggle (`SettingsStore.overlayEnabled` + `OverlaySettingsScreen`, in `:app` verdrahtet).
  - **M3.2 Chat im Panel:** `OverlayDependencies` (Hilt-`@EntryPoint`) liefert die `ConversationEngine` in den Service; `OverlayChatState` (State-Holder, kein ViewModel, vom Service gehalten → überdauert Panel-Auf/Zu) hält die `SnapshotStateList<UiMessage>` und streamt `engine.stream()`; `OverlayPanel` = echter Mini-Chat (Verlauf + Eingabe + Streaming). Panel-LayoutParams jetzt fokussierbar (Texteingabe), `WATCH_OUTSIDE_TOUCH` schließt.
- **v0.6.1 (Touch-Fix, am Gerät verifiziert):** In v0.6.0 war die Bubble sichtbar, reagierte aber auf NICHTS. Zwei Ursachen, nacheinander gefunden (per adb-Logcat + WindowManager-Dump, nicht geraten): (1) **Honor/EMUI blockiert Overlays zusätzlich über ein verstecktes „Schwebefenster"-Recht** (App-Info) — trotz erteilter `SYSTEM_ALERT_WINDOW` bleibt das Overlay sonst unsichtbar. (2) **Der `ComposeView` konsumiert `ACTION_DOWN` selbst** → ein `View.OnTouchListener` am Fenster-Root feuert per Android-Dispatch-Kontrakt NIE. Fix: Touch/Drag/Tap in Compose via `Modifier.pointerInput` (`detectTapGestures`+`detectDragGestures`) in `OverlayBubble`, Deltas per Callback an den Controller. Zusätzlich robuster gemacht: `OverlayLifecycleOwner` in zwei Phasen (CREATED vor `addView`, RESUMED danach) + `setViewCompositionStrategy(DisposeOnLifecycleDestroyed)`, feste Bubble-Fenstergröße statt WRAP_CONTENT, Service-Auto-Restart in `MainActivity` bei `overlayEnabled`.
- **OFFEN: v0.6.1-Gerätetest Rest** — Bubble-Tap+Drag ok; noch prüfen: Panel-Chat mit echtem Key (streamt eine Antwort über der Fremd-App?), Verhalten über mehreren Apps, Toggle-aus → alles weg, App-Neustart → Bubble kommt via Auto-Restart wieder.
- **M3.3 Robustheit — CODE FERTIG, CI-grün, in PR #28 (`feat/overlay-robustness`), NICHT gemergt, GERÄTETEST STEHT AUS** (Gerät war beim Bauen ab). Sechs Punkte: (1) Bubble-Clamping + Rand-Snapping (nicht mehr verlierbar, `onDragEnd`→`snapToEdge`), (2) Panel-Position an Bubble-Seite + `SOFT_INPUT_ADJUST_PAN` gegen IME-Verdeckung, (3) Rotation via `OverlayService.onConfigurationChanged`→`Controller.onConfigChanged`, (4) Toggle-Konsistenz: Service setzt `setOverlayEnabled(false)` bei Permission-Verlust (via `OverlayDependencies.settingsStore()`), (5) Konversations-Reset (`reset()` + Papierkorb-Icon), (6) Stream-Cancel (`streamJob` + `cancelStream()`, Senden↔Stopp-Button). `feature-overlay` hängt jetzt auch an `:core-data`. **Nächster Schritt: Gerätetest** (5 Checks: Snapping, IME, Rotation, Toggle-nach-Permission-Entzug, Reset+Cancel) → dann Merge + Release (Reset+Cancel sind Features → v0.7.0), **nur nach Freigabe**.
- Danach M4 (AccessibilityService/MediaProjection Screen-Read), M5 ist erledigt (Anthropic-Adapter existiert), M6 (Web-Search-Router + Transcription).

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
