//! Reads JSON-RPC requests (one per line) from stdin and writes responses and notifications to stdout.
//! stderr is for logs. The helper exits when stdin closes, which is how it learns that the agent is gone.

use sdhelper_native::backend::{Backend, UnsupportedBackend};
use sdhelper_native::poll::Poller;
use sdhelper_native::rpc::handle_line;
use std::io::{self, BufRead, Write};
use std::sync::mpsc;
use std::thread;
use std::time::{Duration, Instant};

const TICK: Duration = Duration::from_millis(100);

#[cfg(windows)]
fn create_backend() -> Box<dyn Backend> {
    match sdhelper_native::win::WindowsBackend::new() {
        Ok(backend) => Box::new(backend),
        Err(error) => {
            eprintln!("could not initialise the Windows backend: {error}");
            Box::new(UnsupportedBackend)
        }
    }
}

#[cfg(not(windows))]
fn create_backend() -> Box<dyn Backend> {
    Box::new(UnsupportedBackend)
}

fn write_line(line: &str) -> io::Result<()> {
    let mut out = io::stdout().lock();
    writeln!(out, "{line}")?;
    out.flush()
}

fn main() {
    if std::env::args().any(|a| a == "--version") {
        println!("sdhelper-native {}", env!("CARGO_PKG_VERSION"));
        return;
    }

    // Windows API objects belong to the thread that created them, so one thread owns the backend and does
    // both the requests and the polling; a second thread only reads stdin.
    let mut backend = create_backend();
    let (sender, receiver) = mpsc::channel::<String>();
    thread::spawn(move || {
        for line in io::stdin().lock().lines() {
            let Ok(line) = line else { break };
            if sender.send(line).is_err() {
                break;
            }
        }
        // dropping `sender` tells the main loop that the agent has gone
    });

    let mut poller = Poller::new(Instant::now());
    loop {
        match receiver.recv_timeout(TICK) {
            Ok(line) => {
                if let Some(response) = handle_line(backend.as_mut(), &line) {
                    if write_line(&response).is_err() {
                        break;
                    }
                }
            }
            Err(mpsc::RecvTimeoutError::Timeout) => {}
            Err(mpsc::RecvTimeoutError::Disconnected) => break,
        }
        for notification in poller.poll(Instant::now(), backend.as_mut()) {
            if write_line(&notification).is_err() {
                return;
            }
        }
    }
}
