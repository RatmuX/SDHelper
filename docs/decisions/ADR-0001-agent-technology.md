# ADR-0001: Agent technology

**Context.** The Windows agent needs SMTC (WinRT), Core Audio (COM), `SendInput`, and must share protocol code with the Android app. Linux support comes later.

**Decision.** Kotlin/JVM agent (Ktor, Compose Desktop tray) plus a Rust native helper (`native/windows`) using the `windows` crate, connected by JSON-RPC 2.0 over stdio.

**Alternatives.**
- Kotlin/JVM only with JNA: shares everything, but SMTC from the JVM is impractical and COM vtables must be written by hand.
- Rust or Go only: small and close to the OS, but protocol and models would be duplicated and not shared with Android.

**Consequences.** Two toolchains in CI; the helper is isolated from the agent; a JVM is bundled in the installer (jlink/jpackage). The helper can be swapped behind the port interfaces. Linux gets its own platform module.
