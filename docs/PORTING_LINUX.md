# Porting the Agent to Linux

The agent is built so that Linux support is a new platform module. Nothing in `agent:core`, `core:*` or the Android app should change.

## 1. What to implement
Create `agent/platform-linux` implementing the port interfaces from `agent:core`:

| Port | Windows (v1) | Linux candidates |
|---|---|---|
| `MediaController` | SMTC | MPRIS over D-Bus |
| `AudioController` | Core Audio | PipeWire / PulseAudio (`pactl`, libpulse) |
| `KeyInjector` | `SendInput` | X11: XTest. Wayland: uinput, libei or the xdg-desktop-portal RemoteDesktop |
| `ForegroundWindowProvider` | Win32 | X11 yes; Wayland generally not available |
| `AutostartManager` | Run key / startup folder | systemd user service or XDG autostart |
| `SecretStore` | DPAPI | Secret Service (libsecret) |

A `native/linux/` helper (Rust, `zbus`) mirrors `native/windows/` if D-Bus is easier outside the JVM. It speaks the same JSON-RPC over stdio.

## 2. Wayland caveats
Wayland restricts input injection and window inspection by design. uinput needs device permissions (udev rule or group membership); the RemoteDesktop portal shows a permission dialog. GNOME is the primary Linux target of the owner. These details are **not verified** and must be researched when the port starts.

## 3. Steps
1. Add the module and register it in `agent:desktop-app` for Linux builds.
2. Implement ports one at a time with the existing contract tests (fake and real).
3. Add packaging (AppImage, deb or Flatpak).
4. Update CI with a Linux job.

## 4. Guard rails
The architecture test forbids `agent:core` from referencing platform modules, so the abstraction cannot erode before the port happens.
