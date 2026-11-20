# SDHelper

**Simple Desktop Helper**: turn your Android phone into a customizable shortcut panel for your PC, over your local network.

> Status: early development (Phase 3a). You can pair a phone with the agent and keep a secure connection; actions and the button grid arrive next. See [`docs/ROADMAP.md`](docs/ROADMAP.md).

## Goals
- Android app with a grid of buttons that trigger actions on your Windows PC (volume, microphone mute, media control, Discord shortcuts, more through plugins).
- Secure by default: LAN only, QR/PIN pairing, encrypted traffic, revocable devices.
- Plugins in any language, maintained in [`SDHelper-Plugins`](https://github.com/RatmuX/SDHelper-Plugins).
- Swappable design languages (Material 3 first, Miuix later) without touching feature code.
- Prepared for Linux, a phone keyboard and a phone touchpad.

## Build
Requires a JDK 17+ with `javac` (on Fedora: `java-25-openjdk-devel`), the Android SDK (`local.properties` with `sdk.dir`) and nothing else; the Gradle wrapper is included.

```bash
./gradlew check                       # tests, Android lint, ktlint, architecture rules
./gradlew :app:android:assembleM3Debug # debug APK, Material 3 flavor
./gradlew :agent:desktop-app:run       # agent skeleton
```

## Try it (Phase 3a)
1. On the computer: `./gradlew :agent:desktop-app:run` (add `--args="--headless"` for a terminal-only mode). A QR code and PIN appear; they exist only while the window (or terminal) shows them.
2. On the phone: install the debug APK (`adb install app/android/build/outputs/apk/m3/debug/android-m3-debug.apk`), open SDHelper → Computers → Add computer → Scan QR code. Alternatively enter the computer's address and the PIN, then confirm on the computer after comparing the two codes.
3. Phone and computer must be on the same network and the computer's firewall must allow TCP port 47821.
4. Once connected, open **Control Panel**: it turns landscape and shows large square buttons for volume, speakers, microphone and media; swipe sideways for the second page. Use **Edit Shortcuts** to rearrange buttons (turn on *Edit mode*, then press and hold a button and drag it), and **Settings** to switch layouts or add a ready-made one for streaming or gaming. The buttons follow changes made on the computer (a muted microphone turns red). Without Windows, start the agent with `--args="--demo-platform"` (simulated volume, microphone and a three-track player).

### Trying the agent on Windows
Pushes to `main` build a Windows test bundle: GitHub → Actions → the latest run → *Artifacts* → `sdhelper-agent-windows` (kept 14 days, not a release). Unzip it, run `SDHelperAgent.exe` (keep `sdhelper-native.exe` next to it) and pair as above. `SDHelperAgent.exe --demo-platform` simulates volume and media.

## Documentation
[Requirements](docs/REQUIREMENTS.md) · [Architecture](docs/ARCHITECTURE.md) · [Protocol](docs/PROTOCOL.md) · [Plugin spec](docs/PLUGIN_SPEC.md) · [Design system](docs/DESIGN_SYSTEM.md) · [Security](docs/SECURITY.md) · [Plugin repo plan](docs/PLUGIN_REPO_PLAN.md) · [Roadmap](docs/ROADMAP.md) · [Linux porting](docs/PORTING_LINUX.md) · [Decisions](docs/decisions/README.md)

## License
Apache-2.0, see [LICENSE](LICENSE).
