# SDHelper Architecture

Status: Phase 2 skeleton implemented. Versions are pinned in `gradle/libs.versions.toml` (verified 2026-11-20).

## 1. Overview

```
 Android app (Kotlin, Compose)                Windows PC
 ┌───────────────────────────┐   TLS + WS    ┌────────────────────────────────┐
 │ feature:* (UI, no design  │◄─────────────►│ agent (Kotlin/JVM, Ktor)        │
 │ library imports)          │  JSON, v1     │  ├─ agent:core (ports, routing) │
 │ ui:design-api ◄ m3/miuix  │               │  ├─ plugin host (JSON-RPC/stdio)│
 │ data:* (Room, DataStore)  │               │  └─ tray + settings window      │
 └───────────────────────────┘               │        │ JSON-RPC over stdio    │
        shared: core:model,                  │        ▼                        │
        core:protocol, core:plugin-api       │ native helper (Rust, Windows)   │
                                             │  SMTC, Core Audio, SendInput…   │
                                             └────────────────────────────────┘
```

## 2. Technology decision (ADR-0001)

Hybrid: a Kotlin/JVM agent plus a small Rust native helper for Windows APIs.

| Option | Code sharing with Android | OS access | Footprint | Verdict |
|---|---|---|---|---|
| (a) Kotlin/JVM only (JNA/JNI) | highest | SMTC is WinRT, hard to reach from the JVM; COM via hand-written vtables | ~150 MB RAM | rejected: fragile for SMTC |
| (b) Native only (Rust or Go) | low | direct | small | rejected: protocol and models duplicated |
| (c) Kotlin agent + Rust helper | high for protocol and models | direct, through the official `windows` crate | JVM plus a small exe | **chosen** |

Consequences: two toolchains in CI (Gradle, Cargo); the helper is a separate process, so a crash does not take the agent down. The helper talks JSON-RPC 2.0 over stdio, the same framing plugins use.

Verified: SMTC is exposed through `Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager` (Windows 10 1809+); the Rust `windows` crate exposes it. Microphone mute uses `IAudioEndpointVolume::SetMute` on the capture endpoint. Not verified: runtime behavior on every Windows 11 build (not testable from Linux).

## 3. Modules

```
build-logic/                 convention plugins
core/model                   Action, Button, Page, Folder, Profile, Device (pure Kotlin/JVM)
core/protocol                message types, versions, channels, serialization (Kotlin/JVM)
core/plugin-api              ActionProvider, manifest, parameter schema (Kotlin/JVM)
core/common                  tiny helpers only
ui/design-api                tokens and semantic component contracts (Android library)
ui/design-m3                 Material 3 implementation
ui/design-miuix              Miuix implementation (later, Phase 5)
feature/home                 launcher of surfaces
feature/deck                 control panel
feature/deck-editor          layout, pages, folders, profiles, action editing
feature/connection           discovery, pairing, devices
feature/settings
data/profiles, data/devices  Room, DataStore, repositories
app/android                  composition root; build flavor selects the design module
agent/core                   platform-independent agent logic and port interfaces
agent/platform-windows       ports implemented through the native helper
agent/desktop-app            tray, settings window, packaging
plugins/builtin-*            built-in plugins (system-audio, media, discord)
native/windows               Rust helper
(feature/keyboard, feature/touchpad)   future
```

Dependency rules (enforced, see section 8):
- `feature:*` → `ui:design-api`, `core:*`, `data:*` interfaces. Never `design-m3`, `design-miuix`, `material3` or `miuix`.
- Only `app:android` wires a concrete design module.
- `agent:core` never depends on `agent:platform-*`; `agent:desktop-app` wires them.
- `core:*` depends on nothing platform-specific.

## 4. Agent port interfaces (Linux readiness)

`agent:core` defines these interfaces; Windows implements all of them in v1:
`KeyInjector`, `MediaController`, `AudioController`, `ForegroundWindowProvider`, `AutostartManager`, `SecretStore`.

Adding Linux means a new `agent:platform-linux` module (and `native/linux/` if needed) implementing the same interfaces. Core code is not changed. See `PORTING_LINUX.md`.

## 5. Surfaces (future-proofing)

The home menu lists registered surfaces: `Surface(id, titleRes, icon, route, requiredCapabilities)`.
v1 registers Deck and Deck Editor. Keyboard (v2) and touchpad (v3) are added as a new `feature:*` module plus one registration line, a protocol channel (`input.keyboard`, `input.pointer`) and a matching handler registered on the agent. Existing modules are not edited.

## 6. Actions

An action has an id, a parameter schema, an execute contract and a state stream (title, icon, color, toggled). A button stores only an action reference plus parameters. Toggle state and macros (action chains) are part of the model; macros ship after v1.

Built-in actions run behind the same `ActionProvider` interface as external plugins (external plugins through an adapter). The same contract tests run against both.

### Discord (honest feasibility)
- Discord's RPC `SET_VOICE_SETTINGS` needs the `rpc.voice.write` scope. Per Discord's documentation this is available to approved partners; unapproved apps work only for a limited number of registered testers (secondary sources say 50). Only one RPC app can control voice settings at a time. Re-verify from live docs in Phase 3.
- Default method: simulate the user's own Discord toggle-mute/deafen keybind with `SendInput`. The real state is unknown, so the button shows an assumed state and the docs say so.
- Optional advanced mode: the user registers their own Discord application and adds themselves as a tester, which enables real state through RPC. No shared client id is shipped.
- No user-token or ToS-violating methods.

## 7. Android app

- Jetpack Compose, Navigation 3, ViewModel plus `StateFlow` (unidirectional data flow). No heavier architecture than needed.
- DI: Koin (usable in Android and the JVM agent). Alternatives in ADR-0004.
- Storage: Room for profiles, DataStore for settings, Android Keystore for the device key.
- Discovery: Android NSD (platform API, F-Droid friendly). QR scanning with a library that has no proprietary dependency (ZXing based; to be verified in Phase 2).

## 8. Architecture enforcement

Implemented in `build-logic` (`sdhelper.root` plugin, rules in `architecture/ArchitectureRules.kt`, unit-tested):
1. `verifyDependencies` checks every module's declared dependencies: direction rules per layer, no feature-to-feature dependencies, `agent:core` never depends on `agent:platform-*`, no dependency cycles, and no `material3`/`miuix` artifacts outside `ui:design-*` and `app:*`.
2. `verifyImports` (one task per module) rejects `import androidx.compose.material3...` and `import top.yukonga.miuix...` outside `ui:design-*` and `app:*`.
3. The feature convention plugin (`sdhelper.android-feature`) gives features only `ui:design-api` and `core:model` by default.
All of these run in `./gradlew check` and CI; a violation fails the build.

detekt's `ForbiddenImport` was the original plan, but detekt 1.23.x targets an old Kotlin compiler and 2.x is still alpha (verified 2026-11-20), so a small own check is more reliable with Kotlin 2.4. Style is enforced with Spotless + ktlint.

## 9. Design flavors (ADR-0003)

A Gradle product-flavor dimension `design` with `m3` (default) and `miuix`. Each flavor's source set contains one file choosing the `DesignSystem` provider. Rejected: one branch per design language (merge drift, doubled CI, release complexity). Runtime switching is possible later because features depend only on `design-api`, but it would ship both libraries in one APK and make experimental Miuix everyone's dependency.

## 10. Risks and plan B
See the table in `ROADMAP.md`.
