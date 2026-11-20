# ADR-0002: Share core code as plain Kotlin/JVM libraries

**Context.** Android and the JVM agent must agree on messages, models and plugin interfaces. Phase 1 proposed Kotlin Multiplatform (KMP) for this.

**Decision (revised in Phase 2).** `core:model`, `core:protocol` and `core:plugin-api` are plain Kotlin/JVM libraries (JVM target 17) using kotlinx.serialization. Both consumers, the Android app and the desktop agent, run on the JVM, so KMP adds no reach today but adds cost: with AGP 9 a KMP module needs the separate `com.android.kotlin.multiplatform.library` plugin and a larger build matrix. The UI is not shared: the Android app uses Jetpack Compose (AndroidX); the agent's small desktop UI will use Compose Multiplatform.

**Alternatives.** Kotlin Multiplatform with Android and JVM targets (revisit if a non-JVM client such as iOS or web appears; the modules have no JVM-specific code beyond the serialization plugin, so migration is mechanical); duplicated models (drift risk).

**Consequences.** Simple builds and fast CI. One source of truth for the protocol. Changing to KMP later touches only these three modules' build files.
