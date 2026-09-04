//! Drives a scenario through the real controller and the real reconstruction
//! passes, in a closed loop.
//!
//! Closed loop matters. Frame time is computed from the rung the controller
//! chose, and fed back into the next decision — so boosting actually buys
//! headroom, and the perf governor is reacting to consequences of its own
//! choices rather than to a script. An open-loop harness would prove only that
//! a state machine transitions when told to.
//!
//! Everything the controller sees is measured, never asserted: signals come out
//! of [`measure_pair`] and the reconstruction passes running on rendered
//! pixels. The one thing modelled rather than measured is frame time itself,
//! since there is no GPU here — it comes from the ladder's cost model times the
//! scenario's load.

use frameboost_core::recon::{
    easu, interpolate_midpoint, rcas, reproject, EasuConfig, InterpolateConfig, ReprojectConfig,
};
use frameboost_core::signals::measure_pair;
use frameboost_core::{
    BoostController, ControllerConfig, DiscardReason, FramePacer, GBuffer, SignalKind, Signals,
    Telemetry, Verdict,
};

use crate::scenario::Scenario;
use crate::scene::{Frame, Scene};

/// Simulator settings.
#[derive(Clone, Copy, Debug)]
pub struct Config {
    /// Output width.
    pub output_width: u32,
    /// Output height.
    pub output_height: u32,
    /// Display refresh the controller targets.
    pub target_fps: f32,
    /// What one native frame would cost, with no boost at all.
    ///
    /// The default is deliberately brutal — a heavy frame that native rendering
    /// misses the budget by a wide margin — because that is the only situation
    /// in which the temporal rungs are ever reached, and they are the
    /// interesting ones.
    pub native_frame_ns: u64,
    /// Also run the spatial passes (EASU, RCAS) each frame.
    ///
    /// They do not feed the controller — the cost model, not a clock, decides
    /// frame time here — so this is about exercising them on real pixels and
    /// checking they stay finite and in range.
    pub full_pipeline: bool,
}

impl Default for Config {
    fn default() -> Self {
        Config {
            output_width: 192,
            output_height: 108,
            target_fps: 60.0,
            native_frame_ns: 70_000_000,
            full_pipeline: true,
        }
    }
}

impl Config {
    /// Small and quick, for tests.
    pub fn fast() -> Self {
        Config {
            output_width: 96,
            output_height: 54,
            full_pipeline: false,
            ..Default::default()
        }
    }
}

/// What the controller decided about one frame.
#[derive(Clone, Copy, Debug, PartialEq)]
pub enum Judgement {
    /// Reconstruction accepted.
    Presented,
    /// A generated frame thrown away.
    Discarded,
    /// A real frame shown, but the rung lost the controller's trust.
    Degraded,
    /// Not judged: no comparable history this frame.
    ///
    /// Happens on the frame after a rung change, when render resolution moves
    /// and the previous frame is the wrong size to compare against. A real
    /// integration resets its history at exactly the same moment and for
    /// exactly the same reason. Judging the frame anyway — against nothing —
    /// would hand the controller a clean bill of health it never earned.
    NotJudged,
}

/// One frame of the run.
#[derive(Clone, Copy, Debug)]
pub struct FrameRecord {
    /// Frame index.
    pub index: u32,
    /// Rung in use.
    pub level: u8,
    /// Quality ceiling after the frame.
    pub ceiling: u8,
    /// Confidence score, or 1.0 when not judged.
    pub confidence: f32,
    /// The decision.
    pub judgement: Judgement,
    /// Signal that dominated a rejection.
    pub dominant: Option<SignalKind>,
    /// Whether the cut detector fired.
    pub scene_cut: bool,
    /// Measured signals.
    pub signals: Signals,
    /// Modelled frame time.
    pub frame_ns: u64,
    /// Frames handed to the display.
    pub presented: u32,
}

/// The result of a run.
#[derive(Clone, Debug)]
pub struct RunReport {
    /// Scenario name.
    pub scenario: &'static str,
    /// Every frame.
    pub frames: Vec<FrameRecord>,
    /// The controller's own accounting.
    pub telemetry: Telemetry,
    /// Settings used.
    pub config: Config,
}

impl RunReport {
    /// Mean modelled frame time, in nanoseconds.
    pub fn mean_frame_ns(&self) -> f64 {
        if self.frames.is_empty() {
            return 0.0;
        }
        self.frames.iter().map(|f| f.frame_ns as f64).sum::<f64>() / self.frames.len() as f64
    }

    /// Frames per second the display actually receives.
    ///
    /// Presented frames over elapsed time — so a discarded generated frame
    /// correctly costs half its interval's frame rate, rather than being
    /// counted because it was computed.
    pub fn effective_fps(&self) -> f64 {
        let elapsed: f64 = self.frames.iter().map(|f| f.frame_ns as f64).sum();
        if elapsed <= 0.0 {
            return 0.0;
        }
        let presented: u32 = self.frames.iter().map(|f| f.presented).sum();
        presented as f64 * 1e9 / elapsed
    }

    /// Highest rung reached.
    pub fn max_level(&self) -> u8 {
        self.frames.iter().map(|f| f.level).max().unwrap_or(0)
    }

    /// Modal rung over a frame range.
    pub fn typical_level(&self, range: std::ops::Range<u32>) -> u8 {
        let mut counts = [0u32; 8];
        for f in self.frames.iter().filter(|f| range.contains(&f.index)) {
            counts[(f.level as usize).min(7)] += 1;
        }
        counts
            .iter()
            .enumerate()
            .max_by_key(|(_, c)| **c)
            .map(|(i, _)| i as u8)
            .unwrap_or(0)
    }

