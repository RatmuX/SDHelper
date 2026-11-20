# ADR-0006: Plugin model

**Context.** Third parties must add actions in any language; a faulty plugin must not crash the agent; plugin UI must follow each design language.

**Decision.** Plugins are separate processes speaking JSON-RPC 2.0 over stdio, described by `plugin.json` with a declarative parameter schema that the Android app renders. Built-in actions implement the same `ActionProvider` interface.

**Alternatives.** In-process JVM plugins (language-locked, crash risk); local WebSocket per plugin (needs ports and extra authentication).

**Consequences.** No real sandbox; permissions are consent plus host-mediated enforcement (`SECURITY.md`). Plugins cannot draw custom UI by design.
