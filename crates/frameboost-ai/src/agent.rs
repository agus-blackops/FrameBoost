//! The tool-use loop.
//!
//! Ask a question, let Claude call simulator tools until it has the evidence
//! it wants, return its answer. History is only ever appended to — thinking
//! blocks are tied to the exact conversation that produced them — and a turn
//! that does not finish cleanly is cut off at the point it started, leaving
//! the earlier conversation intact.

use serde_json::{json, Value};

use crate::api::{build_request, ApiError, Settings, Transport};
use crate::tools;

/// Model turns allowed per question before giving up.
pub const DEFAULT_MAX_STEPS: usize = 30;

pub const SYSTEM_PROMPT: &str = "\
You are the FrameBoost advisor, an engineer who tunes and explains FrameBoost.

FrameBoost sits between a renderer and a swapchain. It renders fewer, smaller \
frames than the display consumes and reconstructs the difference: spatially by \
upscaling (FSR 1-style EASU + RCAS) and temporally by interpolating frames that \
were never rendered. Boost is an ordered ladder of rungs, each cheaper and \
riskier than the one below.

Two governors pick the rung, and quality vetoes performance:
  active_rung = min(perf.requested, quality.ceiling)
- The perf governor watches frame time and asks for whatever rung meets the budget.
- The quality governor is shaped like dynamic loss scaling (GradScaler): it scores \
each frame's confidence from measured signals (disocclusion, motion residual, luma \
shift, camera motion, depth complexity, pacing instability). Below `reject_below` \
a generated frame is discarded (the real frame is shown instead) or a spatial rung \
is degraded; the ceiling drops `backoff_rungs`, waits a cooldown scaled by the \
rung's risk, and climbs one rung only after `growth_interval` clean frames. Scene \
cuts impose `cut_cooldown_frames`.

You have a deterministic closed-loop simulator. Signals in it are measured from \
rendered pixels by the same code a real integration calls; only frame time is \
modelled. Use the tools to measure rather than speculate: establish a baseline \
with defaults before changing anything, compare variants against that baseline, \
and change one knob at a time when attributing an effect. Run independent \
configurations in parallel.

When you recommend settings, give the numbers that justify them (boost, cost, \
rejected %, delivered fps) and name the trade-off: a lower rejection threshold \
buys boost by letting through frames that may visibly ghost or smear, and that \
cost is not visible in any metric here. Say plainly when the simulator cannot \
answer a question (it has no eyes on the image and no real GPU timings). Answer \
in the language the user writes in.";

