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
- Run the agent: `./gradlew :agent:desktop-app:run` (window), add `--args="--headless"` for the terminal mode that prints the pairing QR code; `--port N` changes the port. State lives in `~/.config/sdhelper` (Linux) or `%APPDATA%\SDHelper`; set `SDHELPER_CONFIG_DIR` to use another folder.
- Native helper (`native/windows`, needs the `rust`, `cargo`, `clippy`, `rustfmt` packages): `cargo fmt && cargo clippy --all-targets -- -D warnings && cargo test` in that folder; type-check the Windows code from Linux with `cargo check --target x86_64-pc-windows-gnu --all-targets` (needs `rust-std-static-x86_64-pc-windows-gnu`). Set `SDHELPER_NATIVE_PATH` to a built helper to let the agent tests start the real process.
- `local.properties` needs `sdk.dir=<Android SDK path>`; the build needs platform `android-37.1` (AGP downloads it if licenses are accepted).

## Layout
`build-logic/` convention plugins and architecture rules; `core/*` pure Kotlin/JVM; `ui/*` design contract and implementations; `feature/*` screens; `data/*` storage; `app/android` composition root; `agent/*` desktop agent (`agent:server` is the Ktor transport). Tests of the phone side run against the real agent.

## Phases
The owner approves each phase before the next starts. Done: 0 (requirements), 1 (design docs), 2 (skeleton), 3a (secure connection and pairing), 3b (deck channel, built-in actions, Windows helper, DPAPI, phone actions list). 3c (landscape button grid, pages, layouts and presets, editor with drag and drop, settings) was verified on the owner's phone. 3d (folders) is built and awaits the owner's phone test. Next: Phase 4 (plugin system); the final visual design (colors, layout, look) is done in Phase 5 from the owner's HTML mock-ups.
