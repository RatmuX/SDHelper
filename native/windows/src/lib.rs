//! The SDHelper native helper: system integration of the agent behind a small JSON-RPC interface.

pub mod backend;
pub mod mock;
pub mod poll;
pub mod rpc;

#[cfg(windows)]
pub mod win;
