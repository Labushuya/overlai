# Changelog

Alle nennenswerten Änderungen an diesem Projekt werden hier dokumentiert.

Das Format orientiert sich an [Keep a Changelog](https://keepachangelog.com/de/1.1.0/),
die Versionierung an [Semantic Versioning](https://semver.org/lang/de/).
Dieser Changelog wird ab dem ersten Release von `release-please` gepflegt.

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
