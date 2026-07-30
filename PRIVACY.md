<!-- CHANGE-MARKER v0.1.0: Initiales Projektgrundgerüst (siehe CHANGELOG.md) -->

# Datenschutzerklärung

OverlAI ist **client-only**. Es gibt **kein Backend**, das von diesem Projekt
betrieben wird, **keine Telemetrie** und **kein Nutzerkonto**.

## Welche Daten verarbeitet werden

- **API-Keys (BYOK):** Die von dir eingegebenen Provider-API-Keys werden
  **ausschließlich lokal** und **verschlüsselt** (Android Keystore/TEE) gespeichert.
  Sie verlassen das Gerät nur als Teil des `Authorization`-Headers deiner eigenen
  Anfragen an den von dir gewählten Provider.
- **Deine Anfragen (Text, Bilder, Audio):** gehen **direkt von deinem Gerät** an den
  gewählten Provider (z. B. OpenAI, Anthropic). Es fließt **nichts** über einen
  Server dieses Projekts. Was der Provider mit den Daten macht, regelt **dessen**
  Datenschutzerklärung — bitte dort prüfen.
- **Chat-Historie:** wird lokal auf dem Gerät gespeichert (Room-Datenbank) und ist
  nicht Teil eines Cloud-Backups (`allowBackup=false`).

## Was NICHT passiert

- Keine Weitergabe an Dritte außer dem von dir gewählten Provider.
- Kein Tracking, keine Analytics, keine Werbe-IDs.
- Kein Klartext-HTTP — nur HTTPS.

## Berechtigungen

Jede angeforderte Berechtigung wird im **Permission Hub** der App erklärt (was, warum).
In Phase 2 kommen sensible Berechtigungen (Overlay, ggf. Screen-Reading, Mikrofon)
hinzu — jeweils **nur nutzerinitiiert** und transparent.

## Datenlöschung

Deinstallation entfernt alle lokalen Daten inkl. der verschlüsselten Keys.
