# SDHelper Roadmap

## Phases
| Phase | Content |
|---|---|
| 0 (done) | Requirements |
| 1 (done) | Research and design documents |
| 2 (done) | Skeleton: Gradle, version catalog, convention plugins, CI, lint, architecture tests, "hello" Android app and agent start |
| 3a, 3b (done) | Secure pairing and connection; deck channel, built-in actions (volume, speakers, microphone, media) on Windows through the native helper, DPAPI secrets, plain actions list on the phone. Verified on Windows 11 with a phone |
| 3c (done) | Vertical slice, last part: landscape button grid with state colors and icons, pages, profiles and ready-made layouts, editor with drag and drop, settings. Verified on the owner's phone |
| 3d (cancelled) | Folders were built and then removed on the owner's decision (see ADR-0012); pages and layouts cover the need |
| 3e | Material You 3 design and the new app skeleton from the owner's design references: navigation drawer with modes (Stream Deck, Touch Pad and Keyboard marked "coming soon"), settings list with General (theme), bar-less landscape deck, new editor with the "Hazır ayarlar" menu |
| 4 | Plugin system: manifest, out-of-process host, permission prompt, generated parameter UI, built-ins moved to plugins, one Python example plugin, developer guide |
| 5 | Polish: Discord actions, state feedback everywhere, packaging (APK, Windows installer), README, screenshots, release process. Ends with **v1.0** |

## Versions after that
| Version | Content |
|---|---|
| v1.1 | **Miuix design** from the owner's `miuix` references; the library is added only after the owner approves it; chosen at run time in Settings > General (the design choice moves from a build flavor to a setting, recorded in an ADR) |
| v1.2 | Linux support: `agent:platform-linux` implementing the port interfaces (`PORTING_LINUX.md`) |
| v1.3 | Webcam and what follows, in the owner's order of priority |
| Later | Keyboard surface (`feature:keyboard`, channel `input.keyboard`), Touch Pad surface (`feature:touchpad`, `input.pointer`, low-latency transport), OBS, launch app/URL, Twitch/YouTube, smart home, automatic profile switching |

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
