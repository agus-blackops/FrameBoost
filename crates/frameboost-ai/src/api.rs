//! The Claude Messages API: how a request is shaped, and the seam it is sent
//! through.
//!
//! There is no official Anthropic SDK for Rust, so this speaks the wire format
//! directly: one `POST /v1/messages` per model turn. The [`Transport`] trait is
//! the seam the agent loop is tested through — the tests script Claude's side
//! of the conversation without a network or a key. The real transport lives in
//! `http`, behind the `http` feature.

use serde_json::{json, Value};

/// The model used unless told otherwise.
pub const DEFAULT_MODEL: &str = "claude-opus-5-5";

/// Server-side refusal fallback, in its `"default"` (category-routed) form.
const FALLBACK_BETA: &str = "server-side-fallback-2026-07-01";


/// How each request is shaped.
#[derive(Clone, Debug)]
pub struct Settings {
    /// Model ID, e.g. `claude-opus-5-5`.
    pub model: String,
    /// `output_config.effort`: `low`, `medium`, `high`, `xhigh` or `max`.
    ///
    /// Set explicitly because Claude Opus 5.5 defaults to `medium`, and tuning
    /// a control loop is exactly the multi-step work that repays more.
    pub effort: String,
    /// Per-response output ceiling.
    pub max_tokens: u32,
}

impl Default for Settings {
    fn default() -> Self {
        Settings {
            model: DEFAULT_MODEL.to_string(),
            effort: "high".to_string(),
            max_tokens: 16_000,
        }
    }
}

impl Settings {
    /// Haiku 4.5 predates adaptive thinking and effort; everything current
    /// takes both.
    fn is_legacy(&self) -> bool {
        self.model.starts_with("claude-haiku")
    }

    /// Models that accept `fallbacks: "default"` on the Claude API.
    fn supports_default_fallback(&self) -> bool {
        matches!(
            self.model.as_str(),
            "claude-opus-5-5" | "claude-opus-5" | "claude-fable-5-1" | "claude-sonnet-5-5"
        )
    }
}

/// A request body plus the beta flags it needs.
#[derive(Clone, Debug)]
pub struct Request {
    pub body: Value,
    pub betas: Vec<&'static str>,
}

/// Builds one Messages API request.
///
/// The system prompt and tool list are byte-identical across turns and the
/// history is only ever appended to, so top-level automatic caching keeps the
/// whole prefix warm.
pub fn build_request(
    settings: &Settings,
    system: &str,
    tools: &Value,
    messages: &[Value],
) -> Request {
    let mut body = json!({
        "model": settings.model,
        "max_tokens": settings.max_tokens,
        "system": system,
        "tools": tools,
        "messages": messages,
        "cache_control": {"type": "ephemeral"},
    });
    let mut betas = Vec::new();

    if !settings.is_legacy() {
        body["thinking"] = json!({"type": "adaptive", "display": "summarized"});
        body["output_config"] = json!({"effort": settings.effort});
    }
    if settings.supports_default_fallback() {
        body["fallbacks"] = json!("default");
        betas.push(FALLBACK_BETA);
    }
    Request { body, betas }
}

/// Something that failed on the way to, or back from, the API.
#[derive(Debug)]
pub struct ApiError {
    /// HTTP status, when there was a response at all.
    pub status: Option<u16>,
    pub message: String,
}

impl std::fmt::Display for ApiError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self.status {
            Some(s) => write!(f, "HTTP {s}: {}", self.message),
            None => write!(f, "{}", self.message),
        }
    }
}

impl std::error::Error for ApiError {}

/// Sends a request, returns the parsed response.
pub trait Transport {
    fn send(&self, request: &Request) -> Result<Value, ApiError>;
}
