//! What the helper can do, independent of the operating system. The JSON-RPC layer ([`crate::rpc`]) only knows
//! this trait; Windows implements it in `win`, tests use [`crate::mock::MockBackend`].

use serde::{Deserialize, Serialize};

/// State of the default output device. `volume` is a percentage from 0 to 100.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct Output {
    pub volume: u8,
    pub muted: bool,
}

/// State of the default input device (microphone).
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct Input {
    pub muted: bool,
}

/// `None` means there is currently no default device of that kind.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize, Default)]
pub struct AudioState {
    pub output: Option<Output>,
    pub input: Option<Input>,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub enum Device {
    Output,
    Input,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub enum MuteMode {
    Toggle,
    On,
    Off,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub enum MediaCommand {
    PlayPause,
    Next,
    Previous,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub enum MediaStatus {
    None,
    Playing,
    Paused,
    Stopped,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct MediaState {
    pub available: bool,
    pub status: MediaStatus,
    pub title: Option<String>,
    pub artist: Option<String>,
}

impl MediaState {
    pub fn none() -> Self {
        MediaState {
            available: false,
            status: MediaStatus::None,
            title: None,
            artist: None,
        }
    }
}

/// Why an operation failed. `Unavailable` is expected in normal life (no microphone, no player running).
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum BackendError {
    /// The thing asked for does not exist right now.
    Unavailable(String),
    /// The operating system reported an error.
    Failed(String),
    /// This helper build cannot do that on this platform.
    Unsupported(String),
}

impl std::fmt::Display for BackendError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            BackendError::Unavailable(m)
            | BackendError::Failed(m)
            | BackendError::Unsupported(m) => f.write_str(m),
        }
    }
}

pub type BackendResult<T> = Result<T, BackendError>;

pub trait Backend {
    fn audio_get(&mut self) -> BackendResult<AudioState>;
    /// Changes the output volume by `delta` percentage points, or sets it when `absolute` is given. Result is clamped to 0..=100.
    fn audio_set_volume(
        &mut self,
        delta: Option<i32>,
        absolute: Option<u8>,
    ) -> BackendResult<AudioState>;
    fn audio_set_mute(&mut self, device: Device, mode: MuteMode) -> BackendResult<AudioState>;
    fn media_command(&mut self, command: MediaCommand) -> BackendResult<MediaState>;
    fn media_get(&mut self) -> BackendResult<MediaState>;
    /// Encrypts for the current Windows user (DPAPI).
    fn protect(&mut self, data: &[u8]) -> BackendResult<Vec<u8>>;
    fn unprotect(&mut self, data: &[u8]) -> BackendResult<Vec<u8>>;
}

/// Backend for platforms without an implementation: every call reports that it is unsupported.
pub struct UnsupportedBackend;

impl UnsupportedBackend {
    fn no<T>(&self) -> BackendResult<T> {
        Err(BackendError::Unsupported(format!(
            "not supported on {}",
            std::env::consts::OS
        )))
    }
}

impl Backend for UnsupportedBackend {
    fn audio_get(&mut self) -> BackendResult<AudioState> {
        self.no()
    }
    fn audio_set_volume(&mut self, _: Option<i32>, _: Option<u8>) -> BackendResult<AudioState> {
        self.no()
    }
    fn audio_set_mute(&mut self, _: Device, _: MuteMode) -> BackendResult<AudioState> {
        self.no()
    }
    fn media_command(&mut self, _: MediaCommand) -> BackendResult<MediaState> {
        self.no()
    }
    fn media_get(&mut self) -> BackendResult<MediaState> {
        self.no()
    }
    fn protect(&mut self, _: &[u8]) -> BackendResult<Vec<u8>> {
        self.no()
    }
    fn unprotect(&mut self, _: &[u8]) -> BackendResult<Vec<u8>> {
        self.no()
    }
}
