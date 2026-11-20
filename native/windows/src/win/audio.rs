//! Volume and mute of the default playback and recording devices through Core Audio.

use super::failed;
use crate::backend::*;
use std::ptr;
use windows::Win32::Media::Audio::Endpoints::IAudioEndpointVolume;
use windows::Win32::Media::Audio::{
    eCapture, eConsole, eRender, EDataFlow, IMMDeviceEnumerator, MMDeviceEnumerator,
};
use windows::Win32::System::Com::{CoCreateInstance, CLSCTX_ALL};

pub struct Audio {
    enumerator: IMMDeviceEnumerator,
}

impl Audio {
    pub fn new() -> Result<Self, String> {
        // SAFETY: COM is initialised on this thread by `WindowsBackend::new`.
        let enumerator: IMMDeviceEnumerator =
            unsafe { CoCreateInstance(&MMDeviceEnumerator, None, CLSCTX_ALL) }
                .map_err(|e| e.message())?;
        Ok(Audio { enumerator })
    }

    /// The volume interface of the *current* default device. Resolved on every call because the default changes
    /// when headphones are plugged in; `None` when there is no such device.
    fn endpoint(&self, flow: EDataFlow) -> BackendResult<Option<IAudioEndpointVolume>> {
        // SAFETY: COM calls on the owning thread with valid arguments.
        let Ok(device) = (unsafe { self.enumerator.GetDefaultAudioEndpoint(flow, eConsole) })
        else {
            return Ok(None); // the system has no default device of this kind
        };
        let volume: IAudioEndpointVolume =
            unsafe { device.Activate(CLSCTX_ALL, None) }.map_err(failed)?;
        Ok(Some(volume))
    }

    pub fn state(&self) -> BackendResult<AudioState> {
        let output = match self.endpoint(eRender)? {
            Some(volume) => Some(Output {
                volume: percent(&volume)?,
                muted: is_muted(&volume)?,
            }),
            None => None,
        };
        let input = match self.endpoint(eCapture)? {
            Some(volume) => Some(Input {
                muted: is_muted(&volume)?,
            }),
            None => None,
        };
        Ok(AudioState { output, input })
    }

    pub fn set_volume(
        &self,
        delta: Option<i32>,
        absolute: Option<u8>,
    ) -> BackendResult<AudioState> {
        let volume = self
            .endpoint(eRender)?
            .ok_or_else(|| BackendError::Unavailable("no output device".into()))?;
        let target = match (absolute, delta) {
            (Some(value), _) => i32::from(value),
            (None, Some(delta)) => i32::from(percent(&volume)?) + delta,
            (None, None) => i32::from(percent(&volume)?),
        }
        .clamp(0, 100);
        // SAFETY: a null event context is allowed and means "no particular source".
        unsafe { volume.SetMasterVolumeLevelScalar(target as f32 / 100.0, ptr::null()) }
            .map_err(failed)?;
        self.state()
    }

    pub fn set_mute(&self, device: Device, mode: MuteMode) -> BackendResult<AudioState> {
        let (flow, missing) = match device {
            Device::Output => (eRender, "no output device"),
            Device::Input => (eCapture, "no microphone"),
        };
        let volume = self
            .endpoint(flow)?
            .ok_or_else(|| BackendError::Unavailable(missing.into()))?;
        let target = match mode {
            MuteMode::Toggle => !is_muted(&volume)?,
            MuteMode::On => true,
            MuteMode::Off => false,
        };
        // SAFETY: as above.
        unsafe { volume.SetMute(target, ptr::null()) }.map_err(failed)?;
        self.state()
    }
}

fn percent(volume: &IAudioEndpointVolume) -> BackendResult<u8> {
    // SAFETY: plain getter on a valid interface.
    let scalar = unsafe { volume.GetMasterVolumeLevelScalar() }.map_err(failed)?;
    Ok((scalar * 100.0).round().clamp(0.0, 100.0) as u8)
}

fn is_muted(volume: &IAudioEndpointVolume) -> BackendResult<bool> {
    // SAFETY: plain getter on a valid interface.
    Ok(unsafe { volume.GetMute() }.map_err(failed)?.as_bool())
}
