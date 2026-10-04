//! What Claude can do: look at the scenario catalogue, and run the closed-loop
//! simulator under a configuration of its choosing.
//!
//! Every number Claude reasons from comes out of [`frameboost_sim::run`] — the
//! same harness the test suite uses — so an answer is only ever as good as a
//! measurement, never a guess about one.

use frameboost_core::level::{rung, LADDER, MAX_LEVEL};
use frameboost_core::{QualityConfig, SignalKind};
use frameboost_sim::{by_name, Config, Judgement, RunReport, CATALOGUE};
use serde::Deserialize;
use serde_json::{json, Value};

const TIMELINE_COLUMNS: usize = 60;
const MAX_FRAMES: u32 = 20_000;

/// Tool definitions, in Messages API form.
pub fn definitions() -> Value {
    json!([
        {
            "name": "list_scenarios",
            "description": "Describe the simulator: every scenario in the catalogue (name, length, \
                what failure it targets), the boost ladder (render scale, generated frames, \
                relative cost, a-priori risk per rung), and the default quality-governor and \
                simulator settings. Call this before running anything if you have not yet.",
            "input_schema": {"type": "object", "properties": {}, "additionalProperties": false}
        },
        {
            "name": "run_scenario",
            "description": "Run one scenario through the real FrameBoost controller in the \
                closed-loop simulator and return measured results: boost ratio (presented \
                frames per rendered frame), mean cost relative to native, delivered fps, \
                rejection rate and the signal that caused most rejections, time spent on each \
                rung, a rung-over-time timeline, and the first rejections. Each run takes about a second \
                in a release build, is deterministic, and independent runs can be requested in parallel \
                — e.g. a baseline and a variant side by side. Omitted fields use defaults.",
            "input_schema": {
                "type": "object",
                "properties": {
                    "scenario": {"type": "string", "description": "Scenario name from list_scenarios."},
                    "native_ms": {"type": "number", "description": "Cost of one unboosted frame in ms. Default 70."},
                    "target_fps": {"type": "number", "description": "Display refresh to target. Default 60."},
                    "frames": {"type": "integer", "description": "Override the scenario length."},
                    "quality": {
                        "type": "object",
                        "description": "Quality-governor overrides.",
                        "properties": {
                            "reject_below": {"type": "number", "description": "Confidence threshold in 0..1 below which reconstruction is rejected."},
                            "growth_interval": {"type": "integer", "description": "Clean frames required before the ceiling climbs one rung."},
                            "backoff_rungs": {"type": "integer", "description": "Rungs dropped on a rejection."},
                            "cooldown_frames": {"type": "integer", "description": "Base probation after a rejection, scaled by the failing rung's risk."},
                            "cut_cooldown_frames": {"type": "integer", "description": "Probation after a scene cut."},
                            "max_level": {"type": "integer", "description": "Highest rung ever allowed (0 = native)."}
                        },
                        "additionalProperties": false
                    }
                },
                "required": ["scenario"],
                "additionalProperties": false
            }
        }
    ])
}

/// Runs one tool call. `Err` is reported back to Claude as an error result, so
/// it is written for Claude to read and correct.
pub fn execute(name: &str, input: &Value) -> Result<Value, String> {
    match name {
        "list_scenarios" => Ok(list_scenarios()),
        "run_scenario" => {
            let args: RunArgs = serde_json::from_value(input.clone())
                .map_err(|e| format!("invalid input for run_scenario: {e}"))?;
            run_scenario(&args)
        }
        other => Err(format!("no tool named '{other}'")),
    }
}

