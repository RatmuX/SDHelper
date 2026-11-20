# SDHelper Requirements

SDHelper ("Simple Desktop Helper") turns an Android phone into a shortcut panel for a Windows PC over the local network.

Repository: `github.com/RatmuX/SDHelper`. Plugins live in a separate repository, `github.com/RatmuX/SDHelper-Plugins`.

## 1. Scope

| Item | v1 | Later |
|---|---|---|
| Android client (Kotlin, Compose, min SDK 26) | yes | |
| Windows 11 desktop agent | yes | |
| Linux desktop agent | no (architecture ready, see `PORTING_LINUX.md`) | after v1 |
| Keyboard surface (phone as keyboard) | no (reserved in protocol) | v2 |
| Touchpad surface (phone as pointer) | no (reserved in protocol) | v3 |
| Google Play distribution | no | not planned |

Distribution: GitHub Releases and F-Droid. This means no proprietary Google dependencies in the Android app.

## 2. Functional requirements (v1)

### 2.1 App flow
- The app opens on a data-driven home menu (surface registry, see `ARCHITECTURE.md`).
- v1 entries: **Control Panel** (use the shortcut grid) and **Edit Shortcuts** (grid, pages, folders, profiles, actions).
- A separate connection screen handles discovery, pairing and device management.
- UI strings avoid the trademarked term "Stream Deck".

### 2.2 Built-in actions
Built-in actions are implemented as built-in plugins behind the same interface third-party plugins use.
- System audio: master volume up/down/mute, microphone mute.
- Media (Spotify and any SMTC-capable player): play/pause, next, previous, with state feedback.
- Discord: microphone mute and deafen (method decided in `ARCHITECTURE.md` section Discord).
- Later (Phase 5 and beyond): OBS (obs-websocket), launch app/window and open URL, Twitch/YouTube, smart home, automatic profile switching by foreground app.

### 2.3 Buttons and layout
- Two-way state feedback: a toggle action changes the button icon, color and title.
- Customizable grid (default 3x5), portrait and landscape, tablet layouts.
- Pages, folders (a button opens a sub-page) and profiles (independent layouts).
- Icons: built-in icon set plus custom image.
- Haptic feedback and keep-screen-on while the control panel is open.

### 2.4 Connection and security
- LAN only. Discovery with mDNS (Android NSD) plus manual IP entry.
- Pairing with QR code (primary) and PIN (fallback).
- No command is accepted from an unpaired device. Per-device keys, revocation on the agent. Details in `SECURITY.md`.
- Reconnect with backoff, multiple computer profiles. The app connects only while it is in the foreground.
- Android 17+ requires the `ACCESS_LOCAL_NETWORK` runtime permission for any local network access; the app requests it where needed (ADR-0010).

### 2.5 Design languages
- Default: Material Design 3. Second: Miuix, as an extra build flavor.
- Feature code never imports Material3 or Miuix directly. See `DESIGN_SYSTEM.md`.

### 2.6 Plugins
- Third parties can add actions in any language through out-of-process plugins. See `PLUGIN_SPEC.md`.
- Plugins declare their settings schema; the Android app renders it with the active design system.

## 3. Non-functional requirements
- Clean, small, single-responsibility modules with one-way dependencies. The codebase doubles as a portfolio piece.
- Accessibility (TalkBack, large fonts), light/dark theme, dynamic color on Android 12+ (static palette below).
- UI languages: Turkish and English. Repository text (code, comments, commits, docs): English.
- License: Apache-2.0.
- Unit tests for core and protocol, fake-based tests for actions, Compose tests where practical.

## 4. Out of scope for v1
Linux agent, keyboard/touchpad surfaces, internet or VPN access, USB/ADB connection, OBS and other integrations from the "Later" list, Google Play.

## 5. Open items
- Verify the `SDHelper` name is free on GitHub, F-Droid and package namespaces before the first release.
- Discord RPC availability must be re-verified from live documentation in Phase 3.
