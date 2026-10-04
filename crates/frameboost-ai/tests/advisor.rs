//! The advisor loop against a scripted Claude: no network, no key.

use std::cell::RefCell;

use frameboost_ai::{
    build_request, tools, Advisor, ApiError, Outcome, Request, Settings, Transport,
};
use serde_json::{json, Value};

/// Plays back canned responses and records every request it was sent.
struct Scripted {
    responses: RefCell<Vec<Value>>,
    seen: RefCell<Vec<Request>>,
}

impl Scripted {
    fn new(mut responses: Vec<Value>) -> Self {
        responses.reverse();
        Scripted {
            responses: RefCell::new(responses),
            seen: RefCell::new(Vec::new()),
        }
    }
}

impl Transport for &Scripted {
    fn send(&self, request: &Request) -> Result<Value, ApiError> {
        self.seen.borrow_mut().push(request.clone());
        self.responses.borrow_mut().pop().ok_or(ApiError {
            status: Some(500),
            message: "script ran out".into(),
        })
    }
}

fn reply(stop: &str, content: Value) -> Value {
    json!({
        "type": "message",
        "role": "assistant",
        "stop_reason": stop,
        "content": content,
        "usage": {"input_tokens": 10, "output_tokens": 5, "cache_read_input_tokens": 100},
    })
}

#[test]
fn request_uses_opus_with_adaptive_thinking_effort_and_fallback() {
    let r = build_request(&Settings::default(), "sys", &tools::definitions(), &[]);
    assert_eq!(r.body["model"], "claude-opus-5-5");
    assert_eq!(r.body["thinking"]["type"], "adaptive");
    assert_eq!(r.body["output_config"]["effort"], "high");
    assert_eq!(r.body["fallbacks"], "default");
    assert_eq!(r.betas, vec!["server-side-fallback-2026-07-01"]);
    assert_eq!(r.body["cache_control"]["type"], "ephemeral");
    // Removed on current models; sending either is a 400.
    assert!(r.body.get("temperature").is_none());
    assert!(r.body["thinking"].get("budget_tokens").is_none());
    assert!(r.body.get("tool_choice").is_none());
}

#[test]
fn haiku_gets_neither_adaptive_thinking_nor_effort_nor_fallbacks() {
    let s = Settings {
        model: "claude-haiku-4-5".into(),
        ..Settings::default()
    };
    let r = build_request(&s, "sys", &tools::definitions(), &[]);
    assert!(r.body.get("thinking").is_none());
    assert!(r.body.get("output_config").is_none());
    assert!(r.body.get("fallbacks").is_none());
    assert!(r.betas.is_empty());
}

#[test]
fn runs_parallel_tools_and_returns_all_results_in_one_message() {
    let thinking = json!({"type": "thinking", "thinking": "compare", "signature": "sig"});
    let script = Scripted::new(vec![
        reply(
            "tool_use",
            json!([
                thinking,
                {"type": "tool_use", "id": "a", "name": "run_scenario", "input": {"scenario": "calm-pan", "frames": 120}},
                {"type": "tool_use", "id": "b", "name": "run_scenario",
                 "input": {"scenario": "calm-pan", "frames": 120, "quality": {"reject_below": 0.4}}},
            ]),
        ),
        reply(
            "end_turn",
            json!([{"type": "text", "text": "Baseline wins."}]),
        ),
    ]);
    let mut advisor = Advisor::new(&script, Settings::default());
    let mut calls = 0;
    let out = advisor
        .ask("compare", &mut |e| {
            if let frameboost_ai::Event::ToolCall { .. } = e {
                calls += 1;
            }
        })
        .unwrap();

    assert_eq!(out, Outcome::Answered("Baseline wins.".into()));
    assert_eq!(calls, 2);

    let seen = script.seen.borrow();
    assert_eq!(seen.len(), 2);
    let msgs = seen[1].body["messages"].as_array().unwrap();
    assert_eq!(msgs.len(), 3);
    // Assistant content goes back verbatim, thinking block and signature included.
    assert_eq!(msgs[1]["content"][0], thinking);
    let results = msgs[2]["content"].as_array().unwrap();
    assert_eq!(results.len(), 2);
    assert_eq!(results[0]["tool_use_id"], "a");
    assert_eq!(results[1]["tool_use_id"], "b");
    let b: Value = serde_json::from_str(results[1]["content"].as_str().unwrap()).unwrap();
    assert_eq!(
        b["settings"]["quality"]["reject_below"].as_f64().unwrap() as f32,
        0.4
    );
    assert_eq!(b["settings"]["frames"], 120);

    // Second question builds on the first; history is append-only.
    assert_eq!(advisor.messages().len(), 4);
    assert_eq!(advisor.usage().cache_read_input_tokens, 200);
}

