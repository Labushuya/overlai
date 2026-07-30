<!-- CHANGE-MARKER v0.1.0: Initiale Bedrohungsanalyse + Meldeweg. Siehe CHANGELOG.md -->

# Security Policy

OverlAI ist **client-only + BYOK**: keine Telemetrie, kein Konto, kein Backend,
kein Klartext-HTTP. API-Keys liegen verschlüsselt im Android Keystore und verlassen
das Gerät nie. Dieses Dokument fasst die Bedrohungsanalyse kompakt zusammen und
beschreibt den Meldeweg.

> **Status-Legende:** `implementiert` · `teilweise` · `geplant` · `blockiert` ·
> `manuell zu testen` · `automatisch getestet`.
>
> Zum Zeitpunkt v0.1.0 sind viele Gegenmaßnahmen noch `geplant`/`teilweise`, weil
> die zugehörigen Phasen (M1–M6) noch nicht abgeschlossen sind. Diese Datei ist
> eine **Design-Vorgabe**, kein Nachweis fertiger Härtung.

## Unterstützte Versionen

| Version | Sicherheitsupdates |
|---|---|
| 0.1.x (pre-release) | in Entwicklung, keine Garantie |

## Bedrohungsanalyse (Kurzfassung)

| # | Bedrohung | Angriffsbild | Gegenmaßnahme | Status |
|---|---|---|---|---|
| T1 | **BYOK-Key-Leak** | API-Key wird aus SharedPrefs/Backup/Logs ausgelesen | Speicherung via Tink-AEAD mit Master-Key im Android Keystore/TEE (StrongBox wo verfügbar); `allowBackup=false` + Extraction-Rules; Redacting-Logger (Keys nie im Log) | `geplant` (M1) |
| T2 | **MITM auf Provider-Calls** | Angreifer fängt Device→Provider-HTTPS ab, stiehlt Key/Antwort | Ausschließlich HTTPS; Cert-Pinning auf die konfigurierten Provider-Hosts; keine benutzerdefinierten CAs vertrauen | `geplant` (M1/M5) |
| T3 | **Manipuliertes `latest.json`** | Gefälschtes Update-Manifest bietet schädliche/falsche APK an | Nur HTTPS (gepinnt) zu gh-pages; `sha256` der APK VOR Installation verifizieren; keine Auto-Installation ohne Nutzerbestätigung (OS-Dialog) | `geplant` (M1/M2) |
| T4 | **Downgrade-Angriff** | Nutzer wird auf ältere, verwundbare Version gelenkt | Update nur vorwärts nach SemVer; Downgrade wird abgelehnt | `geplant` (M1) |
| T5 | **Fremde/manipulierte APK** | Nutzer installiert präparierte APK aus inoffizieller Quelle | Ausschließlich signierte APKs von der offiziellen Releases-Seite; **stabile Signatur** über alle Releases (§Signing); README-Hinweis | `geplant` (M0) |
| T6 | **Signatur-Inkompatibilität** | Wechselnder Signing-Key → `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, Updater dauerhaft kaputt | Signing-Key lebt nur als GitHub-Secret (`ANDROID_KEYSTORE_BASE64`), wird für JEDES Release wiederverwendet; Release-Build bricht ohne Key hart ab | `implementiert` (M0) |
| T7 | **Supply-Chain** | Kompromittierte Dependency oder GitHub Action schleust Schadcode ein | Third-Party-Actions auf **Commit-SHA** gepinnt (nicht nur Tag); minimale Workflow-`permissions`; Dependabot; Version-Catalog als Single-Source | `implementiert` (M0) |
| T8 | **Secret-Leak in CI** | Signing-Key/Token gelangt in Logs oder untrusted-PR-Kontext | Kein Token in der App; Secrets nur als GitHub Secrets; base64-Keystore nie geloggt; `.gitignore` für `*.jks`/`keystore.properties` | `implementiert` (M0) |
| T9 | **Malware-Profil-Verwechslung** | Sideload + (Phase 2) AccessibilityService + Self-Updater = klassisches Malware-Muster; Nutzer wird zu riskanten Grants trainiert | Transparentes Trust-UX (Permission Hub erklärt jede Berechtigung + warum); Accessibility nur user-initiiert, kein Dauerstrom; Open-Source-Code als Vertrauensanker | `geplant` (M2) |
| T10 | **Prompt-Injection über geteilten Inhalt** | Geteilter Text/OCR-Inhalt enthält Anweisungen, die den Assistenten manipulieren | Geteilte Inhalte als Daten behandeln (System-Prompt-Trennung); keine automatische Ausführung von Aktionen aus Inhalten | `geplant` (M2/M6) |

## Signing (load-bearing)

Der Release-Signing-Key **muss über alle Versionen stabil bleiben** — sonst
scheitern Updates mit `INSTALL_FAILED_UPDATE_INCOMPATIBLE` (Recovery nur via
Deinstallation = Datenverlust). Er wird ausschließlich als base64-GitHub-Secret
gehalten und in `.github/workflows/release.yml` zur Build-Zeit rekonstruiert,
nie geloggt, nie im Repo.

## Grundsätze

- **Kein Klartext-HTTP.** Nur HTTPS zu Provider-Endpunkten + gh-pages.
- **Kein eingebettetes Token/Key** in der App (der Nutzer bringt seinen eigenen).
- **Least Privilege** in CI: minimale `permissions` je Workflow.

## Eine Schwachstelle melden

Bitte **keine öffentlichen Issues** für Sicherheitslücken.

1. Bevorzugt: **GitHub Security Advisory** über *Security → Report a vulnerability*.
2. Falls nicht verfügbar: Kontakt über das Repo-Owner-Profil, Betreff mit `SECURITY:`.

Bitte angeben: betroffene Version, Reproduktionsschritte, erwartetes vs. tatsächliches
Verhalten und — falls vorhanden — ein minimales Beispiel. Es gibt für dieses Projekt
keine zugesicherten Reaktionszeiten (SLA).
