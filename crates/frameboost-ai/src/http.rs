//! The blocking HTTP transport, for the CLI.

use std::time::Duration;

use serde_json::Value;

use crate::api::{ApiError, Request, Transport};

const API_VERSION: &str = "2023-06-01";
const DEFAULT_BASE_URL: &str = "https://api.anthropic.com";
const MAX_RETRIES: u32 = 3;

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
