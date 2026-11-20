# SDHelper Roadmap

## Phases
| Phase | Content |
|---|---|
| 0 | Requirements (done) |
| 1 | Research and design documents (this) |
| 2 | Skeleton: Gradle, version catalog, convention plugins, CI, lint, architecture tests, "hello" Android app and agent start |
| 3 | Vertical slice: home menu, pairing, connection, deck grid, 2-3 real actions (system volume, mic mute, media), basic editor |
| 4 | Plugin system: manifest, out-of-process host, permission prompt, generated parameter UI, built-ins moved to plugins, one Python example plugin, developer guide |
| 5 | Polish: Discord actions, state feedback everywhere, packaging (APK, Windows installer), Miuix flavor proof, README, screenshots, release process |

## Versions after that
| Version | Content |
|---|---|
| Linux support | `agent:platform-linux` implementing the port interfaces (`PORTING_LINUX.md`) |
| v2 | Keyboard surface: new `feature:keyboard`, protocol channel `input.keyboard`, agent handler |
| v3 | Touchpad surface: `feature:touchpad`, `input.pointer`, low-latency transport |
| Also later | OBS, launch app/URL, Twitch/YouTube, smart home, automatic profile switching |

## Risks and plan B
| Risk | Plan B |
|---|---|
| Discord RPC restricted or changed | Keybind simulation is the default already |
| Miuix is experimental, API may break | Pin version; Miuix flavor is optional with its own CI job; freeze if needed |
| Rust plus Kotlin toolchain complexity | Ports hide the helper; it could be replaced with a JNA implementation |
| JVM memory footprint | jlink minimal runtime; consider native-image later |
| Some players do not publish SMTC sessions | Media key fallback (`VK_MEDIA_*`) |
| F-Droid rules (no proprietary dependencies) | ZXing-based QR scan, platform NSD |
| Windows cannot be tested from the dev Linux machine | CI on `windows-latest`; manual tests by the owner on Windows 11; mark untested code explicitly |
| Future Wayland limitations | See `PORTING_LINUX.md` |
