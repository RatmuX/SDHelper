# ADR-0009: Temporary Swing agent interface and a console mode

**Context.** The agent needs a pairing screen (QR, PIN, approval, paired phones) in Phase 3a. Compose Desktop was planned for the agent's UI, but it would add a large dependency and a Kotlin/Compose Multiplatform compatibility risk before the transport is proven.

**Decision.** `agent:desktop-app` ships a small interface on the JDK's own Swing/AWT (window, tray icon where supported, approval dialog) and a console interface (`--headless`, also the automatic choice without a display) that prints the QR code and asks for approval on stdin. Both sit behind `AgentRuntime`, so they can be replaced without touching agent logic. Strings are localized in English and Turkish.

**Alternatives.** Compose Desktop now (revisit after Phase 3c, when the agent window grows); no UI (unusable for pairing).

**Consequences.** No extra dependencies. The look is plain. The console mode makes the agent testable on machines without a display. The tray icon depends on the desktop environment (it is often unavailable on GNOME); without a tray, closing the window quits the agent. The Swing window itself was not exercised by automated tests.
