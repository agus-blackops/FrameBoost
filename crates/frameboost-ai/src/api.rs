//! The Claude Messages API, over plain HTTP.
//!
//! There is no official Anthropic SDK for Rust, so this speaks the wire format
//! directly: one `POST /v1/messages` per model turn. The [`Transport`] trait is
//! the seam the agent loop is tested through — the tests script Claude's side
//! of the conversation without a network or a key.

use std::time::Duration;

use serde_json::{json, Value};

/// The model used unless told otherwise.
pub const DEFAULT_MODEL: &str = "claude-opus-5-5";

/// Server-side refusal fallback, in its `"default"` (category-routed) form.
const FALLBACK_BETA: &str = "server-side-fallback-2026-07-01";

const API_VERSION: &str = "2023-06-01";
const DEFAULT_BASE_URL: &str = "https://api.anthropic.com";
const MAX_RETRIES: u32 = 3;

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

enum Auth {
    ApiKey(String),
    Bearer(String),
}

/// The real thing.
pub struct HttpTransport {
    agent: ureq::Agent,
    base_url: String,
    auth: Auth,
}

impl HttpTransport {
    /// Reads credentials from `ANTHROPIC_API_KEY` (or `ANTHROPIC_AUTH_TOKEN`)
    /// and an optional `ANTHROPIC_BASE_URL`.
    pub fn from_env() -> Result<Self, ApiError> {
        let nonempty = |k: &str| std::env::var(k).ok().filter(|v| !v.is_empty());
        let auth = if let Some(key) = nonempty("ANTHROPIC_API_KEY") {
            Auth::ApiKey(key)
        } else if let Some(token) = nonempty("ANTHROPIC_AUTH_TOKEN") {
            Auth::Bearer(token)
        } else {
            return Err(ApiError {
                status: None,
                message: "set ANTHROPIC_API_KEY (or ANTHROPIC_AUTH_TOKEN) to talk to Claude".into(),
            });
        };
        let base_url = nonempty("ANTHROPIC_BASE_URL")
            .unwrap_or_else(|| DEFAULT_BASE_URL.to_string())
            .trim_end_matches('/')
            .to_string();

        // Error statuses come back as responses so their JSON body — which
        // says what was wrong — is not thrown away.
        let agent = ureq::Agent::config_builder()
            .http_status_as_error(false)
            .timeout_global(Some(Duration::from_secs(600)))
            .build()
            .into();
        Ok(HttpTransport {
            agent,
            base_url,
            auth,
        })
    }

    fn send_once(&self, request: &Request) -> Result<(u16, Option<u64>, Value), ApiError> {
        let mut req = self
            .agent
            .post(format!("{}/v1/messages", self.base_url))
            .header("anthropic-version", API_VERSION)
            .header("content-type", "application/json");
        req = match &self.auth {
            Auth::ApiKey(k) => req.header("x-api-key", k),
            Auth::Bearer(t) => req.header("authorization", &format!("Bearer {t}")),
        };
        if !request.betas.is_empty() {
            req = req.header("anthropic-beta", &request.betas.join(","));
        }

        let mut resp = req.send_json(&request.body).map_err(|e| ApiError {
            status: None,
            message: format!("request failed: {e}"),
        })?;
        let status = resp.status().as_u16();
        let retry_after = resp
            .headers()
            .get("retry-after")
            .and_then(|v| v.to_str().ok())
            .and_then(|v| v.trim().parse::<u64>().ok());
        let body: Value = resp.body_mut().read_json().map_err(|e| ApiError {
            status: Some(status),
            message: format!("unreadable response body: {e}"),
        })?;
        Ok((status, retry_after, body))
    }
}

fn retryable(status: u16) -> bool {
    status == 408 || status == 409 || status == 429 || status >= 500
}

impl Transport for HttpTransport {
    fn send(&self, request: &Request) -> Result<Value, ApiError> {
        let mut attempt = 0;
        loop {
            let (status, retry_after, body) = match self.send_once(request) {
                Ok(r) => r,
                Err(e) if e.status.is_none() && attempt < MAX_RETRIES => {
                    attempt += 1;
                    std::thread::sleep(Duration::from_secs(1 << attempt));
                    continue;
                }
                Err(e) => return Err(e),
            };
            if (200..300).contains(&status) {
                return Ok(body);
            }
            if retryable(status) && attempt < MAX_RETRIES {
                attempt += 1;
                let wait = retry_after.unwrap_or(1 << attempt).min(60);
                std::thread::sleep(Duration::from_secs(wait));
                continue;
            }
            let message = body["error"]["message"]
                .as_str()
                .map(str::to_string)
                .unwrap_or_else(|| body.to_string());
            return Err(ApiError {
                status: Some(status),
                message,
            });
        }
    }
}
