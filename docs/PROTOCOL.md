# SDHelper Protocol (draft v1)

Transport: WebSocket over TLS (`wss`). Messages are UTF-8 JSON text frames.

## 1. Envelope

```json
{ "v": 1, "ch": "deck", "type": "invoke", "id": "c0ffee", "payload": { } }
```

| Field | Meaning |
|---|---|
| `v` | protocol major version |
| `ch` | channel (see below) |
| `type` | message type within the channel |
| `id` | optional correlation id; replies echo it |
| `payload` | type-specific object |

Unknown `type` values on a known channel return an `error` (`unsupported_type`); the connection stays open. Maximum frame size: 64 KiB (larger frames close the connection). Per-connection rate limit: burst of 60 messages, refilled at 30 per second (`error rate_limited`).

## 2. Channels

| Channel | Status | Purpose |
|---|---|---|
| `control` | v1 | `hello`, `pair.*`, `auth.*`, `ping`/`pong`, `error` |
| `deck` | v1 | `catalog` (actions and schemas), `invoke`, `state` (feedback), `icons` |
| `plugin` | v1 | plugin list, settings schemas, permission prompts surfaced to the phone |
| `input.keyboard` | reserved (v2) | key events |
| `input.pointer` | reserved (v3) | relative pointer movement, buttons, scroll |

Continuous low-latency input (pointer) may later use a separate binary or UDP transport. The `hello` message therefore carries a `transports` list so a second transport can be negotiated without breaking v1 clients.

## 3. Connection, pairing and authentication

Endpoint: `wss://<host>:<port>/v1/ws` (default port 47821). TLS certificate: self-signed ECDSA P-256 created by the agent; clients trust it by **SHA-256 fingerprint** only (`PinnedTrustManager`), not by CA or host name.

### 3.1 hello (every connection)
1. Client sends `control/hello` `{ protocolVersions: [1], appVersion, deviceName, capabilities }`. It must be the first message.
2. Agent replies `hello` `{ protocolVersion, agentVersion, agentId, agentName, transports: ["ws"] }` and immediately sends `auth.challenge` `{ nonce }` (32 random bytes, Base64 URL-safe, no padding). If no common version exists: `error unsupported_version` and close.

### 3.2 Authentication of a paired phone
3. Client sends `auth.response` `{ deviceId, signature }`: ECDSA (`SHA256withECDSA`, DER, Base64) with the device key over
   `"SDHelper-auth-v1" 0x00 || nonce || SHA-256(agentCertFingerprintHex)`.
   Binding the signature to the agent's certificate fingerprint prevents a relaying agent from reusing it.
4. Agent verifies against the stored public key and replies `auth.ok`. Any failure: `error unauthenticated` and close. A challenge can be answered once.

### 3.3 Pairing (unpaired phone)
Pairing mode shows an **offer** on the agent: a one-time token (inside the QR code) and a 6-digit PIN. One offer at a time, valid 2 minutes, at most 5 wrong attempts, then it is locked; a new offer invalidates the old one.
- **QR flow.** The QR code carries `sdhelper://pair?v=1&host&port&fp&token&name`. The phone pins `fp`, connects, sends `hello`, then `pair.request` `{ deviceName, publicKey, token }`. The token is the proof; no confirmation at the PC.
- **PIN flow.** The phone does not know the fingerprint, so it accepts the certificate for this handshake only and shows a short code derived from it (first 8 hex characters, `AB12-CD34`). It sends `pair.request` `{ deviceName, publicKey, pin }`. The agent asks the person at the PC to approve and shows the code from its own certificate; the person approves only if both codes match.
- Reply: `pair.accepted` `{ deviceId, agentId, agentName }` (the connection is then authenticated; the phone stores the fingerprint and `deviceId`) or `pair.rejected` `{ reason }` followed by close. Reasons: `no_active_offer`, `expired`, `invalid_credentials`, `locked`, `denied`, `invalid_key`.
- `publicKey` is the EC P-256 public key, X.509 SubjectPublicKeyInfo, Base64; the private key never leaves the Android Keystore.

### 3.4 Unauthenticated connections
Until authenticated, only `hello`, `ping`, `pair.request` and `auth.response` are processed; every other message gets `error unauthenticated`. Five protocol violations (including rate-limit hits) close the connection. Anonymous connections are closed after 15 s idle or 60 s total. A device revoked while connected is disconnected on its next message.

## 4. Deck channel

- `catalog`: list of available actions with parameter schemas and plugin ids (sent after auth and when plugins change).
- `invoke` `{ actionId, params, buttonId }` → reply `result` `{ ok, error? }`. `invoke` is idempotent when the same `id` is replayed within 10 seconds.
- `state` `{ buttonId | actionKey, title?, icon?, color?, toggled? }` pushed by the agent when action state changes (for example microphone muted).

## 5. Reliability

- Heartbeat: the client sends `ping` every 5 seconds and treats the connection as lost after 15 seconds without any message; the agent closes authenticated connections that are idle for 30 seconds.
- The client reconnects with exponential backoff (1 s to 30 s, with jitter) and re-authenticates.
- Rate limit per device on the agent; messages above the limit return `error` `rate_limited`.
- Malformed JSON, oversized frames and unknown channels never crash the agent; repeated violations close the connection.

## 6. Versioning

`v` is a major version. Additive changes (new optional fields, new types) do not bump it. A change that breaks a v1 client bumps `v`; agents may support several versions at once and answer in the highest common one. A compatibility matrix is kept in `PLUGIN_REPO_PLAN.md` for plugin protocol versions.

## 7. Open questions (to settle before implementation)
- Icon transfer: embed small PNGs in `catalog` or fetch over a separate HTTPS endpoint.
- Exact JSON Schema subset for parameter schemas (shared with `PLUGIN_SPEC.md`).
