<!-- CHANGE-MARKER v0.1.0: Initiales Projektgrundgerüst (siehe CHANGELOG.md) -->

# OverlAI

[![CI](https://github.com/Labushuya/overlai/actions/workflows/ci.yml/badge.svg)](https://github.com/Labushuya/overlai/actions/workflows/ci.yml)
[![CodeQL](https://github.com/Labushuya/overlai/actions/workflows/codeql.yml/badge.svg)](https://github.com/Labushuya/overlai/actions/workflows/codeql.yml)
[![Release](https://img.shields.io/github/v/release/Labushuya/overlai?sort=semver)](https://github.com/Labushuya/overlai/releases/latest)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![min SDK 26](https://img.shields.io/badge/min%20SDK-26-3DDC84?logo=android&logoColor=white)](#)
[![target SDK 36](https://img.shields.io/badge/target%20SDK-36-3DDC84?logo=android&logoColor=white)](#)
[![Kotlin 2.1](https://img.shields.io/badge/Kotlin-2.1-7F52FF?logo=kotlin&logoColor=white)](#)
[![Distribution: Sideload](https://img.shields.io/badge/distribution-sideload%20only-orange)](#installation)
[![BYOK](https://img.shields.io/badge/keys-never%20leave%20device-success)](#datenschutz--sicherheit)

> **OverlAI** — ein AI-Assistent, der **über** deiner gerade geöffneten App liegt,
> statt dich zum App-Wechsel zu zwingen. Aufrufbar per Text-Selektion, Share-Sheet
> und (ab Phase 2) schwebender Bubble. Du bringst deinen eigenen API-Key mit
> (Bring-Your-Own-Key) — Anfragen gehen **direkt** von deinem Gerät zum Provider,
> ohne Zwischenserver.

---

## Idee

Man wechselt ständig die App, nur um etwas nachzuschlagen: ein Wort übersetzen, eine
Aussage zusammenfassen, ein Bild verstehen. OverlAI holt den Assistenten dorthin, wo
du gerade bist:

- **Übersetzen** eines markierten Wortes
- **Zusammenfassen** von Text/Artikeln
- **Bildinhalte analysieren** (Vision) + **OCR** (Text aus Bildern)
- **Web-Suche** (bei Providern mit Grounding)
- kurze **Transcription** (Sprache → Text)

## Prinzip: Bring-Your-Own-Key (BYOK)

Du trägst deinen API-Key eines LLM-Providers ein. OverlAI speichert ihn
**verschlüsselt im Android Keystore** und schickt deine Anfragen direkt an den
Provider. **Kein Backend, keine Telemetrie, kein Proxy.** Unterstützt (im Ausbau):

| Provider | Chat | Vision | Web-Suche | Transcription |
|---|:--:|:--:|:--:|:--:|
| OpenAI | ✅ | ✅ | ⚠️ (modellabh.) | ✅ Whisper |
| Anthropic (Claude) | ✅ | ✅ | ✅ | — |
| xAI Grok | ✅ | ✅ | ✅ | ⚠️ |
| Google Gemini | ✅ | ✅ | ✅ | ⚠️ |
| DeepSeek | ✅ | ⚠️ | ➕ ext. Key | — |
| Moonshot Kimi | ✅ | ✅ | ⚠️ | — |
| OpenRouter | ✅ | ⚠️ (modellabh.) | ⚠️ | — |

Legende: ✅ nativ · ⚠️ konditional · ➕ externer Such-Key nötig · — nicht verfügbar.
Features, die der gewählte Provider nicht kann, werden in der UI **ausgegraut** —
nie stillschweigend ignoriert.

## Installation

OverlAI wird **ausschließlich als Sideload-APK** über
[GitHub Releases](https://github.com/Labushuya/overlai/releases/latest) verteilt
(**kein Play Store**). Grund: die Kernfunktionen (Overlay, Screen-Reading,
In-App-Updater) sind mit den Play-Store-Richtlinien nicht vereinbar.

1. Neueste `overlai-vX.Y.Z.apk` herunterladen.
2. Öffnen; „Installation aus unbekannten Quellen" für die installierende App erlauben.
3. Der **In-App-Updater** hält OverlAI danach aktuell. Hinweis: Bei jedem Update
   erscheint der **System-Install-Dialog** (2–3 Taps) — echtes „Silent-Update" ist
   für sideloaded Apps technisch nicht möglich.

## Architektur (Kurzüberblick)

Native **Kotlin + Jetpack Compose (Material 3)**, Multi-Modul-Gradle:

```
:app                     Application, Navigation, Manifest, Release-Signing
:core:core-llm           Provider-Abstraktion (OpenAI-compat Core + Adapter), rein JVM
:core:core-security      BYOK-Key-Storage (Android Keystore/TEE via Tink)
:core:core-data          DataStore + Room (Chat-Historie, Provider-Config)
:core:core-ui            Material-3-Theme, geteilte Compose-Komponenten
:core:core-common        Result, SemVer, Redacting-Logger
:feature:feature-*        onboarding · chat · permissions · share · ocr · updater
```

Siehe [ROADMAP.md](ROADMAP.md) für die Phasen M0–M6.

## Entwicklung

Alles läuft über GitHub Actions — **lokal muss nichts gehostet werden**. Für lokale
Builds (optional) genügt ein Android SDK + JDK 21:

```bash
./gradlew ktlintCheck detekt      # Lint-Gate
./gradlew testDebugUnitTest test  # Unit-Tests (JVM-Kern ohne Emulator)
./gradlew :app:assembleDebug      # Debug-APK
```

## Datenschutz & Sicherheit

- **Keys verlassen nie das Gerät** (Keystore/TEE, `allowBackup=false`).
- **Nur HTTPS** zu den Provider-Endpunkten (Cert-Pinning).
- **Keine Telemetrie, kein Konto.**

Details: [PRIVACY.md](PRIVACY.md) · [SECURITY.md](SECURITY.md).

## Lizenz

[MIT](LICENSE).
