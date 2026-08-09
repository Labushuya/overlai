# Changelog

Alle nennenswerten Änderungen an diesem Projekt werden hier dokumentiert.

Das Format orientiert sich an [Keep a Changelog](https://keepachangelog.com/de/1.1.0/),
die Versionierung an [Semantic Versioning](https://semver.org/lang/de/).
Dieser Changelog wird ab dem ersten Release von `release-please` gepflegt.

## [0.8.0](https://github.com/Labushuya/overlai/compare/v0.7.2...v0.8.0) (2026-08-09)


### Features

* **chat:** Modell-Chip, Chat-CRUD & geführter Neuer-Chat-Flow (E1) ([#38](https://github.com/Labushuya/overlai/issues/38)) ([4cb8006](https://github.com/Labushuya/overlai/commit/4cb800657e3521c17c3b01591bd909af4bb596a9))

## [0.7.2](https://github.com/Labushuya/overlai/compare/v0.7.1...v0.7.2) (2026-08-09)


### Bug Fixes

* **ui:** Marken-Palette immer anwenden — Dynamic Color entfernt ([092fc32](https://github.com/Labushuya/overlai/commit/092fc32eed6c75f58c62335f02cb2b00b1af77c7))

## [0.7.1](https://github.com/Labushuya/overlai/compare/v0.7.0...v0.7.1) (2026-08-09)


### Bug Fixes

* **ui:** App-Icon + Splash auf Blau-Palette (Release + Debug) ([6e63073](https://github.com/Labushuya/overlai/commit/6e6307376e9eec7c081ff23310be068d6e62a74e))

## [0.7.0](https://github.com/Labushuya/overlai/compare/v0.6.1...v0.7.0) (2026-08-09)


### Features

* Multi-Chat-Persistenz (P2.1b) + Bubble-UX-Block & Marken-Politur (P2.1c) ([#35](https://github.com/Labushuya/overlai/issues/35)) ([ee8bd7a](https://github.com/Labushuya/overlai/commit/ee8bd7ad0832b4bb1cebe1f496a55e5811ee32ad))
* **overlay:** Bubble-Snapping + Papierkorb (P2.2) + Debug-Icon/Updater ([#33](https://github.com/Labushuya/overlai/issues/33)) ([b6482d4](https://github.com/Labushuya/overlai/commit/b6482d4ea3c26d1e9380706e48363458301ca559))
* **ui:** Farbpalette auf Blau-Schema umgestellt ([eb609fe](https://github.com/Labushuya/overlai/commit/eb609fe458adaec4fe0385801793f16ba83f9e65))

## [0.6.1](https://github.com/Labushuya/overlai/compare/v0.6.0...v0.6.1) (2026-08-01)


### Bug Fixes

* **overlay:** Bubble reagiert auf Tap/Drag — Touch-Handling in Compose (pointerInput) ([188d08c](https://github.com/Labushuya/overlai/commit/188d08cc813be7e1b51e84af459ecc0028011e76))

## [0.6.0](https://github.com/Labushuya/overlai/compare/v0.5.1...v0.6.0) (2026-08-01)


### Features

* **overlay:** Overlay-Bubble-Skelett (M3) ([#24](https://github.com/Labushuya/overlai/issues/24)) ([23b6994](https://github.com/Labushuya/overlai/commit/23b699417437f2de1f0779ae305b8298ad6749eb))

## [0.5.1](https://github.com/Labushuya/overlai/compare/v0.5.0...v0.5.1) (2026-08-01)


### Bug Fixes

* **core-llm:** stream:true im Anthropic-Body erzwingen (@EncodeDefault) ([b40cfc0](https://github.com/Labushuya/overlai/commit/b40cfc0d9e29ce74d61dea376aef1580425a189d))

## [0.5.0](https://github.com/Labushuya/overlai/compare/v0.4.6...v0.5.0) (2026-08-01)


### Features

* **onboarding:** Provider-Hub — ein Akkordeon-Screen für Provider+Keys+Modelle ([7a566ca](https://github.com/Labushuya/overlai/commit/7a566ca4f82530618626fb0eeaa2b6e053dbe2f7))

## [0.4.6](https://github.com/Labushuya/overlai/compare/v0.4.5...v0.4.6) (2026-08-01)


### Bug Fixes

* **core-llm:** stream:true im Body erzwingen — Root-Cause aller "leere Antwort"-204 ([99e61b2](https://github.com/Labushuya/overlai/commit/99e61b2ac74b363eaaf8f89d7e0f2a289462e597))

## [0.4.5](https://github.com/Labushuya/overlai/compare/v0.4.4...v0.4.5) (2026-08-01)


### Bug Fixes

* **core-llm:** reasoning_content lesen (Kimi/R1) + Leer-Meldung neutralisieren ([c26c1cb](https://github.com/Labushuya/overlai/commit/c26c1cb91c919d10744bde8e618f896cafb7e486))

## [0.4.4](https://github.com/Labushuya/overlai/compare/v0.4.3...v0.4.4) (2026-08-01)


### Bug Fixes

* **catalog:** Kimi/Grok/Gemini live laden statt veralteter hartkodierter IDs ([8d58434](https://github.com/Labushuya/overlai/commit/8d58434553c9e88d41057d7cb1742d4ec615950e))

## [0.4.3](https://github.com/Labushuya/overlai/compare/v0.4.2...v0.4.3) (2026-08-01)


### Bug Fixes

* **catalog:** :free nur nach echtem Preis, nicht nach Slug-Suffix ([ef1390c](https://github.com/Labushuya/overlai/commit/ef1390c9c79d428165521cfbc46de3789d00982d))
* **core-llm:** OpenRouter :free 204-Fehler — code als Zahl verschluckte den Fehler ([f316dca](https://github.com/Labushuya/overlai/commit/f316dca64bf5b24c345848dbe05fad988b7a9d31))
* **onboarding:** Provider-Setup spiegelt aktiven Zustand statt hart OpenAI ([ad71916](https://github.com/Labushuya/overlai/commit/ad719165282552b0cdd5f469a2370919bf5530c1))

## [0.4.2](https://github.com/Labushuya/overlai/compare/v0.4.1...v0.4.2) (2026-08-01)


### Bug Fixes

* **updater:** EXTRA_INTENT liegt auf Intent, nicht PackageInstaller ([fc61d82](https://github.com/Labushuya/overlai/commit/fc61d82b6c0d611a4866ac0cb0eea0edd289d324))
* **updater:** Install-Dialog erscheint jetzt (fehlender Status-Receiver) ([bddafa4](https://github.com/Labushuya/overlai/commit/bddafa42c4a5413bfd755be5160d0f8adb6de25e))

## [0.4.1](https://github.com/Labushuya/overlai/compare/v0.4.0...v0.4.1) (2026-08-01)


### Bug Fixes

* **core-llm:** In-Stream-Fehler nicht mehr verschlucken (leere Bubble) ([32a090b](https://github.com/Labushuya/overlai/commit/32a090b722e6442cda5ff36f4ba11d6ba02b7db2))

## [Unreleased]

### Added
- **M0 — Projektgrundgerüst:** Multi-Modul-Gradle-Setup (Kotlin DSL, Version-Catalog,
  Convention-Plugins in `build-logic`), `:app` + `core-*` + `feature-*`-Module.
- Native **Kotlin + Jetpack Compose (Material 3)** mit dynamic color + Dark Mode.
- **CI/CD:** `ci.yml` (ktlint/detekt/Tests/assembleDebug/Kover), `release.yml`
  (Tag → signierte Universal-APK → GitHub Release → `latest.json` auf gh-pages),
  `codeql.yml`. Third-Party-Actions auf Commit-SHA gepinnt.
- **Repo-Hygiene:** README (Badges), SECURITY (Threat-Table), PRIVACY, CONTRIBUTING,
  ROADMAP, LICENSE, Issue-/PR-Templates, Dependabot, release-please-Config.
- **M1 — Provider/BYOK/Chat:** `:core-llm` (Provider-Interface, OpenAI-compat
  SSE-Transport, 6 Provider als Config, CapabilityRouter, Vision-Payload),
  `:core-security` (`TinkKeyVault` über Keystore/TEE, `KeyRedactor`),
  `:feature-chat` (Streaming-Chat), `:feature-onboarding` (BYOK-Key-Eingabe).
- **M2 — MVP-Entry-Points:** `ACTION_PROCESS_TEXT` (Text markieren → OverlAI),
  Share-Target (Text + Bild→OCR), `:feature-ocr` (ML Kit gebündelt, offline),
  Quick-Actions (Übersetzen/Zusammenfassen/Erklären/Fragen), Permission-Hub-lite.
- **In-App-Updater:** `latest.json`-Check, SemVer (Downgrade abgelehnt),
  sha256-Verifikation vor Installation, `PackageInstaller`-Session.

### Changed
- **Provider-/Modell-UI komplett überarbeitet (ein Akkordeon-Screen).** Statt einer
  flachen Radio-Liste + separatem Key-Block + eigenem Modell-Screen gibt es jetzt
  **eine aufklappbare Karte pro Provider** (genau eine offen). Aufgeklappt zeigt sie:
  Key-Verwaltung (bei gesetztem Key **maskiert `••••••••1234`** + „Ändern"/„Löschen",
  sonst Eingabe + „Speichern"), Capability-Badges (Chat/Bild/Websuche/…) und den
  **live geladenen Modell-Katalog** (lazy beim Aufklappen, mit Modell-ID + Kontext­fenster
  + „Free"-Marker). Ein **Modell-Tap** setzt Modell **und** Provider app-weit aktiv.
  Einheitlicher `KeyStatusBadge` (● Aktiv / ✓ Key / Kein Key) statt vier verschiedener
  Status-Darstellungen. Ersetzt `OnboardingScreen` + `ModelCatalogScreen`.
- **Release-APK baut jetzt automatisch nach Release-Please.** Bisher triggerte der
  von Release-Please (GITHUB_TOKEN) erzeugte Tag den APK-Build nicht (GitHub-Anti-
  Rekursion) → die APK musste per manuellem Tag-Repush nachgebaut werden. `release.yml`
  läuft jetzt via `workflow_run` nach erfolgreichem „Release Please", ermittelt den
  Tag über das neueste Release und baut/signiert/veröffentlicht automatisch. Ein
  `workflow_dispatch`-Eingang bleibt als manueller (Neu-)Bau-Knopf.
- **Provider-Setup spiegelt jetzt den echten Zustand.** Der Provider-Screen war
  immer auf OpenAI vorselektiert, egal welcher Provider aktiv war. Jetzt startet
  die Auswahl beim app-weit aktiven Provider, markiert ihn mit „● Aktiv", zeigt
  „✓ Key" bei hinterlegtem Key und das aktuell gewählte Modell (reaktiv, auch nach
  Rückkehr aus dem Modell-Katalog).

### Fixed
- **ROOT CAUSE: `stream:true` fehlte im Request-Body → jede Chat-Anfrage bei
  JEDEM Provider schlug als „leere Antwort" (204) fehl (seit v0.1.0).** Das
  `stream`-Feld hatte den Default `true`, und die JSON-Config ließ Default-Werte
  weg (`encodeDefaults=false`) → `stream` fiel aus dem Body → der Provider
  antwortete **non-streaming** (ein JSON-Objekt statt SSE) → unser SSE-Reader fand
  nie eine `data:`-Zeile → falsche 204-„leerer Stream"-Meldung. Das war die
  gemeinsame Ursache hinter den vermeintlich provider-spezifischen Leer-Symptomen
  (OpenRouter `:free`, Kimi …). Fix: `@EncodeDefault(ALWAYS)` erzwingt `stream:true`
  im Body (die korrekte null-Weglassung von `temperature`/`max_tokens` bleibt).
- **Kimi (und andere Reasoning-Modelle) lieferten „leere"/„ausgelastet"-Meldung
  trotz gültigem, bezahltem Konto.** Reasoning-Modelle (Kimi k2-thinking,
  DeepSeek-R1 …) streamen den Text im Feld `reasoning_content`/`reasoning` statt
  `content` — der Transport las nur `content` → Stream schien leer → falsche
  „bei kostenlosen Modellen oft ausgelastet"-Meldung (die ohnehin fälschlich
  free-spezifisch getextet war, auch bei bezahlten Providern). Jetzt wird
  `reasoning_content`/`reasoning` als sichtbarer Text ausgegeben, und die
  Leer-Meldung ist **neutral** (nennt Provider + Modell, kein „kostenlos"-Bias).
- **„Model not found" bei Kimi/Grok/Gemini (auch bei bezahltem Konto).** Diese
  Provider nutzten hartkodierte, veraltete Modellnamen (`moonshot-v1-8k`,
  `grok-2-latest`, `gemini-2.0-flash` …), die die Provider nicht mehr kennen →
  jede Anfrage scheiterte trotz gültigem Key/Kontingent. Jetzt wird der Katalog
  **live** vom Provider geladen (`/v1/models` bzw. Gemini-Shim `/models`), mit
  Chat-Filter je Provider und `models/`-Präfix-Strip für Gemini. Die Fallback-IDs
  sind auf aktuelle Modelle aktualisiert (`kimi-k2.6`, `grok-4.5`, `gemini-2.5-flash`).
- **Leere Chat-Bubble bei In-Stream-Fehlern:** Manche OpenAI-kompatible Provider
  (v.a. OpenRouter `:free`) antworten mit HTTP 200 und liefern den Fehler erst als
  `data`-Zeile (`{"error":…}`) im SSE-Stream. Der wurde bisher verschluckt → Stream
  endete ohne Content → stumme leere Bubble. Jetzt werden In-Stream-Fehler erkannt
  und als typisierter `LlmError` (RateLimited / InsufficientQuota / Unauthorized)
  gemeldet; ein Stream ganz ohne Content endet mit einer ehrlichen Leer-Meldung.
  Gilt für den OpenAI-compat-Transport **und** den Anthropic-Adapter
  (`type:"error"`, z.B. `overloaded_error`).
- **OpenRouter:** `HTTP-Referer`- und `X-Title`-Header werden mitgeschickt (von
  OpenRouter empfohlen; ohne sie werden v.a. kostenlose Modelle gedrosselt).
- **OpenRouter `:free` meldete fälschlich „204 Leere Antwort" bei ALLEN Modellen.**
  Das Fehler-JSON von OpenRouter/OpenAI liefert `code` oft als **Zahl** (`"code":404`),
  unser DTO erwartete aber `String` → die Deserialisierung des Chunks kippte → der
  In-Stream-Fehler wurde (erneut) verschluckt → irreführende 204. `OpenAiError.code`
  ist jetzt `JsonElement` (frisst String **und** Zahl); die Meldung ist ehrlich —
  z.B. „Modell nicht (mehr) verfügbar" (viele `:free`-Slugs sind bei OpenRouter
  abgeschaltet) statt „ausgelastet".
- **Modellkatalog markierte tote `:free`-Slugs als gratis.** Die `free`-Erkennung
  vertraute dem `:free`-Namenssuffix — OpenRouter hat aber viele davon abgeschaltet
  (realer Preis > 0). Jetzt zählt **nur der echte Preis** (`pricing.prompt`/`.completion`
  == 0); fehlt der Preis, gilt das Modell fail-closed als *nicht* gratis. Der
  „Nur kostenlose"-Filter zeigt damit nur noch wirklich nutzbare Free-Modelle.
- **In-App-Updater: „Installieren" reagierte nicht.** `PackageInstaller.commit()`
  zeigt bei einer Sideload-App **nicht** direkt den Install-Dialog, sondern sendet
  zuerst `STATUS_PENDING_USER_ACTION` als Broadcast mit dem Dialog-Intent in
  `EXTRA_INTENT`. Es fehlte der Empfänger dafür → der Systemdialog wurde nie
  gestartet, der Button blieb wirkungslos. Jetzt registriert `PackageInstallerSession`
  einen `BroadcastReceiver` (`RECEIVER_NOT_EXPORTED`), startet den Bestätigungs-Intent
  (`FLAG_ACTIVITY_NEW_TASK`) und meldet Erfolg/Fehler über einen `StateFlow` zurück;
  die Updater-UI zeigt jetzt „Installation läuft …" und „Update installiert".

### Tested
- JVM-Unit-Tests: OpenAI-SSE-Transport (MockWebServer), CapabilityRouter,
  SseLineParser, KeyRedactor, SemVer, Sha256Verifier, UpdateChecker.
- Robolectric: `TinkKeyVault`-Roundtrip (put/get/has/remove/clear).
- CI grün: Lint (ktlint/detekt), Tests, `assembleDebug` mit Android SDK.

### Notes
- Distribution: **Sideload-only** (GitHub Releases, kein Play Store).
- BYOK: Provider-Keys bleiben verschlüsselt auf dem Gerät; Calls gehen direkt
  Gerät → Provider, kein Backend.
- Overlay-Bubble, Screen-Reading, Multi-Provider-Adapter (Anthropic), Web-Search
  und Transcription folgen in M3–M6 (siehe ROADMAP.md).
