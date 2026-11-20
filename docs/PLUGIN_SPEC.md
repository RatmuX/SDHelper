# SDHelper Plugin Specification (draft v1)

Plugins add actions to SDHelper. They run **out of process**, in any language, and talk to the agent with JSON-RPC 2.0 over stdio. Built-in actions use the same `ActionProvider` interface (through the same contract), so the system tests itself.

## 1. Layout

```
my-plugin/
  plugin.json
  icons/
  main.py | main.js | my-plugin.jar | my-plugin.exe
```

## 1a. The action provider interface (in-process, Phase 3b)

Built-in actions already implement this interface (`core:plugin-api`); external plugins will be adapted to it in Phase 4:

```kotlin
interface ActionProvider {
    val providerId: String
    fun actions(): List<ActionDescriptor>      // id, localized title, icon key, toggle
    val states: Flow<Map<String, ActionState>> // current state first, then every change
    suspend fun invoke(actionId: String, params: Map<String, String>): ActionResult
}
```
`ActionState(available, toggled?, label?, tone)` carries what a button needs to look right; `tone` is a meaning (`NORMAL`, `ACTIVE`, `WARNING`, `ALERT`) that each design language maps to its own colors, so plugins never choose colors. The agent publishes actions as `providerId/actionId` (see `PROTOCOL.md` section 4).

## 2. Manifest (`plugin.json`)

```json
{
  "id": "com.example.hello",
  "name": "Hello",
  "version": "1.0.0",
  "author": "Jane Doe",
  "license": "MIT",
  "platforms": ["windows"],
  "minProtocol": 1,
  "entry": { "command": "python", "args": ["main.py"] },
  "permissions": ["keys.inject"],
  "actions": [
    {
      "id": "greet",
      "title": { "en": "Say hello", "tr": "Merhaba de" },
      "icon": "icons/greet.png",
      "toggle": false,
      "params": [
        { "key": "text", "type": "string", "title": { "en": "Text" }, "default": "Hello" }
      ]
    }
  ]
}
```

Parameter types in v1: `string`, `number`, `bool`, `enum`, `keybind`, `file`, `color`. The Android app renders them with the active design system, so plugins look native in every design flavor and never ship UI code.

A JSON Schema for the manifest lives in the `SDHelper-Plugins` repository (`spec/`), with a validator used in CI. The main repository keeps a reference copy.

## 3. Permissions (initial set)

| Permission | Allows |
|---|---|
| `keys.inject` | send key presses through the host |
| `process.run` | start processes |
| `net` | outbound network access |
| `fs.read`, `fs.write` | file access |
| `audio.control` | system audio functions via the host API |
| `media.control` | media session control via the host API |

The user sees and approves permissions at install. See `SECURITY.md` for what is and is not enforced.

## 4. Protocol between agent and plugin

Methods (agent → plugin): `initialize`, `action.invoke`, `action.stateSubscribe`, `shutdown`.
Notifications (plugin → agent): `action.state` (title, icon, color, toggled), `log`.
Host API (plugin → agent, permission checked): `host.keys.inject`, `host.audio.*`, `host.media.*`.

Lifecycle: the agent starts the process, sends `initialize` with the protocol version, the plugin answers with capabilities. A crashed plugin is restarted with backoff and its actions are marked unavailable in the meantime. stdout is reserved for JSON-RPC; plugins write logs to stderr or `log` notifications.

## 5. Developer experience
- Developer mode: hot reload on file change, plugin log viewer in the desktop app.
- SDKs: Kotlin first, then Python and Node (thin wrappers around the stdio protocol). Planned in `SDHelper-Plugins`.
- Install, update and remove from the desktop app; in-app catalog later.

## 6. Existing ecosystems
Elgato Stream Deck SDK plugins and OpenDeck (OpenAction) plugins use a WebSocket-based model and their own manifests. Compatibility is **not** a goal; the differences are intentional (declarative UI instead of property inspector HTML, stdio instead of ports). Macro Deck is the closest open-source relative with a plugin store. I have not read their wire protocols in detail; a comparison note will be added when we design the SDK.