    /// Rejections in a frame range.
    pub fn rejections_in(&self, range: std::ops::Range<u32>) -> usize {
        self.frames
            .iter()
            .filter(|f| {
                range.contains(&f.index)
                    && matches!(f.judgement, Judgement::Discarded | Judgement::Degraded)
            })
            .count()
    }

    /// Every rejection, with the signal that caused it.
    pub fn rejections(&self) -> impl Iterator<Item = &FrameRecord> {
        self.frames
            .iter()
            .filter(|f| matches!(f.judgement, Judgement::Discarded | Judgement::Degraded))
    }
}

/// Run one scenario.
pub fn run(scenario: &Scenario, cfg: &Config) -> RunReport {
    let (ow, oh) = (cfg.output_width, cfg.output_height);
    let scene = if scenario.fence {
        Scene::fence(ow, oh)
    } else {
        Scene::open(ow, oh)
    };

    let mut ctl = BoostController::new(ControllerConfig::for_target_fps(ow, oh, cfg.target_fps));
    let mut pacer = FramePacer::for_target_fps(cfg.target_fps);
    let mut prev: Option<GBuffer> = None;
    let mut camera = [0.0f32, 0.0];
    let mut seed = 1u32;
    let mut clock = 0u64;
    let mut frame_ns = cfg.native_frame_ns;
    let mut last_level: Option<u8> = None;
    let mut records = Vec::with_capacity(scenario.frames as usize);

    for i in 0..scenario.frames {
        let beat = (scenario.beat)(i);

        if beat.cut {
            // A cut is a different shot: unrelated content, and a camera the
            // motion vectors describe nonsense about. Both are what a renderer
            // would actually emit across one.
            seed = seed.wrapping_mul(1_664_525).wrapping_add(1_013_904_223);
            camera[0] += ow as f32 * 37.0;
        }
        let prev_camera = camera;
        camera[0] += beat.velocity[0];
        camera[1] += beat.velocity[1];

        let plan = ctl.plan(frame_ns);
        if last_level != Some(plan.level) {
            // The rung changed, so the rendered-frame interval is about to
            // change with it. That is not jitter; feeding it back to the
            // controller as instability would make the controller oscillate on
            // its own decisions.
            pacer.reset();
            last_level = Some(plan.level);
        }
        let f = Frame {
            camera,
            prev_camera,
            flash: beat.flash,
            particles: beat.particles,
            seed,
            tick: i,
        };
        let cur = scene.render(plan.render_width, plan.render_height, &f);

        // Signals, measured — never asserted.
        let comparable = prev
            .as_ref()
            .is_some_and(|p| p.width() == cur.width() && p.height() == cur.height());

        let mut signals = Signals::default();
        if comparable {
            let p = prev.as_ref().expect("comparable implies present");
            // Reference speed in render pixels: a tenth of the rendered width
            // per frame reads as full camera motion.
            signals = measure_pair(p, &cur, cur.width() as f32 * 0.10);
            signals.pacing_instability = pacer.instability();

            signals.disocclusion = if plan.rung.is_temporal() {
                interpolate_midpoint(p, &cur, 0.5, InterpolateConfig::default()).disocclusion
            } else {
                // The controller ignores this at a spatial rung — correctly,
                // since nothing is reprojected — but it is worth measuring for
                // the report: it says what the temporal rungs *would* have hit.
                reproject(p, &cur, ReprojectConfig::default()).disocclusion
            };
        }

        if cfg.full_pipeline && (cur.width() != ow || cur.height() != oh) {
            let up = easu(&cur.color, ow, oh, EasuConfig::default());
            let sharp = rcas(&up, 0.5);
            debug_assert!(
                sharp.as_slice().iter().all(|c| c
                    .iter()
                    .all(|v| v.is_finite() && (-0.01..=1.01).contains(v))),
                "spatial pipeline produced an out-of-range pixel at frame {i}"
            );
        }

        let record = if comparable {
            let outcome = ctl.resolve(&signals);
            let (judgement, dominant, scene_cut) = match outcome.verdict {
                Verdict::Present { .. } => (Judgement::Presented, None, false),
                Verdict::Discard { reason, .. } => (
                    Judgement::Discarded,
                    Some(reason_signal(reason)),
                    matches!(reason, DiscardReason::SceneCut),
                ),
                Verdict::Degrade { reason, .. } => {
                    (Judgement::Degraded, Some(reason_signal(reason)), false)
                }
            };
            FrameRecord {
                index: i,
                level: outcome.level,
                ceiling: outcome.ceiling,
                confidence: outcome.verdict.confidence(),
                judgement,
                dominant,
                scene_cut,
                signals,
                frame_ns,
                presented: outcome.frames_presented,
            }
        } else {
            // No history to judge against, so the controller is not asked to.
            FrameRecord {
                index: i,
                level: plan.level,
                ceiling: ctl.ceiling(),
                confidence: 1.0,
                judgement: Judgement::NotJudged,
                dominant: None,
                scene_cut: false,
                signals,
                frame_ns,
                presented: 1,
            }
        };
        records.push(record);

        // Close the loop: the rung the controller picked decides what the next
        // frame costs, which is what it will decide from.
        let jitter = 1.0 + 0.03 * ((i as f64 * 0.7).sin());
        frame_ns = (cfg.native_frame_ns as f64
            * plan.rung.relative_cost() as f64
            * beat.load as f64
            * jitter) as u64;
        clock += frame_ns;
        pacer.on_rendered(clock);
        prev = Some(cur);
    }

    RunReport {
        scenario: scenario.name,
        frames: records,
        telemetry: ctl.telemetry().clone(),
        config: *cfg,
    }
}

fn reason_signal(reason: DiscardReason) -> SignalKind {
    match reason {
        DiscardReason::LowConfidence(k) => k,
        DiscardReason::SceneCut => SignalKind::MotionResidual,
    }
}
