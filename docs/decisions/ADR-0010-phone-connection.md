# ADR-0010: Phone side of the connection

**Context.** Phase 3a connects the Android app to the agent (ADR-0005, ADR-0008). The platform imposes new rules on local network access, and ADR-0004's stack (Compose, Navigation 3, Koin) meets real code for the first time.

**Decisions.**
- **Plain OkHttp 5.5.1 WebSockets** (Apache-2.0), not the Ktor client. One layer fewer; OkHttp is the Android standard. TLS uses `OkHttpClient.Builder.sslSocketFactory(factory, trustManager)` with the pinned trust manager from `core:protocol` and a host name verifier that accepts, because the fingerprint pin is the identity (ADR-0008). HTTP/1.1 only, as WebSocket needs.
- **One Keystore key per paired computer** (EC P-256, signing only, non-exportable), alias stored with the computer. The key is created before the pairing request and deleted again if pairing fails or the computer is forgotten. Different computers never see the same public key.
- **Android 17 local network permission.** For apps targeting SDK 37 the platform blocks all traffic to local network addresses (OkHttp sockets, NsdManager, mDNS) until `ACCESS_LOCAL_NETWORK` is granted; a missing permission shows up as silent TCP time-outs. The app targets 37, declares the permission, and asks for it on the screens that need it (computers, add computer). `LocalNetworkAccess` reports the state; `ConnectionSupervisor` does not connect or scan until it is granted. Below Android 17 the permission does not exist and INTERNET suffices, so access counts as granted. Source: Android developer documentation "Local network permission", read 2026-11-20. The system picker alternative (`FLAG_SHOW_PICKER`) was rejected because the QR and manual-IP flows connect to addresses the app did not discover itself.
- **Connection lifetime.** `ConnectionSupervisor` connects only while the app is in the foreground, a computer is selected and the permission is granted. No foreground service. `DefaultConnectionManager` reconnects with exponential backoff (1 to 30 s, 20% jitter), pings every 5 s, treats 15 s of silence as loss, and stops retrying for certificate mismatch and for a revoked phone, because neither is a network problem. If mDNS reports a paired computer at a new address, the stored address is updated.
- **Discovery** with `NsdManager` (`_sdhelper._tcp`), IPv4 only, resolved one at a time (platform limitation). `CHANGE_WIFI_MULTICAST_STATE` is not declared: the official documentation does not list it for NsdManager. Unverified on a device.
- **Storage:** paired computers as JSON in Preferences DataStore (`data:devices`); a damaged value reads as an empty list.
- **QR scanning:** `zxing-android-embedded` 4.3.0 (Apache-2.0). Its only dependency is zxing core; no Google Play Services, which F-Droid requires. Not yet built with F-Droid tooling.
- **UI structure:** screens depend only on `ui:design-api` (extended with text field, buttons, list item, dialog, status message, progress, text). Navigation 3 and the route keys live in `app:android` so features stay independent; view models come from Koin (`koin-compose-viewmodel`), and a test verifies the whole Koin graph at build time.
- **Tests:** `data:connection` unit tests run the phone code against the real agent (Netty, TLS, ECDSA) with `agent:server` on the test classpath only; the architecture check ignores test configurations.

**Addendum (Phase 3b): actions on the phone.**
- `DefaultConnectionManager` also carries the `deck` channel: it exposes incoming messages and `send` (`DeckTransport`) and sets the active socket *before* it announces `Connected`, so listeners can send the moment they see the state.
- `DefaultDeckActions` asks for the catalog on every new connection, keeps the pushed state, matches `invoke` answers to requests by id (10 s timeout) and clears everything when the connection ends. A message it cannot read is ignored. Titles are picked in the phone's language and fall back to `en`.
- `feature:deck` shows the actions as a plain list (title, live label, On/Off); unavailable actions are dimmed and ignore taps (`DeckListItem(enabled)` in the design contract). The computer's refusal reason is shown as it is, in English; localized error codes come with the plugin system.
- Tested against the real agent with in-memory controllers (8 tests), plus view model tests.

**Alternatives.** Ktor client with the OkHttp engine (extra layer); one shared device key (links computers to each other); a foreground service to stay connected in the background (battery, permission and Play-policy cost without a need in Phase 3a).

**Consequences / not verified.**
- OkHttp with a custom `X509ExtendedTrustManager` is exercised by the JVM tests, and the owner confirmed on a real phone (end of Phase 3a) that QR pairing and the pinned connection work on the Android runtime. The camera needed a fix for its orientation.
- NsdManager discovery has not been confirmed on a device. Keystore access and the permission flow work in the owner's test, but cannot be tested off-device.
- The unmatched case where the app targets 37 but runs on Android 16 or older relies on the permission being absent; this follows the documentation and is untested.
