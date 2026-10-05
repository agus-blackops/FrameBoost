//! A Claude-powered tuning assistant for FrameBoost.
//!
//! The quality governor has knobs — a rejection threshold, a growth interval,
//! cooldowns — and the README is honest that they are things a title will
//! need to tune rather than constants someone got right once. This crate hands
//! that job to Claude, with the closed-loop simulator as its only source of
//! evidence: Claude reads the scenario catalogue, runs baselines and variants,
//! and reports back with the measurements that justify a recommendation.
//!
//! ```text
//! export ANTHROPIC_API_KEY=...
//! cargo run --release -p frameboost-ai -- "Why does the fence scenario never boost past balanced?"
//! cargo run --release -p frameboost-ai            # interactive
//! ```

pub mod agent;
pub mod api;
#[cfg(feature = "http")]
pub mod http;
pub mod tools;

pub use agent::{Advisor, Event, Outcome, Usage, SYSTEM_PROMPT};
pub use api::{build_request, ApiError, Request, Settings, Transport, DEFAULT_MODEL};
#[cfg(feature = "http")]
pub use http::HttpTransport;
