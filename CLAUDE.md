# SDHelper: rules for contributors and AI assistants

SDHelper = Android shortcut panel + Windows agent (Linux later). Read `docs/ARCHITECTURE.md` before changing structure.

## Language
Talk to the owner in Turkish. Code, comments, commits, READMEs and docs in English.

## Hard rules
- `feature:*` must NOT import `androidx.compose.material3.*` or `top.yukonga.miuix.*`, and must not depend on `ui:design-m3` / `ui:design-miuix`. Use `ui:design-api`. Only `app:android` binds a design module (build flavor `design`: `m3`, `miuix`).
- `agent:core` must NOT depend on `agent:platform-*`. OS access goes through the port interfaces.
- Built-in actions use the same `ActionProvider` interface as plugins.
- Protocol changes: update `docs/PROTOCOL.md` and keep `v` semantics.
- No hard-coded colors, sizes or user-visible strings in features.
- Never invent versions or APIs: verify on Maven Central or official docs. State what you could not test (Windows code cannot be tested on the Linux dev machine).
- Never commit secrets or `local.properties`. Commits only when the owner asks; Conventional Commits.
- Record decisions as ADRs in `docs/decisions/`.

## Commands
Filled in during Phase 2 (Gradle build, tests, detekt/ktlint, architecture tests, Cargo for `native/windows`).

## Phases
The owner approves each phase before the next starts. Current: Phase 1 (documents only, no code).
