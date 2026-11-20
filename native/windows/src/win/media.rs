//! Media control through the System Media Transport Controls: the same sessions that the Windows volume flyout
//! and the keyboard media keys control. Players that do not publish a session (some older apps) are invisible here.

use super::failed;
use crate::backend::*;
use windows::Media::Control::{
    GlobalSystemMediaTransportControlsSession as Session,
    GlobalSystemMediaTransportControlsSessionManager as Manager,
    GlobalSystemMediaTransportControlsSessionPlaybackStatus as Status,
};

pub struct Media {
    manager: Option<Manager>,
}

impl Media {
    pub fn new() -> Self {
        Media { manager: None }
    }

    /// The session manager is requested on first use, and again after a failure.
    fn manager(&mut self) -> BackendResult<&Manager> {
        if self.manager.is_none() {
            self.manager = Some(
                Manager::RequestAsync()
                    .map_err(failed)?
                    .join()
                    .map_err(failed)?,
            );
        }
        Ok(self.manager.as_ref().expect("set just above"))
    }

    /// The session the system treats as current, or `None` when no player is running.
    fn session(&mut self) -> BackendResult<Option<Session>> {
        Ok(self.manager()?.GetCurrentSession().ok())
    }

    pub fn command(&mut self, command: MediaCommand) -> BackendResult<MediaState> {
        let session = self
            .session()?
            .ok_or_else(|| BackendError::Unavailable("no media player is running".into()))?;
        let operation = match command {
            MediaCommand::PlayPause => session.TryTogglePlayPauseAsync(),
            MediaCommand::Next => session.TrySkipNextAsync(),
            MediaCommand::Previous => session.TrySkipPreviousAsync(),
        };
        let accepted = operation.map_err(failed)?.join().map_err(failed)?;
        if !accepted {
            return Err(BackendError::Unavailable(
                "the player did not accept the command".into(),
            ));
        }
        self.state()
    }

    pub fn state(&mut self) -> BackendResult<MediaState> {
        let Some(session) = self.session()? else {
            return Ok(MediaState::none());
        };
        let status = match session
            .GetPlaybackInfo()
            .and_then(|info| info.PlaybackStatus())
        {
            Ok(Status::Playing) => MediaStatus::Playing,
            Ok(Status::Paused) => MediaStatus::Paused,
            Ok(Status::Stopped) | Ok(Status::Opened) | Ok(Status::Changing) => MediaStatus::Stopped,
            _ => return Ok(MediaState::none()), // closed, or the session vanished while asking
        };
        let (title, artist) = match session
            .TryGetMediaPropertiesAsync()
            .and_then(|operation| operation.join())
        {
            Ok(properties) => (
                properties
                    .Title()
                    .ok()
                    .map(|t| t.to_string())
                    .filter(|t| !t.is_empty()),
                properties
                    .Artist()
                    .ok()
                    .map(|a| a.to_string())
                    .filter(|a| !a.is_empty()),
            ),
            Err(_) => (None, None),
        };
        Ok(MediaState {
            available: true,
            status,
            title,
            artist,
        })
    }
}
