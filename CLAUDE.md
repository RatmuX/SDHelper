# SDHelper: rules for contributors and AI assistants

SDHelper = Android shortcut panel + Windows agent (Linux later). Read `docs/ARCHITECTURE.md` before changing structure.

## Language
Talk to the owner in Turkish. Code, comments, commits, READMEs and docs in English.

## Hard rules
- `feature:*` must NOT import `androidx.compose.material3.*` or `top.yukonga.miuix.*`, and must not depend on `ui:design-m3` / `ui:design-miuix`. Use `ui:design-api`. Only `app:android` binds a design module (build flavor `design`: `m3`, later `miuix`). `./gradlew check` enforces this.
- `agent:core` must NOT depend on `agent:platform-*`. OS access goes through the port interfaces.
- Built-in actions use the same `ActionProvider` interface as plugins.
- Protocol changes: update `docs/PROTOCOL.md` and keep `v` semantics.
- No hard-coded colors, sizes or user-visible strings in features.
- Never invent versions or APIs: verify on Maven Central or official docs. State what you could not test (Windows code cannot be tested on the Linux dev machine).
- Never commit secrets or `local.properties`. Commits only when the owner asks; Conventional Commits.
- Record decisions as ADRs in `docs/decisions/`.

## Commands
Gradle needs a full **JDK** (with `javac`), 17 or newer; the build targets Java 17 bytecode whatever JDK runs it. Developed with JDK 25. On Fedora install `java-25-openjdk-devel` (the plain `java-25-openjdk` package is JRE-only). If several JDKs are installed, point `JAVA_HOME` at the one to use.
- Everything (tests, Android lint, style, architecture rules): `./gradlew check`
- Format code: `./gradlew spotlessApply`
- Architecture rules only: `./gradlew verifyDependencies`
- Build-logic tests: `./gradlew -p build-logic test`
- Android debug APK (Material 3 flavor): `./gradlew :app:android:assembleM3Debug`
- Run the agent skeleton: `./gradlew :agent:desktop-app:run`
- `local.properties` needs `sdk.dir=<Android SDK path>`; the build needs platform `android-37.1` (AGP downloads it if licenses are accepted).

## Layout
`build-logic/` convention plugins and architecture rules; `core/*` pure Kotlin/JVM; `ui/*` design contract and implementations; `feature/*` screens; `data/*` storage; `app/android` composition root; `agent/*` desktop agent.

## Phases
The owner approves each phase before the next starts. Done: 0 (requirements), 1 (design docs), 2 (skeleton). Next: 3 (vertical slice).
