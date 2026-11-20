//! In-memory backend for tests, and a stand-in whenever the real one is not available.

use crate::backend::*;

/// Behaves like a small computer: a speaker, a microphone and a player with three tracks.
pub struct MockBackend {
    pub audio: AudioState,
    pub media: MediaState,
    track: usize,
    /// When set, every call fails with this error (for testing error handling).
    pub fail_with: Option<BackendError>,
}

const TRACKS: [&str; 3] = ["Track One", "Track Two", "Track Three"];

impl MockBackend {
    pub fn new() -> Self {
        MockBackend {
            audio: AudioState {
                output: Some(Output {
                    volume: 50,
                    muted: false,
                }),
                input: Some(Input { muted: false }),
            },
            media: MediaState {
                available: true,
                status: MediaStatus::Paused,
                title: Some(TRACKS[0].to_string()),
                artist: Some("Mock Artist".to_string()),
            },
            track: 0,
            fail_with: None,
        }
    }

    fn check(&self) -> BackendResult<()> {
        match &self.fail_with {
            Some(error) => Err(error.clone()),
            None => Ok(()),
        }
    }
}

impl Default for MockBackend {
    fn default() -> Self {
        Self::new()
    }
}

impl Backend for MockBackend {
    fn audio_get(&mut self) -> BackendResult<AudioState> {
        self.check()?;
        Ok(self.audio.clone())
    }

    fn audio_set_volume(
        &mut self,
        delta: Option<i32>,
        absolute: Option<u8>,
    ) -> BackendResult<AudioState> {
        self.check()?;
        let output = self
            .audio
            .output
            .as_mut()
            .ok_or_else(|| BackendError::Unavailable("no output device".into()))?;
        let target = match (absolute, delta) {
            (Some(value), _) => i32::from(value),
            (None, Some(delta)) => i32::from(output.volume) + delta,
            (None, None) => i32::from(output.volume),
        };
        output.volume = target.clamp(0, 100) as u8;
        Ok(self.audio.clone())
    }

    fn audio_set_mute(&mut self, device: Device, mode: MuteMode) -> BackendResult<AudioState> {
        self.check()?;
        let apply = |current: bool| match mode {
            MuteMode::Toggle => !current,
            MuteMode::On => true,
            MuteMode::Off => false,
        };
        match device {
            Device::Output => {
                let output = self
                    .audio
                    .output
                    .as_mut()
                    .ok_or_else(|| BackendError::Unavailable("no output device".into()))?;
                output.muted = apply(output.muted);
            }
            Device::Input => {
                let input = self
                    .audio
                    .input
                    .as_mut()
                    .ok_or_else(|| BackendError::Unavailable("no microphone".into()))?;
                input.muted = apply(input.muted);
            }
        }
        Ok(self.audio.clone())
    }

    fn media_command(&mut self, command: MediaCommand) -> BackendResult<MediaState> {
        self.check()?;
        if !self.media.available {
            return Err(BackendError::Unavailable("no media player".into()));
        }
        match command {
            MediaCommand::PlayPause => {
                self.media.status = if self.media.status == MediaStatus::Playing {
                    MediaStatus::Paused
                } else {
                    MediaStatus::Playing
                };
            }
            MediaCommand::Next => self.track = (self.track + 1) % TRACKS.len(),
            MediaCommand::Previous => self.track = (self.track + TRACKS.len() - 1) % TRACKS.len(),
        }
        self.media.title = Some(TRACKS[self.track].to_string());
        Ok(self.media.clone())
    }

    fn media_get(&mut self) -> BackendResult<MediaState> {
        self.check()?;
        Ok(self.media.clone())
    }

    /// Not real encryption: reverses the bytes, which is enough to test the plumbing.
    fn protect(&mut self, data: &[u8]) -> BackendResult<Vec<u8>> {
        self.check()?;
        Ok(data.iter().rev().copied().collect())
    }

    fn unprotect(&mut self, data: &[u8]) -> BackendResult<Vec<u8>> {
        self.check()?;
        Ok(data.iter().rev().copied().collect())
    }
}
