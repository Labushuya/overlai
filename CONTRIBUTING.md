<!-- CHANGE-MARKER v0.1.0: Initiales Projektgrundgerüst (siehe CHANGELOG.md) -->

# Contributing

Danke für dein Interesse an OverlAI!

## Commit-Konvention

Dieses Repo nutzt **[Conventional Commits](https://www.conventionalcommits.org/)**.
`release-please` erzeugt daraus automatisch Version-Bumps und den CHANGELOG.

```
feat(chat): Streaming-Antworten im Chat-Screen
fix(updater): sha256-Prüfung vor Installation
docs(readme): Provider-Matrix ergänzt
ci: setup-android auf SHA gepinnt
chore(deps): OkHttp 4.12.0
```

Typen: `feat`, `fix`, `docs`, `ci`, `chore`, `refactor`, `test`, `perf`, `build`.
`feat!:` oder ein `BREAKING CHANGE:`-Footer lösen einen Major-Bump aus.

## Qualitäts-Gates (müssen grün sein)

```bash
./gradlew ktlintCheck detekt       # Style + statische Analyse
./gradlew testDebugUnitTest test    # Unit-Tests
./gradlew :app:assembleDebug        # Build
```

Die CI (`.github/workflows/ci.yml`) fährt dieselben Gates plus CodeQL.

## Architektur-Regeln

- `feature-*`-Module hängen nur an `core-*`, **nie** an einem anderen `feature-*`.
- `:core-llm` bleibt **reines Kotlin/JVM** (keine Android-UI-Deps) — testbar ohne
  Emulator via MockWebServer.
- Provider-Fähigkeiten werden über `Capability` gemodellt; Features **ausgrauen**
  statt still no-op'en.
- **Keine Secrets** committen (`*.jks`, `keystore.properties` sind ignoriert).

## Branching

Feature-Branches → PR gegen `main`. `main` ist geschützt (CI muss grün sein).
