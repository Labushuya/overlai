# Changelog

Alle nennenswerten Änderungen an diesem Projekt werden hier dokumentiert.

Das Format orientiert sich an [Keep a Changelog](https://keepachangelog.com/de/1.1.0/),
die Versionierung an [Semantic Versioning](https://semver.org/lang/de/).
Dieser Changelog wird ab dem ersten Release von `release-please` gepflegt.

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

### Fixed
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