fn list_scenarios() -> Value {
    let scenarios: Vec<Value> = CATALOGUE
        .iter()
        .map(|s| {
            json!({
                "name": s.name,
                "frames": s.frames,
                "about": s.blurb.split_whitespace().collect::<Vec<_>>().join(" "),
            })
        })
        .collect();
    let ladder: Vec<Value> = LADDER
        .iter()
        .enumerate()
        .map(|(i, r)| {
            json!({
                "level": i,
                "name": r.name,
                "render_scale": r.render_scale,
                "generated_per_rendered": r.generated_per_rendered,
                "relative_cost": round(r.relative_cost(), 3),
                "a_priori_risk": r.a_priori_risk,
            })
        })
        .collect();
    let d = Config::default();
    json!({
        "scenarios": scenarios,
        "ladder": ladder,
        "defaults": {
            "native_ms": d.native_frame_ns as f64 / 1e6,
            "target_fps": d.target_fps,
            "resolution": format!("{}x{}", d.output_width, d.output_height),
            "quality": quality_json(&QualityConfig::default()),
        },
    })
}

#[derive(Deserialize, Debug, Default)]
#[serde(deny_unknown_fields)]
pub struct RunArgs {
    pub scenario: String,
    pub native_ms: Option<f64>,
    pub target_fps: Option<f32>,
    pub frames: Option<u32>,
    #[serde(default)]
    pub quality: QualityArgs,
}

#[derive(Deserialize, Debug, Default)]
#[serde(deny_unknown_fields)]
pub struct QualityArgs {
    pub reject_below: Option<f32>,
    pub growth_interval: Option<u32>,
    pub backoff_rungs: Option<u8>,
    pub cooldown_frames: Option<u32>,
    pub cut_cooldown_frames: Option<u32>,
    pub max_level: Option<u8>,
}

fn run_scenario(args: &RunArgs) -> Result<Value, String> {
    let base = by_name(&args.scenario).ok_or_else(|| {
        let names: Vec<_> = CATALOGUE.iter().map(|s| s.name).collect();
        format!(
            "no scenario named '{}'; choose one of {names:?}",
            args.scenario
        )
    })?;
    let mut scenario = *base;
    if let Some(n) = args.frames {
        if !(1..=MAX_FRAMES).contains(&n) {
            return Err(format!("frames must be in 1..={MAX_FRAMES}"));
        }
        scenario.frames = n;
    }

    let mut cfg = Config {
        // The spatial passes do not feed the controller; skipping them only
        // makes the run faster.
        full_pipeline: false,
        ..Config::default()
    };
    if let Some(ms) = args.native_ms {
        if !(0.1..=1000.0).contains(&ms) {
            return Err("native_ms must be in 0.1..=1000".into());
        }
        cfg.native_frame_ns = (ms * 1e6) as u64;
    }
    if let Some(fps) = args.target_fps {
        if !(1.0..=1000.0).contains(&fps) {
            return Err("target_fps must be in 1..=1000".into());
        }
        cfg.target_fps = fps;
    }
    cfg.quality = apply_quality(&args.quality)?;

    let report = frameboost_sim::run(&scenario, &cfg);
    Ok(summarize(&report))
}

fn apply_quality(q: &QualityArgs) -> Result<QualityConfig, String> {
    let mut c = QualityConfig::default();
    if let Some(v) = q.reject_below {
        if !(0.0..=1.0).contains(&v) {
            return Err("quality.reject_below must be in 0..=1".into());
        }
        c.reject_below = v;
    }
    if let Some(v) = q.growth_interval {
        if !(1..=MAX_FRAMES).contains(&v) {
            return Err(format!(
                "quality.growth_interval must be in 1..={MAX_FRAMES}"
            ));
        }
        c.growth_interval = v;
    }
    if let Some(v) = q.backoff_rungs {
        if !(1..=MAX_LEVEL).contains(&v) {
            return Err(format!("quality.backoff_rungs must be in 1..={MAX_LEVEL}"));
        }
        c.backoff_rungs = v;
    }
    if let Some(v) = q.cooldown_frames {
        if v > MAX_FRAMES {
            return Err(format!(
                "quality.cooldown_frames must be at most {MAX_FRAMES}"
            ));
        }
        c.cooldown_frames = v;
    }
    if let Some(v) = q.cut_cooldown_frames {
        if v > MAX_FRAMES {
            return Err(format!(
                "quality.cut_cooldown_frames must be at most {MAX_FRAMES}"
            ));
        }
        c.cut_cooldown_frames = v;
    }
    if let Some(v) = q.max_level {
        if v > MAX_LEVEL {
            return Err(format!("quality.max_level must be at most {MAX_LEVEL}"));
        }
        c.max_level = v;
    }
    Ok(c)
}

