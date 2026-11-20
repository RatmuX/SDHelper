# ADR-0003: Design languages as build flavors

**Context.** The app should ship in several design languages (Material 3 first, Miuix next) without touching feature code. The owner proposed either an in-app theme selector or one branch per design language and asked for the more practical option.

**Decision.** A Gradle product-flavor dimension `design` (`m3` default, `miuix`). Features depend only on `ui:design-api`; each flavor source set picks one `DesignSystem` provider in `app:android`.

**Alternatives.**
- Branch per design language: merge drift, duplicated CI and release work.
- Runtime selector in one APK: friendly for users, but ships both UI libraries and makes the experimental Miuix a dependency for everyone. Feasible later because of the abstraction.

**Consequences.** One branch, one codebase, separate APK variants (also easy for F-Droid). Adding a design language is a new `ui:design-*` module and one flavor entry. Enforcement rules in `ARCHITECTURE.md`.
