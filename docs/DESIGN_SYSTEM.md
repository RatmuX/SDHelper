# SDHelper Design System Abstraction

Goal: adding a design language means a new design module and a one-line flavor selection, with no changes to feature code.

## 1. Rules
- Feature modules use only `ui:design-api`. They never import `androidx.compose.material3.*` or `top.yukonga.miuix.*` (enforced, see `ARCHITECTURE.md` section 8).
- No hard-coded colors or sizes in features; text comes from string resources.
- Only `app:android` binds a concrete design module, per build flavor (`m3` default, `miuix`).

## 2. What `design-api` contains

**Tokens**: color roles (surface, onSurface, primary, accent for toggled state, danger…), typography roles (title, body, label), shapes, spacing scale. Each design module maps these to its own theme.

**Semantic components** (contracts, not wrappers of one library): `DeckScaffold` (with optional back action), `DeckDrawerShell` (home frame with the navigation drawer: brand, computer, modes with an optional "coming soon" badge that makes an entry inert, settings), `DeckButton` (tonal pill / text action), `DeckTextField`, `DeckListItem`, `DeckDialog`, `DeckModal` (dialog with scrollable content), `DeckStatusMessage` (neutral/positive/warning/error), `DeckProgress`, `DeckText` (title, body, caption, eyebrow, headline, supporting, section), `DeckBackground`, `DeckFeatureCard`, `DeckSettingsGroup` and `DeckSettingsRow` (end: none, chevron, check, badge, switch), `DeckChoiceRow`, `DeckSegmentedChoice`, `DeckPill`, `DeckPopupMenu` with `DeckPopupItem` and `DeckPopupHeading`, `DeckButtonTile` (large square control-panel button with icon, title, live label, tone, dimmed when unavailable, highlight for drop targets, optional number in the editor), `DeckEmptyTile`, `DeckPageIndicator` (dots plus a label such as "Apps · 1 / 2"). The built-in icon set (`DeckIconSet`, `DeckIcon`) lives in the contract module as vector drawables, so every design language draws the same icons in its own colors. Provided through a `CompositionLocal` holding a `DesignSystem` interface.

**Navigation chrome** is part of the contract at the scaffold level (top bar, back action, optional bottom bar). Navigation logic itself (Navigation 3) stays in features.

## 3. Balancing thickness
Too thin (colors only) cannot carry Miuix, whose components and layout conventions differ from M3. Too thick means every screen is written twice. The chosen level: semantic components per UI *role*, not per widget parameter. A design module decides how a `DeckSettingsGroup` or `DeckTopBar` looks. Screens decide only what content they hold.

## 4. M3 and Miuix mapping (to be proven)
This is an analysis, not yet a prototype. I have not verified Miuix's component API (it is marked experimental and changes without notice; latest stable found: 0.9.4, requires Kotlin 2.4 and Compose Multiplatform 1.11, minSdk 24).

| Contract | M3 | Miuix (candidate, unverified) |
|---|---|---|
| DeckScaffold | `Scaffold` | Miuix `Scaffold` |
| DeckTopBar | `TopAppBar` | Miuix `TopAppBar` |
| DeckSwitch | `Switch` | Miuix `Switch` |
| DeckSettingsGroup | `ListItem` group | Miuix preference / card group |
| DeckDialog | `AlertDialog` | Miuix dialog |

**Proof (Phase 5):** run at least one real screen (settings, then the deck grid) with the Miuix module. If a contract is hard to satisfy, the contract changes, not the feature. Phase 2 includes a 1-hour spike to check Miuix version compatibility with Kotlin, Compose and AGP before committing.

## 5. Theming
`DeckTheme(design, darkTheme)`: the app decides light or dark (setting "Theme": follows the phone until the person picks one) and the design module maps it to its palette. The Material You 3 module uses the owner's static indigo palette from the design references (light and dark); dynamic color is not used because the design does not ask for it (it can be added as an option later). Miuix flavor follows its own theming; the token mapping defines the common roles.
