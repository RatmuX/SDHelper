# ADR-0004: Android stack

**Context.** Keep the stack modern and light; avoid over-engineering.

**Decision.** Jetpack Compose, Navigation 3, ViewModel plus `StateFlow` (unidirectional data flow), Koin for DI (also works in the JVM agent), Room and DataStore for storage, Android Keystore for keys, Android NSD for discovery.

**Alternatives.** Hilt (compile-time safety, Android-only, so the agent would need a second DI); manual DI (simple, but grows noisy); Orbit/MVI frameworks (extra dependency without need).

**Consequences.** Koin errors surface at runtime, so a DI graph verification test is part of CI. F-Droid compatibility means no Google Play Services dependency.