#[test]
fn bad_tool_input_comes_back_as_an_error_result() {
    let script = Scripted::new(vec![
        reply(
            "tool_use",
            json!([{"type": "tool_use", "id": "x", "name": "run_scenario", "input": {"scenario": "nope"}}]),
        ),
        reply("end_turn", json!([{"type": "text", "text": "ok"}])),
    ]);
    let mut advisor = Advisor::new(&script, Settings::default());
    advisor.ask("go", &mut |_| {}).unwrap();
    let seen = script.seen.borrow();
    let result = &seen[1].body["messages"][2]["content"][0];
    assert_eq!(result["is_error"], true);
    assert!(result["content"].as_str().unwrap().contains("calm-pan"));
}

#[test]
fn a_refusal_rolls_the_turn_back_and_keeps_earlier_history() {
    let script = Scripted::new(vec![
        reply("end_turn", json!([{"type": "text", "text": "first"}])),
        reply(
            "tool_use",
            json!([{"type": "tool_use", "id": "t", "name": "list_scenarios", "input": {}}]),
        ),
        json!({"stop_reason": "refusal", "content": [], "stop_details": {"type": "refusal", "category": null}}),
    ]);
    let mut advisor = Advisor::new(&script, Settings::default());
    advisor.ask("one", &mut |_| {}).unwrap();
    let before = advisor.messages().to_vec();

    let out = advisor.ask("two", &mut |_| {}).unwrap();
    assert_eq!(out, Outcome::Refused { category: None });
    assert_eq!(advisor.messages(), &before[..]);
}

#[test]
fn step_limit_stops_a_runaway_loop() {
    let call = reply(
        "tool_use",
        json!([{"type": "tool_use", "id": "t", "name": "list_scenarios", "input": {}}]),
    );
    let script = Scripted::new(vec![call.clone(), call.clone(), call]);
    let mut advisor = Advisor::new(&script, Settings::default()).with_max_steps(3);
    assert_eq!(
        advisor.ask("loop", &mut |_| {}).unwrap(),
        Outcome::StepLimit
    );
    assert!(advisor.messages().is_empty());
}

#[test]
fn quality_overrides_reach_the_controller() {
    // Capping the ladder at native must keep every frame on rung 0.
    let out = tools::execute(
        "run_scenario",
        &json!({"scenario": "calm-pan", "frames": 300, "quality": {"max_level": 0}}),
    )
    .unwrap();
    assert_eq!(out["result"]["max_level_reached"], "native");
    assert_eq!(out["result"]["boost_ratio"], 1.0);

    let free = tools::execute(
        "run_scenario",
        &json!({"scenario": "calm-pan", "frames": 300}),
    )
    .unwrap();
    assert!(free["result"]["boost_ratio"].as_f64().unwrap() > 1.0);
}

#[test]
fn out_of_range_overrides_are_rejected() {
    for q in [
        json!({"reject_below": 1.5}),
        json!({"max_level": 99}),
        json!({"bogus": 1}),
    ] {
        let r = tools::execute("run_scenario", &json!({"scenario": "fence", "quality": q}));
        assert!(r.is_err(), "{q} was accepted");
    }
}

#[test]
fn list_scenarios_describes_the_whole_catalogue() {
    let v = tools::execute("list_scenarios", &json!({})).unwrap();
    assert_eq!(
        v["scenarios"].as_array().unwrap().len(),
        frameboost_sim::CATALOGUE.len()
    );
    assert_eq!(
        v["ladder"].as_array().unwrap().len(),
        frameboost_core::level::LADDER.len()
    );
    assert_eq!(
        v["defaults"]["quality"]["reject_below"].as_f64().unwrap() as f32,
        0.55
    );
}