/// What the loop reports as it goes, for a UI to render.
#[derive(Debug)]
pub enum Event<'a> {
    /// A summary of Claude's reasoning.
    Thinking(&'a str),
    /// Answer text.
    Text(&'a str),
    /// Claude asked for a tool.
    ToolCall { name: &'a str, input: &'a Value },
    /// The tool's result, or the error sent back in its place.
    ToolResult { name: &'a str, ok: bool },
}

/// How a question ended.
#[derive(Debug, PartialEq)]
pub enum Outcome {
    /// Claude answered; this is the answer's text.
    Answered(String),
    /// A safeguard declined the request (after any server-side fallback).
    Refused { category: Option<String> },
    /// The response hit `max_tokens` before finishing.
    Truncated,
    /// Claude was still calling tools after the step limit.
    StepLimit,
}

/// Running token counts.
#[derive(Debug, Default, Clone, Copy)]
pub struct Usage {
    pub input_tokens: u64,
    pub output_tokens: u64,
    pub cache_read_input_tokens: u64,
    pub cache_creation_input_tokens: u64,
}

impl Usage {
    fn add(&mut self, u: &Value) {
        let get = |k: &str| u[k].as_u64().unwrap_or(0);
        self.input_tokens += get("input_tokens");
        self.output_tokens += get("output_tokens");
        self.cache_read_input_tokens += get("cache_read_input_tokens");
        self.cache_creation_input_tokens += get("cache_creation_input_tokens");
    }
}

/// A conversation with the advisor.
pub struct Advisor<T: Transport> {
    transport: T,
    settings: Settings,
    tools: Value,
    messages: Vec<Value>,
    max_steps: usize,
    usage: Usage,
}

impl<T: Transport> Advisor<T> {
    pub fn new(transport: T, settings: Settings) -> Self {
        Advisor {
            transport,
            settings,
            tools: tools::definitions(),
            messages: Vec::new(),
            max_steps: DEFAULT_MAX_STEPS,
            usage: Usage::default(),
        }
    }

    pub fn with_max_steps(mut self, steps: usize) -> Self {
        self.max_steps = steps.max(1);
        self
    }

    pub fn settings(&self) -> &Settings {
        &self.settings
    }

    pub fn usage(&self) -> Usage {
        self.usage
    }

    /// The conversation so far, in Messages API form.
    pub fn messages(&self) -> &[Value] {
        &self.messages
    }

    /// Forget the conversation.
    pub fn reset(&mut self) {
        self.messages.clear();
    }

    /// Ask one question and run tools until Claude answers.
    pub fn ask(
        &mut self,
        question: &str,
        on_event: &mut dyn FnMut(Event<'_>),
    ) -> Result<Outcome, ApiError> {
        let checkpoint = self.messages.len();
        let result = self.run(question, on_event);
        if !matches!(result, Ok(Outcome::Answered(_))) {
            // Drop the unfinished turn whole. Truncating a suffix never
            // edits what came before, so earlier thinking stays valid.
            self.messages.truncate(checkpoint);
        }
        result
    }

    fn run(
        &mut self,
        question: &str,
        on_event: &mut dyn FnMut(Event<'_>),
    ) -> Result<Outcome, ApiError> {
        self.messages
            .push(json!({"role": "user", "content": question}));

        for _ in 0..self.max_steps {
            let request = build_request(&self.settings, SYSTEM_PROMPT, &self.tools, &self.messages);
            let response = self.transport.send(&request)?;
            self.usage.add(&response["usage"]);

            let stop_reason = response["stop_reason"].as_str().unwrap_or("");
            if stop_reason == "refusal" {
                let category = response["stop_details"]["category"]
                    .as_str()
                    .map(str::to_string);
                return Ok(Outcome::Refused { category });
            }

            let content = response["content"].as_array().cloned().unwrap_or_default();
            let mut text = String::new();
            let mut calls = Vec::new();
            for block in &content {
                match block["type"].as_str() {
                    Some("thinking") => {
                        if let Some(t) = block["thinking"].as_str().filter(|t| !t.is_empty()) {
                            on_event(Event::Thinking(t));
                        }
                    }
                    Some("text") => {
                        let t = block["text"].as_str().unwrap_or("");
                        on_event(Event::Text(t));
                        text.push_str(t);
                    }
                    Some("tool_use") => calls.push(block.clone()),
                    _ => {}
                }
            }

            if stop_reason == "max_tokens" {
                return Ok(Outcome::Truncated);
            }

            // The whole content array goes back verbatim, thinking included.
            // An empty one is not a valid message and carries nothing.
            if !content.is_empty() {
                self.messages
                    .push(json!({"role": "assistant", "content": content}));
            }

            match stop_reason {
                "tool_use" => {
                    let results = run_tools(&calls, on_event);
                    self.messages
                        .push(json!({"role": "user", "content": results}));
                }
                // The server paused a long turn; sending the history back
                // as-is lets it continue.
                "pause_turn" => {}
                _ => return Ok(Outcome::Answered(text)),
            }
        }
        Ok(Outcome::StepLimit)
    }
}

/// Runs every call from one assistant turn — concurrently, since simulator
/// runs are independent — and returns all results for a single user message.
fn run_tools(calls: &[Value], on_event: &mut dyn FnMut(Event<'_>)) -> Vec<Value> {
    for call in calls {
        on_event(Event::ToolCall {
            name: call["name"].as_str().unwrap_or(""),
            input: &call["input"],
        });
    }

    let outputs: Vec<Result<Value, String>> = std::thread::scope(|s| {
        let handles: Vec<_> = calls
            .iter()
            .map(|call| {
                s.spawn(move || tools::execute(call["name"].as_str().unwrap_or(""), &call["input"]))
            })
            .collect();
        handles
            .into_iter()
            .map(|h| {
                h.join()
                    .unwrap_or_else(|_| Err("the tool panicked".to_string()))
            })
            .collect()
    });

    calls
        .iter()
        .zip(outputs)
        .map(|(call, output)| {
            let name = call["name"].as_str().unwrap_or("");
            on_event(Event::ToolResult { name, ok: output.is_ok() });
            let id = call["id"].clone();
            match output {
                Ok(v) => json!({"type": "tool_result", "tool_use_id": id, "content": v.to_string()}),
                Err(e) => json!({"type": "tool_result", "tool_use_id": id, "content": e, "is_error": true}),
            }
        })
        .collect()
}
