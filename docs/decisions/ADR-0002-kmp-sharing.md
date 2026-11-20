# ADR-0002: Share core code with Kotlin Multiplatform

**Context.** Android and the JVM agent must agree on messages, models and plugin interfaces.

**Decision.** `core:model`, `core:protocol` and `core:plugin-api` are Kotlin Multiplatform modules with Android and JVM targets, using kotlinx.serialization. The UI is not shared: the Android app uses Jetpack Compose (AndroidX); the agent's small desktop UI uses Compose Multiplatform.

**Alternatives.** Duplicate models in each project (drift risk); a schema-first approach with generated code (extra tooling for a small protocol).

**Consequences.** One source of truth for the protocol. Kotlin and Compose Multiplatform versions must stay compatible (research: Kotlin 2.4.x, Compose Multiplatform 1.11.x; re-verify in Phase 2).
