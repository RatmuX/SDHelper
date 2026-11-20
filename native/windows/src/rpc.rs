//! JSON-RPC 2.0, one JSON object per line. The only entry point is [`handle_line`]; everything it can do is
//! listed in [`dispatch`], so a caller can never make the helper run anything else.

use crate::backend::*;
use base64::{engine::general_purpose::STANDARD, Engine};
use serde::de::DeserializeOwned;
use serde::Deserialize;
use serde_json::{json, Value};

/// Longest accepted request line (1 MiB).
pub const MAX_LINE_BYTES: usize = 1 << 20;
/// Largest payload accepted for DPAPI (64 KiB), far more than a password needs.
const MAX_SECRET_BYTES: usize = 64 * 1024;

mod code {
    pub const PARSE_ERROR: i64 = -32700;
    pub const INVALID_REQUEST: i64 = -32600;
    pub const METHOD_NOT_FOUND: i64 = -32601;
    pub const INVALID_PARAMS: i64 = -32602;
    pub const FAILED: i64 = -32000;
    pub const UNAVAILABLE: i64 = -32001;
    pub const UNSUPPORTED: i64 = -32002;
}

struct RpcError {
    code: i64,
    message: String,
}

impl RpcError {
    fn new(code: i64, message: impl Into<String>) -> Self {
        RpcError {
            code,
            message: message.into(),
        }
    }
    fn invalid_params(message: impl Into<String>) -> Self {
        RpcError::new(code::INVALID_PARAMS, message)
    }
}

impl From<BackendError> for RpcError {
    fn from(error: BackendError) -> Self {
        let code = match &error {
            BackendError::Unavailable(_) => code::UNAVAILABLE,
            BackendError::Failed(_) => code::FAILED,
            BackendError::Unsupported(_) => code::UNSUPPORTED,
        };
        RpcError::new(code, error.to_string())
    }
}

