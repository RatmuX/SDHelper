//! Detects changes made outside SDHelper (the user turns the volume knob, a player starts) by looking at the
//! system every so often, and turns them into `audio.changed` / `media.changed` notifications.
//! Polling is simple and robust; event callbacks can replace it later without changing the protocol.

use crate::backend::*;
use crate::rpc::notification_line;
use serde_json::to_value;
use std::time::{Duration, Instant};

pub const AUDIO_EVERY: Duration = Duration::from_millis(500);
pub const MEDIA_EVERY: Duration = Duration::from_millis(1000);

pub struct Poller {
    audio_every: Duration,
    media_every: Duration,
    next_audio: Instant,
    next_media: Instant,
    last_audio: Option<AudioState>,
    last_media: Option<MediaState>,
}

impl Poller {
    pub fn new(now: Instant) -> Self {
        Self::with_intervals(now, AUDIO_EVERY, MEDIA_EVERY)
    }

    pub fn with_intervals(now: Instant, audio_every: Duration, media_every: Duration) -> Self {
        Poller {
            audio_every,
            media_every,
            next_audio: now,
            next_media: now,
            last_audio: None,
            last_media: None,
        }
    }

    /// Notification lines for everything that changed since the last time something was reported.
    pub fn poll(&mut self, now: Instant, backend: &mut dyn Backend) -> Vec<String> {
        let mut lines = Vec::new();
        if now >= self.next_audio {
            self.next_audio = now + self.audio_every;
            let state = match backend.audio_get() {
                Ok(state) => Some(state),
                Err(BackendError::Unavailable(_)) => Some(AudioState::default()),
                Err(_) => None, // a failed reading says nothing about the devices; keep the last known state
            };
            if let Some(state) = state {
                if self.last_audio.as_ref() != Some(&state) {
                    lines.push(notification_line(
                        "audio.changed",
                        to_value(&state).unwrap_or_default(),
                    ));
                    self.last_audio = Some(state);
                }
            }
        }
        if now >= self.next_media {
            self.next_media = now + self.media_every;
            let state = match backend.media_get() {
                Ok(state) => Some(state),
                Err(BackendError::Unavailable(_)) => Some(MediaState::none()),
                Err(_) => None,
            };
            if let Some(state) = state {
                if self.last_media.as_ref() != Some(&state) {
                    lines.push(notification_line(
                        "media.changed",
                        to_value(&state).unwrap_or_default(),
                    ));
                    self.last_media = Some(state);
                }
            }
        }
        lines
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::mock::MockBackend;
    use serde_json::Value;

    fn methods(lines: &[String]) -> Vec<String> {
        lines
            .iter()
            .map(|l| {
                serde_json::from_str::<Value>(l).unwrap()["method"]
                    .as_str()
                    .unwrap()
                    .to_string()
            })
            .collect()
    }

    #[test]
    fn reports_the_first_reading_then_only_changes() {
        let start = Instant::now();
        let mut backend = MockBackend::new();
        let mut poller =
            Poller::with_intervals(start, Duration::from_millis(10), Duration::from_millis(10));

        assert_eq!(
            methods(&poller.poll(start, &mut backend)),
            ["audio.changed", "media.changed"]
        );
        assert!(
            poller
                .poll(start + Duration::from_millis(20), &mut backend)
                .is_empty(),
            "nothing changed"
        );

        backend.audio_set_volume(Some(5), None).unwrap();
        let lines = poller.poll(start + Duration::from_millis(40), &mut backend);
        assert_eq!(methods(&lines), ["audio.changed"]);
        let params = &serde_json::from_str::<Value>(&lines[0]).unwrap()["params"];
        assert_eq!(params["output"]["volume"], 55);
    }

    #[test]
    fn respects_the_intervals() {
        let start = Instant::now();
        let mut backend = MockBackend::new();
        let mut poller = Poller::with_intervals(
            start,
            Duration::from_millis(500),
            Duration::from_millis(1000),
        );
        poller.poll(start, &mut backend);

        backend.audio_set_mute(Device::Input, MuteMode::On).unwrap();
        backend.media_command(MediaCommand::PlayPause).unwrap();

        assert!(
            poller
                .poll(start + Duration::from_millis(100), &mut backend)
                .is_empty(),
            "too early"
        );
        assert_eq!(
            methods(&poller.poll(start + Duration::from_millis(500), &mut backend)),
            ["audio.changed"]
        );
        assert_eq!(
            methods(&poller.poll(start + Duration::from_millis(1000), &mut backend)),
            ["media.changed"]
        );
    }

    #[test]
    fn missing_devices_are_a_change_but_failed_readings_are_not() {
        let start = Instant::now();
        let mut backend = MockBackend::new();
        let mut poller = Poller::with_intervals(start, Duration::ZERO, Duration::ZERO);
        poller.poll(start, &mut backend);

        backend.audio.output = None;
        backend.audio.input = None;
        let lines = poller.poll(start + Duration::from_millis(1), &mut backend);
        assert_eq!(methods(&lines), ["audio.changed"]);
        assert_eq!(
            serde_json::from_str::<Value>(&lines[0]).unwrap()["params"]["output"],
            Value::Null
        );

        backend.fail_with = Some(BackendError::Failed("transient".into()));
        assert!(poller
            .poll(start + Duration::from_millis(2), &mut backend)
            .is_empty());
    }
}
