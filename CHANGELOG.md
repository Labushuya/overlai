# Changelog

Alle nennenswerten Änderungen an diesem Projekt werden hier dokumentiert.

Das Format orientiert sich an [Keep a Changelog](https://keepachangelog.com/de/1.1.0/),
die Versionierung an [Semantic Versioning](https://semver.org/lang/de/).
Dieser Changelog wird ab dem ersten Release von `release-please` gepflegt.

## [0.2.0](https://github.com/Labushuya/overlai/compare/v0.1.0...v0.2.0) (2026-08-01)


### Features

* **catalog:** Live per-provider Modell-Katalog mit :free-Filter (M-Katalog) ([95b0be9](https://github.com/Labushuya/overlai/commit/95b0be9fc1d58917a355751935edd701aab6be56))
* **m5:** Anthropic-Adapter + app-weite Provider-Auswahl + Gemini-Shim ([0be8146](https://github.com/Labushuya/overlai/commit/0be81467735fa191c341a5f7027c8974e99ab0ac))
* **ux:** Bottom-Navigation + Einstellungs-Hub + Theme/Light-Dark + Updater-UI ([0bf3d04](https://github.com/Labushuya/overlai/commit/0bf3d04bc189fc1cbabb784cc626109c75c6d9cc))


### Bug Fixes

* **core-llm:** In-Stream-Fehler nicht mehr verschlucken (leere Bubble) ([32a090b](https://github.com/Labushuya/overlai/commit/32a090b722e6442cda5ff36f4ba11d6ba02b7db2))
* **core-ui:** getValue-Delegate-Import für by rememberUpdatedState in OnResume ([adbf09b](https://github.com/Labushuya/overlai/commit/adbf09b136f673eb9103d36422c6fa1180de86fd))
* Gemini-Label neutral (kein Kostenversprechen) + Echtzeit-Permission-Status via ON_RESUME ([46d836d](https://github.com/Labushuya/overlai/commit/46d836dd45df3cba522bfd47aedd21b4e4bc31a2))
* **llm,onboarding:** 429 insufficient_quota != Rate-Limit + Provider-Zuordnung im UI ([5d1edda](https://github.com/Labushuya/overlai/commit/5d1eddaf5297b279a8d1d6ec8af2a8942f0266b9))
* **settings:** LazyListScope.item ist Member, kein Import; qualifizierten Typ ersetzt ([d319149](https://github.com/Labushuya/overlai/commit/d319149c4a3feda69224770c19a968089f2d994c))
* **updater:** latest.json über raw.githubusercontent statt github.io (Pages nicht aktiv -&gt; 404) ([b4c1842](https://github.com/Labushuya/overlai/commit/b4c1842230bffdcb36da1ea6287767c61f8d6207))

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