#[derive(Deserialize)]
struct Request {
    jsonrpc: Option<String>,
    id: Option<Value>,
    method: Option<String>,
    #[serde(default)]
    params: Value,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
struct SetVolume {
    delta: Option<i32>,
    value: Option<u8>,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
struct SetMute {
    device: Device,
    #[serde(default = "toggle")]
    mode: MuteMode,
}

fn toggle() -> MuteMode {
    MuteMode::Toggle
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
struct MediaParams {
    command: MediaCommand,
}

#[derive(Deserialize)]
#[serde(deny_unknown_fields)]
struct DataParams {
    data: String,
}

/// Handles one request line and returns the response line, or `None` for notifications (requests without an id).
pub fn handle_line(backend: &mut dyn Backend, line: &str) -> Option<String> {
    if line.len() > MAX_LINE_BYTES {
        return Some(error_line(
            Value::Null,
            &RpcError::new(code::INVALID_REQUEST, "request too large"),
        ));
    }
    let value: Value = match serde_json::from_str(line) {
        Ok(value) => value,
        Err(_) => {
            return Some(error_line(
                Value::Null,
                &RpcError::new(code::PARSE_ERROR, "parse error"),
            ))
        }
    };
    // A notification is a request *without* an id member; `"id": null` is still a request that needs an answer.
    let is_notification = value.get("id").is_none();
    let request: Request = match serde_json::from_value(value) {
        Ok(request) => request,
        Err(_) => {
            return Some(error_line(
                Value::Null,
                &RpcError::new(code::INVALID_REQUEST, "invalid request"),
            ))
        }
    };
    let reply_id = request.id.clone().unwrap_or(Value::Null);
    let method = match (&request.jsonrpc, &request.method) {
        (Some(version), Some(method)) if version == "2.0" => method.as_str(),
        _ => {
            return Some(error_line(
                reply_id,
                &RpcError::new(code::INVALID_REQUEST, "invalid request"),
            ))
        }
    };

    let outcome = dispatch(backend, method, request.params);
    if is_notification {
        return None;
    }
    Some(match outcome {
        Ok(result) => json!({ "jsonrpc": "2.0", "id": reply_id, "result": result }).to_string(),
        Err(error) => error_line(reply_id, &error),
    })
}

/// A notification line the helper sends on its own, for example `audio.changed`.
pub fn notification_line(method: &str, params: Value) -> String {
    json!({ "jsonrpc": "2.0", "method": method, "params": params }).to_string()
}

fn error_line(id: Value, error: &RpcError) -> String {
    json!({ "jsonrpc": "2.0", "id": id, "error": { "code": error.code, "message": error.message } })
        .to_string()
}

fn parse<T: DeserializeOwned>(params: Value) -> Result<T, RpcError> {
    let params = if params.is_null() { json!({}) } else { params };
    serde_json::from_value(params).map_err(|e| RpcError::invalid_params(e.to_string()))
}

fn to_json<T: serde::Serialize>(value: T) -> Result<Value, RpcError> {
    serde_json::to_value(value).map_err(|e| RpcError::new(code::FAILED, e.to_string()))
}

fn decode_secret(params: Value) -> Result<Vec<u8>, RpcError> {
    let DataParams { data } = parse(params)?;
    let bytes = STANDARD
        .decode(data)
        .map_err(|_| RpcError::invalid_params("data is not valid base64"))?;
    if bytes.len() > MAX_SECRET_BYTES {
        return Err(RpcError::invalid_params("data too large"));
    }
    Ok(bytes)
}

fn dispatch(backend: &mut dyn Backend, method: &str, params: Value) -> Result<Value, RpcError> {
    match method {
        "system.version" => {
            Ok(json!({ "version": env!("CARGO_PKG_VERSION"), "os": std::env::consts::OS }))
        }
        "audio.get" => to_json(backend.audio_get()?),
        "audio.setVolume" => {
            let SetVolume { delta, value } = parse(params)?;
            match (delta, value) {
                (Some(_), Some(_)) | (None, None) => Err(RpcError::invalid_params(
                    "give exactly one of delta and value",
                )),
                (Some(delta), None) if !(-100..=100).contains(&delta) => Err(
                    RpcError::invalid_params("delta must be between -100 and 100"),
                ),
                (None, Some(value)) if value > 100 => {
                    Err(RpcError::invalid_params("value must be between 0 and 100"))
                }
                (delta, value) => to_json(backend.audio_set_volume(delta, value)?),
            }
        }
        "audio.setMute" => {
            let SetMute { device, mode } = parse(params)?;
            to_json(backend.audio_set_mute(device, mode)?)
        }
        "media.command" => {
            let MediaParams { command } = parse(params)?;
            to_json(backend.media_command(command)?)
        }
        "media.get" => to_json(backend.media_get()?),
        "dpapi.protect" => {
            let protected = backend.protect(&decode_secret(params)?)?;
            Ok(json!({ "data": STANDARD.encode(protected) }))
        }
        "dpapi.unprotect" => {
            let plain = backend.unprotect(&decode_secret(params)?)?;
            Ok(json!({ "data": STANDARD.encode(plain) }))
        }
        other => Err(RpcError::new(
            code::METHOD_NOT_FOUND,
            format!("unknown method {other}"),
        )),
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::mock::MockBackend;

    fn call(backend: &mut MockBackend, request: &str) -> Value {
        serde_json::from_str(&handle_line(backend, request).expect("a response")).unwrap()
    }

    fn rpc(method: &str, params: Value) -> String {
        json!({ "jsonrpc": "2.0", "id": 1, "method": method, "params": params }).to_string()
    }

    #[test]
    fn answers_with_the_same_id_of_any_json_type() {
        let mut backend = MockBackend::new();
        for id in [json!(7), json!("abc"), json!(null)] {
            let request =
                json!({ "jsonrpc": "2.0", "id": id, "method": "system.version" }).to_string();
            let response = call(&mut backend, &request);
            assert_eq!(response["id"], id);
            assert!(response["result"]["version"].is_string());
        }
    }

    #[test]
    fn requests_without_id_are_notifications_and_get_no_answer() {
        let mut backend = MockBackend::new();
        let request =
            json!({ "jsonrpc": "2.0", "method": "audio.setMute", "params": { "device": "input" } })
                .to_string();
        assert!(handle_line(&mut backend, &request).is_none());
        assert!(
            backend.audio.input.as_ref().unwrap().muted,
            "the notification was still executed"
        );
    }

    #[test]
    fn rejects_garbage_and_oversized_input_without_panicking() {
        let mut backend = MockBackend::new();
        assert_eq!(call(&mut backend, "not json")["error"]["code"], -32700);
        assert_eq!(call(&mut backend, "{}")["error"]["code"], -32600);
        assert_eq!(
            call(&mut backend, r#"{"jsonrpc":"1.0","id":1,"method":"x"}"#)["error"]["code"],
            -32600
        );
        assert_eq!(
            call(&mut backend, &rpc("nope", json!({})))["error"]["code"],
            -32601
        );
        let huge = format!("{{\"pad\":\"{}\"}}", "x".repeat(MAX_LINE_BYTES));
        assert_eq!(call(&mut backend, &huge)["error"]["code"], -32600);
    }

    #[test]
    fn audio_round_trip() {
        let mut backend = MockBackend::new();
        let state = call(&mut backend, &rpc("audio.get", json!({})));
        assert_eq!(
            state["result"]["output"],
            json!({ "volume": 50, "muted": false })
        );

        let up = call(&mut backend, &rpc("audio.setVolume", json!({ "delta": 5 })));
        assert_eq!(up["result"]["output"]["volume"], 55);
        let set = call(
            &mut backend,
            &rpc("audio.setVolume", json!({ "value": 10 })),
        );
        assert_eq!(set["result"]["output"]["volume"], 10);
        let clamped = call(
            &mut backend,
            &rpc("audio.setVolume", json!({ "delta": -100 })),
        );
        assert_eq!(clamped["result"]["output"]["volume"], 0);
    }

    #[test]
    fn volume_parameters_are_validated() {
        let mut backend = MockBackend::new();
        for params in [
            json!({}),
            json!({ "delta": 1, "value": 1 }),
            json!({ "delta": 101 }),
            json!({ "value": 101 }),
            json!({ "value": -1 }),
            json!({ "delta": "5" }),
            json!({ "delta": 5, "extra": true }),
        ] {
            assert_eq!(
                call(&mut backend, &rpc("audio.setVolume", params.clone()))["error"]["code"],
                -32602,
                "{params}"
            );
        }
        assert_eq!(
            backend.audio.output.as_ref().unwrap().volume,
            50,
            "nothing changed"
        );
    }

    #[test]
    fn mute_modes() {
        let mut backend = MockBackend::new();
        let toggled = call(
            &mut backend,
            &rpc("audio.setMute", json!({ "device": "input" })),
        );
        assert_eq!(toggled["result"]["input"]["muted"], true);
        let off = call(
            &mut backend,
            &rpc("audio.setMute", json!({ "device": "input", "mode": "off" })),
        );
        assert_eq!(off["result"]["input"]["muted"], false);
        let on = call(
            &mut backend,
            &rpc("audio.setMute", json!({ "device": "output", "mode": "on" })),
        );
        assert_eq!(on["result"]["output"]["muted"], true);
        assert_eq!(
            call(
                &mut backend,
                &rpc("audio.setMute", json!({ "device": "speaker" }))
            )["error"]["code"],
            -32602
        );
    }

    #[test]
    fn media_commands() {
        let mut backend = MockBackend::new();
        let playing = call(
            &mut backend,
            &rpc("media.command", json!({ "command": "playPause" })),
        );
        assert_eq!(playing["result"]["status"], "playing");
        let next = call(
            &mut backend,
            &rpc("media.command", json!({ "command": "next" })),
        );
        assert_eq!(next["result"]["title"], "Track Two");
        assert_eq!(
            call(&mut backend, &rpc("media.get", json!({})))["result"]["available"],
            true
        );
        assert_eq!(
            call(
                &mut backend,
                &rpc("media.command", json!({ "command": "explode" }))
            )["error"]["code"],
            -32602
        );
    }

    #[test]
    fn dpapi_round_trip_uses_base64() {
        let mut backend = MockBackend::new();
        let secret = STANDARD.encode(b"hunter2");
        let protected = call(
            &mut backend,
            &rpc("dpapi.protect", json!({ "data": secret })),
        );
        let blob = protected["result"]["data"].as_str().unwrap().to_string();
        assert_ne!(blob, secret);
        let plain = call(
            &mut backend,
            &rpc("dpapi.unprotect", json!({ "data": blob })),
        );
        assert_eq!(
            STANDARD
                .decode(plain["result"]["data"].as_str().unwrap())
                .unwrap(),
            b"hunter2"
        );

        assert_eq!(
            call(
                &mut backend,
                &rpc("dpapi.protect", json!({ "data": "!!!" }))
            )["error"]["code"],
            -32602
        );
        let too_big = STANDARD.encode(vec![0u8; MAX_SECRET_BYTES + 1]);
        assert_eq!(
            call(
                &mut backend,
                &rpc("dpapi.protect", json!({ "data": too_big }))
            )["error"]["code"],
            -32602
        );
    }

    #[test]
    fn backend_errors_map_to_distinct_codes() {
        let mut backend = MockBackend::new();
        backend.audio.input = None;
        let missing = call(
            &mut backend,
            &rpc("audio.setMute", json!({ "device": "input" })),
        );
        assert_eq!(missing["error"]["code"], -32001);
        assert_eq!(missing["error"]["message"], "no microphone");

        backend.fail_with = Some(BackendError::Failed("boom".into()));
        assert_eq!(
            call(&mut backend, &rpc("audio.get", json!({})))["error"]["code"],
            -32000
        );
        backend.fail_with = Some(BackendError::Unsupported("nope".into()));
        assert_eq!(
            call(&mut backend, &rpc("media.get", json!({})))["error"]["code"],
            -32002
        );
    }

    #[test]
    fn the_unsupported_backend_still_reports_its_version() {
        let mut backend = UnsupportedBackend;
        let version: Value = serde_json::from_str(
            &handle_line(&mut backend, &rpc("system.version", json!({}))).unwrap(),
        )
        .unwrap();
        assert!(version["result"]["os"].is_string());
        let audio: Value =
            serde_json::from_str(&handle_line(&mut backend, &rpc("audio.get", json!({}))).unwrap())
                .unwrap();
        assert_eq!(audio["error"]["code"], -32002);
    }
}
