# SDHelper

**Simple Desktop Helper**: turn your Android phone into a customizable shortcut panel for your PC, over your local network.

> Status: design phase (Phase 1). No code yet. See [`docs/ROADMAP.md`](docs/ROADMAP.md).

## Goals
- Android app with a grid of buttons that trigger actions on your Windows PC (volume, microphone mute, media control, Discord shortcuts, more through plugins).
- Secure by default: LAN only, QR/PIN pairing, encrypted traffic, revocable devices.
- Plugins in any language, maintained in [`SDHelper-Plugins`](https://github.com/RatmuX/SDHelper-Plugins).
- Swappable design languages (Material 3 first, Miuix later) without touching feature code.
- Prepared for Linux, a phone keyboard and a phone touchpad.

## Documentation
[Requirements](docs/REQUIREMENTS.md) · [Architecture](docs/ARCHITECTURE.md) · [Protocol](docs/PROTOCOL.md) · [Plugin spec](docs/PLUGIN_SPEC.md) · [Design system](docs/DESIGN_SYSTEM.md) · [Security](docs/SECURITY.md) · [Plugin repo plan](docs/PLUGIN_REPO_PLAN.md) · [Roadmap](docs/ROADMAP.md) · [Linux porting](docs/PORTING_LINUX.md) · [Decisions](docs/decisions/README.md)

## License
Apache-2.0, see [LICENSE](LICENSE).
