//! The advisor's Rust half, for a JavaScript host.
//!
//! The mobile app talks to Claude from JavaScript, through the official
//! Anthropic SDK, and keeps everything else here so there is one copy of it:
//! the system prompt, the tool definitions, how a request is shaped for each
//! model, and the simulator the tools run.
//!
//! One entry point, JSON in and JSON out, so the host needs no binding
//! generator:
//!
//! ```text
//! ptr = fb_alloc(len); write the request's UTF-8 bytes at ptr
//! packed = fb_invoke(ptr, len)          // frees the request
//! out_ptr = packed >> 32; out_len = packed & 0xffff_ffff
//! read the response, then fb_free(out_ptr, out_len)
//! ```
//!
//! Requests: `{"op": "system_prompt"}`, `{"op": "tool_definitions"}`,
//! `{"op": "build_request", "settings": {...}, "messages": [...]}`,
//! `{"op": "execute", "name": "...", "input": {...}}`. Responses are
//! `{"ok": ...}` or `{"error": "..."}`.

use frameboost_ai::{build_request, tools, Settings, SYSTEM_PROMPT};
use serde_json::{json, Value};

/// Handles one request. Separate from the ABI so it can be tested natively.
pub fn handle(request: &str) -> Value {
    match dispatch(request) {
        Ok(v) => json!({ "ok": v }),
        Err(e) => json!({ "error": e }),
    }
}

fn dispatch(request: &str) -> Result<Value, String> {
    let req: Value = serde_json::from_str(request).map_err(|e| format!("bad request: {e}"))?;
    match req["op"].as_str() {
        Some("system_prompt") => Ok(json!(SYSTEM_PROMPT)),
        Some("tool_definitions") => Ok(tools::definitions()),
        Some("build_request") => {
            let s = &req["settings"];
            let d = Settings::default();
            let settings = Settings {
                model: s["model"].as_str().map_or(d.model, str::to_string),
                effort: s["effort"].as_str().map_or(d.effort, str::to_string),
                max_tokens: s["max_tokens"].as_u64().map_or(d.max_tokens, |n| n as u32),
            };
            let messages = req["messages"].as_array().cloned().unwrap_or_default();
            let r = build_request(&settings, SYSTEM_PROMPT, &tools::definitions(), &messages);
            Ok(json!({ "body": r.body, "betas": r.betas }))
        }
        // A tool failure is a result for Claude to read, not a host error.
        Some("execute") => Ok(match tools::execute(req["name"].as_str().unwrap_or(""), &req["input"]) {
            Ok(v) => json!({ "result": v }),
            Err(e) => json!({ "tool_error": e }),
        }),
        other => Err(format!("unknown op {other:?}")),
    }
}

#[no_mangle]
pub extern "C" fn fb_alloc(len: usize) -> *mut u8 {
    let mut buf = Vec::<u8>::with_capacity(len);
    let ptr = buf.as_mut_ptr();
    std::mem::forget(buf);
    ptr
}

/// # Safety
/// `ptr` and `len` must come from [`fb_alloc`] or [`fb_invoke`], once.
#[no_mangle]
pub unsafe extern "C" fn fb_free(ptr: *mut u8, len: usize) {
    drop(Vec::from_raw_parts(ptr, 0, len));
}

/// # Safety
/// `ptr` must come from [`fb_alloc`] with capacity `len`, holding `len` bytes.
#[no_mangle]
pub unsafe extern "C" fn fb_invoke(ptr: *mut u8, len: usize) -> u64 {
    let bytes = Vec::from_raw_parts(ptr, len, len);
    let out = match std::str::from_utf8(&bytes) {
        Ok(s) => handle(s),
        Err(_) => json!({ "error": "request is not UTF-8" }),
    }
    .to_string()
    .into_bytes()
    .into_boxed_slice();
    let out_len = out.len();
    let out_ptr = Box::into_raw(out) as *mut u8;
    ((out_ptr as u64) << 32) | out_len as u64
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn build_request_carries_settings_and_history() {
        let v = handle(
            r#"{"op":"build_request","settings":{"model":"claude-sonnet-5-5","effort":"low","max_tokens":64000},
                "messages":[{"role":"user","content":"hola"}]}"#,
        );
        let body = &v["ok"]["body"];
        assert_eq!(body["model"], "claude-sonnet-5-5");
        assert_eq!(body["output_config"]["effort"], "low");
        assert_eq!(body["max_tokens"], 64000);
        assert_eq!(body["messages"][0]["content"], "hola");
        assert_eq!(v["ok"]["betas"][0], "server-side-fallback-2026-07-01");
    }

    #[test]
    fn tool_errors_are_results_and_bad_ops_are_errors() {
        let v = handle(r#"{"op":"execute","name":"run_scenario","input":{"scenario":"nope"}}"#);
        assert!(v["ok"]["tool_error"].as_str().unwrap().contains("fence"));
        assert!(handle(r#"{"op":"nope"}"#)["error"].is_string());
        assert!(handle("not json")["error"].is_string());
    }
}