fn quality_json(q: &QualityConfig) -> Value {
    json!({
        "reject_below": q.reject_below,
        "growth_interval": q.growth_interval,
        "backoff_rungs": q.backoff_rungs,
        "cooldown_frames": q.cooldown_frames,
        "cut_cooldown_frames": q.cut_cooldown_frames,
        "max_level": q.max_level,
    })
}

fn round(x: impl Into<f64>, places: i32) -> f64 {
    let p = 10f64.powi(places);
    (x.into() * p).round() / p
}

/// Everything a reviewer would look at, and nothing per-frame: a full CSV
/// would cost tokens without adding anything the timeline does not show.
pub fn summarize(r: &RunReport) -> Value {
    let t = &r.telemetry;
    let total = r.frames.len().max(1) as f64;

    let mut on_rungs = serde_json::Map::new();
    for (level, count) in t.frames_at_level.iter().enumerate() {
        if *count > 0 {
            on_rungs.insert(
                rung(level as u8).name.to_string(),
                json!(round(*count as f64 / total * 100.0, 1)),
            );
        }
    }
    let mut by_signal = serde_json::Map::new();
    for (kind, count) in SignalKind::ALL.iter().zip(t.rejections_by_signal) {
        if count > 0 {
            by_signal.insert(kind.name().to_string(), json!(count));
        }
    }
    let first_rejections: Vec<Value> = r
        .rejections()
        .take(8)
        .map(|f| {
            json!({
                "frame": f.index,
                "rung": rung(f.level).name,
                "confidence": round(f.confidence, 3),
                "cause": f.dominant.map(|d| d.name()),
                "scene_cut": f.scene_cut,
            })
        })
        .collect();
    let (rungs, rejects, per) = timeline(r);

    json!({
        "scenario": r.scenario,
        "settings": {
            "frames": r.frames.len(),
            "native_ms": r.config.native_frame_ns as f64 / 1e6,
            "target_fps": r.config.target_fps,
            "quality": quality_json(&r.config.quality),
        },
        "result": {
            "boost_ratio": round(t.boost_ratio(), 3),
            "relative_cost": round(t.mean_relative_cost(), 3),
            "mean_frame_ms": round(r.mean_frame_ns() / 1e6, 2),
            "delivered_fps": round(r.effective_fps(), 1),
            "rejected_pct": round(t.discard_rate() * 100.0, 2),
            "rejections_by_signal": by_signal,
            "scene_cuts": t.scene_cuts,
            "max_level_reached": rung(r.max_level()).name,
            "time_on_rungs_pct": on_rungs,
        },
        "timeline": {
            "rung": rungs,
            "reject": rejects,
            "frames_per_column": per,
            "legend": "digit = modal rung index per column; ! = a frame was rejected in that column",
        },
        "first_rejections": first_rejections,
    })
}

fn timeline(r: &RunReport) -> (String, String, usize) {
    let n = r.frames.len();
    if n == 0 {
        return (String::new(), String::new(), 0);
    }
    let per = n.div_ceil(TIMELINE_COLUMNS.min(n));
    let mut rungs = String::new();
    let mut rejects = String::new();
    for chunk in r.frames.chunks(per) {
        let mut counts = [0u32; 10];
        for f in chunk {
            counts[(f.level as usize).min(9)] += 1;
        }
        let modal = (0..counts.len())
            .max_by_key(|&i| (counts[i], usize::MAX - i))
            .unwrap_or(0);
        rungs.push(char::from_digit(modal as u32, 10).unwrap_or('?'));
        let rejected = chunk
            .iter()
            .any(|f| matches!(f.judgement, Judgement::Discarded | Judgement::Degraded));
        rejects.push(if rejected { '!' } else { '.' });
    }
    (rungs, rejects, per)
}
