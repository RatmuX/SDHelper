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

Unknown `type` values on a known channel return an `error` (`unsupported_type`); the connection stays open. Maximum frame size: 64 KiB (limit reviewed in Phase 3).

## 2. Channels

| Channel | Status | Purpose |
|---|---|---|
| `control` | v1 | `hello`, `pair.*`, `auth.*`, `ping`/`pong`, `error` |
| `deck` | v1 | `catalog` (actions and schemas), `invoke`, `state` (feedback), `icons` |
| `plugin` | v1 | plugin list, settings schemas, permission prompts surfaced to the phone |
| `input.keyboard` | reserved (v2) | key events |
| `input.pointer` | reserved (v3) | relative pointer movement, buttons, scroll |

Continuous low-latency input (pointer) may later use a separate binary or UDP transport. The `hello` message therefore carries a `transports` list so a second transport can be negotiated without breaking v1 clients.

## 3. Handshake

1. Client connects (TLS). In the pairing flow the certificate fingerprint comes from the QR code or PIN flow; otherwise from the stored paired-device record (certificate pinning).
2. Client sends `control/hello` `{ protocolVersions: [1], appVersion, deviceName, capabilities: [...] }`.
3. Agent answers `hello` `{ protocolVersion: 1, agentVersion, capabilities, transports: ["ws"] }` or `error` `unsupported_version`.
4. Paired device: `auth.challenge` / `auth.response` (signature with the device key). Unpaired device: only `pair.*` messages are accepted. Everything else returns `error` `unauthenticated`.

## 4. Deck channel

- `catalog`: list of available actions with parameter schemas and plugin ids (sent after auth and when plugins change).
- `invoke` `{ actionId, params, buttonId }` → reply `result` `{ ok, error? }`. `invoke` is idempotent when the same `id` is replayed within 10 seconds.
- `state` `{ buttonId | actionKey, title?, icon?, color?, toggled? }` pushed by the agent when action state changes (for example microphone muted).

## 5. Reliability

- Heartbeat: `ping`/`pong` every 5 seconds, connection considered lost after 15 seconds.
- The client reconnects with exponential backoff (1 s to 30 s, with jitter) and re-authenticates.
- Rate limit per device on the agent; messages above the limit return `error` `rate_limited`.
- Malformed JSON, oversized frames and unknown channels never crash the agent; repeated violations close the connection.

## 6. Versioning

`v` is a major version. Additive changes (new optional fields, new types) do not bump it. A change that breaks a v1 client bumps `v`; agents may support several versions at once and answer in the highest common one. A compatibility matrix is kept in `PLUGIN_REPO_PLAN.md` for plugin protocol versions.

## 7. Open questions (to settle before implementation)
- Icon transfer: embed small PNGs in `catalog` or fetch over a separate HTTPS endpoint.
- Exact JSON Schema subset for parameter schemas (shared with `PLUGIN_SPEC.md`).
