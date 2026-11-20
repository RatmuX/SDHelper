//! Windows implementation of the backend: Core Audio (volume, mute), System Media Transport Controls
//! (whatever player the system exposes) and DPAPI. Everything here runs on the one thread that owns the backend.

mod audio;
mod dpapi;
mod media;

use crate::backend::*;
use windows::Win32::System::Com::{CoInitializeEx, COINIT_MULTITHREADED};

/// An error from the operating system, as the backend reports it.
pub(crate) fn failed(error: windows::core::Error) -> BackendError {
    BackendError::Failed(error.message())
}

pub struct WindowsBackend {
    audio: audio::Audio,
    media: media::Media,
}

impl WindowsBackend {
    /// Starts COM for this thread (multi-threaded apartment) and finds the audio device enumerator.
    pub fn new() -> Result<Self, String> {
        // SAFETY: plain COM initialisation of the calling thread, once, before any other COM call.
        unsafe { CoInitializeEx(None, COINIT_MULTITHREADED) }
            .ok()
            .map_err(|e| e.message())?;
        Ok(WindowsBackend {
            audio: audio::Audio::new()?,
            media: media::Media::new(),
        })
    }
}

impl Backend for WindowsBackend {
    fn audio_get(&mut self) -> BackendResult<AudioState> {
        self.audio.state()
    }

    fn audio_set_volume(
        &mut self,
        delta: Option<i32>,
        absolute: Option<u8>,
    ) -> BackendResult<AudioState> {
        self.audio.set_volume(delta, absolute)
    }

    fn audio_set_mute(&mut self, device: Device, mode: MuteMode) -> BackendResult<AudioState> {
        self.audio.set_mute(device, mode)
    }

    fn media_command(&mut self, command: MediaCommand) -> BackendResult<MediaState> {
        self.media.command(command)
    }

    fn media_get(&mut self) -> BackendResult<MediaState> {
        self.media.state()
    }

    fn protect(&mut self, data: &[u8]) -> BackendResult<Vec<u8>> {
        dpapi::protect(data)
    }

    fn unprotect(&mut self, data: &[u8]) -> BackendResult<Vec<u8>> {
        dpapi::unprotect(data)
    }
}
