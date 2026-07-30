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
- Entry-Point-Stubs: `ACTION_PROCESS_TEXT`-Activity + Share-Target-Activity.

### Notes
- Distribution: **Sideload-only** (GitHub Releases, kein Play Store).
- BYOK: Provider-Keys bleiben verschlüsselt auf dem Gerät; Calls gehen direkt
  Gerät → Provider, kein Backend.
