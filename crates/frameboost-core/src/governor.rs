//! The control loop.
//!
//! Two governors and one arbitration rule.
//!
//! # [`QualityGovernor`] — the loss-scaling half
//!
//! Structurally a `GradScaler`. It holds a **ceiling**, not the active level:
//! the most aggressive rung reconstruction has currently earned. Each frame it
//! scores the evidence, and on a bad score it does the thing that makes loss
//! scaling work — it throws away completed work rather than shipping it — then
//! backs the ceiling down and refuses to climb again until a cooldown has
//! elapsed and a run of clean frames has accumulated.
//!
//! Three places it deliberately departs from `GradScaler`:
//!
//! 1. **The rejection test is statistical, not exact.** `inf`/`NaN` is a bit
//!    pattern; "this frame will look wrong" is a threshold on a weighted
//!    product of proxies. So the threshold is tunable, the dominant signal is
//!    reported, and both are treated as things a title will need to tune.
//!
//! 2. **Rejection means different things at different rungs.** You can decline
//!    to present a frame you generated. You cannot un-upscale a frame you
//!    already rendered at 59% — it is a real frame and it is going on screen.
//!    Hence [`Verdict::Degrade`] alongside [`Verdict::Discard`]: same effect on
//!    the ceiling, different effect on this frame.
//!
//! 3. **A scene cut retreats to [`highest_spatial_level`], not to native.** A
//!    cut breaks reprojection; it does nothing to a spatial upscaler. Dropping
//!    to native would surrender the spatial boost as well and spike frame time
//!    precisely when the renderer has a fresh scene to draw.
//!
//! # [`PerfGovernor`] — the half loss scaling does not have
//!
//! Loss scaling answers only to numerics. A renderer also has to hit a frame
//! budget. This governor watches smoothed frame time and asks for a rung.
//!
//! It asks by inverting the cost model rather than by stepping blindly toward
//! the budget, and that is not a refinement — a bang-bang controller cannot be
//! made to work here at all. Adjacent rungs on the [`LADDER`](crate::level::LADDER)
//! differ in cost by factors from 1.26 to 1.76, so any deadband narrow enough
//! to be useful is narrower than the step it is damping: the governor
//! overshoots the budget going up, undershoots it coming down, and hunts
//! between two rungs forever. Widening the deadband past 1.76 instead would
//! mean holding a rung until frame time fell below 57% of target, wasting most
//! of the headroom the boost just bought.
//!
//! So the governor estimates what a native frame would cost —
//! `measured / relative_cost(active_rung)` — and picks the *least* aggressive
//! rung whose predicted cost fits the budget. Systematic error in the cost
//! model largely cancels, because the estimate is measured through the same
//! model it is then applied to; what has to be right is the *ratios* between
//! rungs, not their absolute values. A dwell requirement keeps noise from
//! flipping the choice, and movement is one rung at a time so the picture never
//! lurches.
//!
//! # Arbitration: quality vetoes performance
//!
//! ```text
//! active_level = min(perf.desired, quality.ceiling)
//! ```
//!
//! Being over budget never buys a rung that quality has withdrawn. This is the
//! whole discipline in one line, and it is the line to reread whenever a boost
//! system starts shipping artifacts: the failure is almost always that
//! something let the frame budget outvote the evidence.
//!
//! The perf governor is additionally clamped to the ceiling, so that when
//! quality recovers, the level climbs back one rung at a time instead of
//! snapping to whatever the perf side had been demanding all along.

use crate::level::{
    highest_spatial_level, rung, BoostPlan, BoostRung, MAX_LEVEL,
};
use crate::signals::{SignalKind, SignalWeights, Signals};
use crate::telemetry::{Decision, DecisionKind, Telemetry};

/// Why reconstruction was not trusted.
#[derive(Clone, Copy, Debug, PartialEq)]
pub enum DiscardReason {
    /// Confidence fell below the threshold; the named signal dominated.
    LowConfidence(SignalKind),
    /// A hard cut. Never interpolate across one.
    SceneCut,
}

/// What to do with this frame.
#[derive(Clone, Copy, Debug, PartialEq)]
pub enum Verdict {
    /// Trusted. Present it, generated frames included.
    Present {
        /// Confidence score.
        confidence: f32,
    },
    /// Do not show the generated frame. Present the newest rendered frame in
    /// its place. Only a temporal rung can produce this.
    Discard {
        /// Why.
        reason: DiscardReason,
        /// Confidence score.
        confidence: f32,
    },
    /// The frame is real and is being shown regardless, but the rung has lost
    /// the controller's trust and the ceiling drops for subsequent frames.
    /// Only a spatial rung produces this.
    Degrade {
        /// Why.
        reason: DiscardReason,
        /// Confidence score.
        confidence: f32,
    },
}

impl Verdict {
    /// Whether reconstruction was accepted.
    pub fn accepted(&self) -> bool {
        matches!(self, Verdict::Present { .. })
    }

    /// The confidence score, whatever the outcome.
    pub fn confidence(&self) -> f32 {
        match self {
            Verdict::Present { confidence }
            | Verdict::Discard { confidence, .. }
            | Verdict::Degrade { confidence, .. } => *confidence,
        }
    }
}

/// Tuning for [`QualityGovernor`].
#[derive(Clone, Copy, Debug)]
pub struct QualityConfig {
    /// How each signal is scored.
    pub weights: SignalWeights,
    /// Confidence below which reconstruction is rejected.
    pub reject_below: f32,
    /// Clean frames required before the ceiling climbs one rung.
    ///
    /// The analogue of `GradScaler`'s `growth_interval`, and it wants to be
    /// generous. Climbing back too eagerly produces a system that oscillates
    /// across the boundary of a difficult scene, which is far more noticeable
    /// than simply sitting one rung lower through it.
    pub growth_interval: u32,
    /// Rungs to drop on a rejection.
    pub backoff_rungs: u8,
    /// Base frames of probation after a rejection, before the clean streak
    /// starts counting at all.
    ///
    /// Total recovery is therefore `cooldown + growth_interval` frames, not the
    /// larger of the two. Scaled by the failing rung's
    /// [`BoostRung::a_priori_risk`]: rungs that fail expensively are retried
    /// more cautiously.
    pub cooldown_frames: u32,
    /// Frames to wait after a hard cut.
    pub cut_cooldown_frames: u32,
    /// Highest rung this configuration will ever allow.
    pub max_level: u8,
}

impl Default for QualityConfig {
    fn default() -> Self {
        QualityConfig {
            weights: SignalWeights::default(),
            reject_below: 0.55,
            // ~2 s at 60 Hz.
            growth_interval: 120,
            backoff_rungs: 1,
            // ~1 s at 60 Hz, before the risk scaling.
            cooldown_frames: 60,
            // ~5 s at 60 Hz. Cuts cluster: a cut is usually a cutscene, and a
            // cutscene is usually several more cuts.
            cut_cooldown_frames: 300,
            max_level: MAX_LEVEL,
        }
    }
}

/// Decides how aggressive reconstruction is *allowed* to be.
#[derive(Clone, Debug)]
pub struct QualityGovernor {
    cfg: QualityConfig,
    ceiling: u8,
    clean_streak: u32,
    cooldown: u32,
}

impl QualityGovernor {
    /// A governor that starts optimistic.
    ///
    /// The ceiling begins at the top of the ladder, exactly as `GradScaler`
    /// begins at a large scale factor. That is not the same as boosting hard
    /// out of the gate: the ceiling is only a cap, and [`PerfGovernor`] starts
    /// at native and has to ask. The system therefore opens honest, boosts only
    /// when the frame budget demands it, and finds the quality limit by hitting
    /// it — rather than by guessing conservatively and leaving performance on
    /// the table forever.
    pub fn new(cfg: QualityConfig) -> Self {
        QualityGovernor {
            ceiling: cfg.max_level.min(MAX_LEVEL),
            cfg,
            clean_streak: 0,
            cooldown: 0,
        }
    }

    /// The most aggressive rung currently permitted.
    pub fn ceiling(&self) -> u8 {
        self.ceiling
    }

    /// Frames remaining before the ceiling may climb.
    pub fn cooldown(&self) -> u32 {
        self.cooldown
    }

    /// Consecutive clean frames.
    pub fn clean_streak(&self) -> u32 {
        self.clean_streak
    }

    /// The configuration in force.
    pub fn config(&self) -> &QualityConfig {
        &self.cfg
    }

    /// Score one frame and update the ceiling.
    pub fn evaluate(&mut self, level: u8, signals: &Signals) -> Verdict {
        if level == 0 {
            // Native. The frame on screen is exactly what the renderer drew;
            // there is no reconstruction to distrust, so there is nothing to
            // score. Scoring it anyway is not merely pointless, it is a trap:
            // difficult content pins the ceiling at 0, every subsequent frame
            // is rejected for a reconstruction that never happened, and the
            // controller can never climb out because climbing requires the
            // clean frames it is refusing to grant itself.
            //
            // The streak still advances, so the ceiling recovers and rung 1
            // gets probed again once the cooldown lapses. Content changes;
            // giving up on it permanently would be its own bug.
            self.advance_clean();
            return Verdict::Present { confidence: 1.0 };
        }

        let r = rung(level);
        let confidence = signals.confidence(r, &self.cfg.weights);

        // A cut is checked before the score and only where it can do harm.
        // Interpolating across one does not yield a slightly wrong frame; it
        // yields a dissolve between two unrelated images. There is no gradual
        // response to that, so it does not get one.
        if r.is_temporal() && signals.is_scene_cut(&self.cfg.weights) {
            self.ceiling = highest_spatial_level().min(self.cfg.max_level);
            self.cooldown = self.cfg.cut_cooldown_frames;
            self.clean_streak = 0;
            return Verdict::Discard {
                reason: DiscardReason::SceneCut,
                confidence: confidence.value,
            };
        }

        if confidence.value < self.cfg.reject_below {
            self.back_off(level, r);
            let reason = DiscardReason::LowConfidence(confidence.dominant);
            return if r.is_temporal() {
                // There is a generated frame to throw away, so throw it away.
                Verdict::Discard {
                    reason,
                    confidence: confidence.value,
                }
            } else {
                // The frame was rendered, only smaller. It is going on screen
                // whatever the score says; all the governor can do is stop
                // asking for this rung.
                Verdict::Degrade {
                    reason,
                    confidence: confidence.value,
                }
            };
        }

        self.advance_clean();
        Verdict::Present {
            confidence: confidence.value,
        }
    }

    /// Credit one clean frame toward getting the ceiling back.
    fn advance_clean(&mut self) {
        if self.cooldown > 0 {
            // Probation. The clean streak does not start accumulating until the
            // cooldown expires, so the two are sequential rather than
            // concurrent: serve the sentence, then earn the rung back. Running
            // them concurrently would make whichever is shorter dead
            // configuration — and with sensible values for both, that is always
            // the cooldown.
            self.cooldown -= 1;
            self.clean_streak = 0;
            return;
        }
        self.clean_streak = self.clean_streak.saturating_add(1);
        if self.clean_streak >= self.cfg.growth_interval {
            self.ceiling = (self.ceiling + 1).min(self.cfg.max_level.min(MAX_LEVEL));
            self.clean_streak = 0;
        }
    }

    fn back_off(&mut self, level: u8, r: &BoostRung) {
        // Back off from the rung that actually failed, not from the ceiling.
        // If the perf governor was sitting below the ceiling, the rungs above
        // it were never exercised and there is no evidence against them — but
        // there is now evidence against this one, so the ceiling must land
        // beneath it either way.
        let from = level.min(self.ceiling);
        self.ceiling = from.saturating_sub(self.cfg.backoff_rungs.max(1));
        // Riskier rungs earn longer probation.
        self.cooldown = (self.cfg.cooldown_frames as f32 * (1.0 + r.a_priori_risk)) as u32;
        self.clean_streak = 0;
    }
}

/// Tuning for [`PerfGovernor`].
#[derive(Clone, Copy, Debug)]
pub struct PerfConfig {
    /// Frame time to aim for, in nanoseconds.
    pub target_frame_ns: u64,
    /// EMA smoothing factor for frame time, in `0..1`.
    pub ema_alpha: f32,
    /// How far over target a predicted frame time may sit and still be
    /// accepted.
    ///
    /// Some slack is necessary: without it the governor chases the last
    /// percent of a budget it can only predict approximately, and takes a rung
    /// of image quality to do it.
    pub tolerance: f32,
    /// Consecutive frames agreeing on a different rung before acting.
    pub dwell_frames: u32,
    /// Highest rung this governor will ask for.
    pub max_level: u8,
}

impl PerfConfig {
    /// A configuration targeting a refresh rate.
    pub fn for_target_fps(fps: f32) -> Self {
        PerfConfig {
            target_frame_ns: (1_000_000_000.0 / fps.max(1.0)) as u64,
            ..Default::default()
        }
    }
}

impl Default for PerfConfig {
    fn default() -> Self {
        PerfConfig {
            target_frame_ns: 16_666_667,
            ema_alpha: 0.10,
            tolerance: 1.05,
            // ~1/3 s at 60 Hz. Long enough that a couple of heavy frames do not
            // move the resolution, short enough to react within a camera cut.
            dwell_frames: 20,
            max_level: MAX_LEVEL,
        }
    }
}

/// Decides how aggressive reconstruction *needs* to be.
#[derive(Clone, Debug)]
pub struct PerfGovernor {
    cfg: PerfConfig,
    ema_ns: f64,
    primed: bool,
    desired: u8,
    dwell: u32,
    last_want: u8,
}

impl PerfGovernor {
    /// A governor that starts at native.
    pub fn new(cfg: PerfConfig) -> Self {
        PerfGovernor {
            cfg,
            ema_ns: cfg.target_frame_ns as f64,
            primed: false,
            desired: 0,
            dwell: 0,
            last_want: 0,
        }
    }

    /// The rung currently being asked for.
    pub fn desired(&self) -> u8 {
        self.desired
    }

    /// Smoothed frame time, in nanoseconds.
    pub fn smoothed_frame_ns(&self) -> u64 {
        self.ema_ns as u64
    }

    /// The configuration in force.
    pub fn config(&self) -> &PerfConfig {
        &self.cfg
    }

    /// Estimated cost of a native frame right now, in nanoseconds.
    ///
    /// Measured frame time divided by the cost model's factor for the rung that
    /// produced it. This is the quantity the whole governor turns on, and it is
    /// worth exposing: if it swings wildly as rungs change, the cost model's
    /// ratios are wrong and every decision built on them is guesswork.
    pub fn estimated_native_ns(&self, active_level: u8) -> f64 {
        let cost = rung(active_level).relative_cost().max(1e-3) as f64;
        self.ema_ns / cost
    }

    /// Feed one frame time and update the request.
    ///
    /// `active_level` is the rung that produced this frame — which is the
    /// arbitrated level, not necessarily the one this governor asked for. Using
    /// the wrong one here corrupts the native-cost estimate and with it every
    /// prediction.
    ///
    /// `ceiling` caps the request, so that a long spell under a lowered ceiling
    /// does not leave a pent-up demand that snaps several rungs at once the
    /// moment quality relents.
    pub fn observe(&mut self, frame_ns: u64, active_level: u8, ceiling: u8) -> u8 {
        let sample = frame_ns as f64;
        if !self.primed {
            self.ema_ns = sample;
            self.primed = true;
        } else {
            let a = self.cfg.ema_alpha.clamp(0.0, 1.0) as f64;
            self.ema_ns += a * (sample - self.ema_ns);
        }

        let cap = self.cfg.max_level.min(MAX_LEVEL).min(ceiling);
        let budget = self.cfg.target_frame_ns as f64 * self.cfg.tolerance.max(1.0) as f64;
        let native = self.estimated_native_ns(active_level);

        // The least aggressive rung that fits. If none fits, take the most
        // aggressive available and miss the budget honestly — there is nothing
        // further to trade.
        let want = (0..=cap)
            .find(|&l| native * rung(l).relative_cost() as f64 <= budget)
            .unwrap_or(cap);

        if want == self.desired {
            self.dwell = 0;
        } else {
            // Reset the dwell if the target itself moved: a rung has to be
            // wanted consistently, not merely wanted repeatedly.
            if want != self.last_want {
                self.dwell = 0;
            }
            self.dwell += 1;
            if self.dwell >= self.cfg.dwell_frames {
                // One rung at a time, even when the model wants a leap. A large
                // jump changes render resolution and frame cadence at once, and
                // is more noticeable than arriving a few frames later.
                self.desired = if want > self.desired {
                    self.desired + 1
                } else {
                    self.desired - 1
                };
                self.dwell = 0;
            }
        }
        self.last_want = want;

        self.desired = self.desired.min(cap);
        self.desired
    }
}

/// Tuning for [`BoostController`].
#[derive(Clone, Copy, Debug)]
pub struct ControllerConfig {
    /// Output width in pixels.
    pub output_width: u32,
    /// Output height in pixels.
    pub output_height: u32,
    /// Quality governor tuning.
    pub quality: QualityConfig,
    /// Perf governor tuning.
    pub perf: PerfConfig,
}

impl ControllerConfig {
    /// A configuration for an output size and refresh rate.
    pub fn for_target_fps(output_width: u32, output_height: u32, fps: f32) -> Self {
        ControllerConfig {
            output_width,
            output_height,
            quality: QualityConfig::default(),
            perf: PerfConfig::for_target_fps(fps),
            }
    }
}

/// What happened to one frame.
#[derive(Clone, Copy, Debug)]
pub struct FrameOutcome {
    /// Monotonic frame index.
    pub frame: u64,
    /// Rung in use.
    pub level: u8,
    /// Quality ceiling after this frame.
    pub ceiling: u8,
    /// The decision.
    pub verdict: Verdict,
    /// Frames actually handed to the display, generated ones included.
    pub frames_presented: u32,
}

/// The arbiter: runs both governors and enforces the veto.
///
/// Call [`BoostController::plan`] before rendering and
/// [`BoostController::resolve`] after reconstructing, once per rendered frame.
#[derive(Clone, Debug)]
pub struct BoostController {
    cfg: ControllerConfig,
    quality: QualityGovernor,
    perf: PerfGovernor,
    active: u8,
    frame: u64,
    telemetry: Telemetry,
}

impl BoostController {
    /// Build a controller.
    pub fn new(cfg: ControllerConfig) -> Self {
        BoostController {
            quality: QualityGovernor::new(cfg.quality),
            perf: PerfGovernor::new(cfg.perf),
            cfg,
            active: 0,
            frame: 0,
            telemetry: Telemetry::default(),
        }
    }

    /// Decide what to render this frame.
    ///
    /// `last_frame_ns` is the measured GPU time of the previous frame. Pass the
    /// target frame time on the very first call.
    pub fn plan(&mut self, last_frame_ns: u64) -> BoostPlan {
        let ceiling = self.quality.ceiling();
        // `self.active` still holds the rung that produced `last_frame_ns`.
        let desired = self.perf.observe(last_frame_ns, self.active, ceiling);

        // The veto, in one line.
        self.active = desired.min(ceiling);

        let r = rung(self.active);
        let (render_width, render_height) =
            r.render_size(self.cfg.output_width, self.cfg.output_height);
        BoostPlan {
            level: self.active,
            rung: r,
            render_width,
            render_height,
            generate: r.generated_per_rendered > 0,
        }
    }

    /// Judge the reconstruction and update the ceiling.
    pub fn resolve(&mut self, signals: &Signals) -> FrameOutcome {
        let level = self.active;
        let r = rung(level);
        let verdict = self.quality.evaluate(level, signals);

        let frames_presented = match verdict {
            // A discarded generated frame means the previous rendered frame is
            // shown again in its slot, so exactly one frame reaches the display.
            Verdict::Discard { .. } => 1,
            _ => r.presented_per_rendered(),
        };

        let (kind, dominant) = match verdict {
            Verdict::Present { .. } => (DecisionKind::Presented, SignalKind::Disocclusion),
            Verdict::Discard { reason, .. } => (DecisionKind::Discarded, reason_signal(reason)),
            Verdict::Degrade { reason, .. } => (DecisionKind::Degraded, reason_signal(reason)),
        };

        let outcome = FrameOutcome {
            frame: self.frame,
            level,
            ceiling: self.quality.ceiling(),
            verdict,
            frames_presented,
        };

        self.telemetry.record(
            Decision {
                frame: self.frame,
                level,
                ceiling: self.quality.ceiling(),
                confidence: verdict.confidence(),
                kind,
                dominant,
                scene_cut: matches!(
                    verdict,
                    Verdict::Discard {
                        reason: DiscardReason::SceneCut,
                        ..
                    }
                ),
            },
            frames_presented,
        );

        self.frame += 1;
        outcome
    }

    /// The rung in use.
    pub fn level(&self) -> u8 {
        self.active
    }

    /// The rung in use.
    pub fn rung(&self) -> &'static BoostRung {
        rung(self.active)
    }

    /// The quality ceiling.
    pub fn ceiling(&self) -> u8 {
        self.quality.ceiling()
    }

    /// The rung performance is asking for.
    pub fn desired(&self) -> u8 {
        self.perf.desired()
    }

    /// The decision log.
    pub fn telemetry(&self) -> &Telemetry {
        &self.telemetry
    }

    /// The quality governor.
    pub fn quality(&self) -> &QualityGovernor {
        &self.quality
    }

    /// The performance governor.
    pub fn perf(&self) -> &PerfGovernor {
        &self.perf
    }
}

fn reason_signal(reason: DiscardReason) -> SignalKind {
    match reason {
        DiscardReason::LowConfidence(k) => k,
        DiscardReason::SceneCut => SignalKind::MotionResidual,
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::level::level_by_name;

    const OVER_BUDGET: u64 = 25_000_000; // 40 fps against a 60 fps target
    const UNDER_BUDGET: u64 = 8_000_000; // 125 fps

    fn controller() -> BoostController {
        BoostController::new(ControllerConfig::for_target_fps(3840, 2160, 60.0))
    }

    fn run(ctl: &mut BoostController, frames: u32, frame_ns: u64, s: &Signals) {
        for _ in 0..frames {
            ctl.plan(frame_ns);
            ctl.resolve(s);
        }
    }

    #[test]
    fn starts_honest() {
        // Optimistic ceiling, but nothing is boosted until the budget asks.
        let ctl = controller();
        assert_eq!(ctl.level(), 0);
        assert_eq!(ctl.ceiling(), MAX_LEVEL);
    }

    #[test]
    fn climbs_when_over_budget_and_clean() {
        let mut ctl = controller();
        run(&mut ctl, 400, OVER_BUDGET, &Signals::clean());
        assert!(ctl.level() > 0, "level {}", ctl.level());
        assert!(ctl.telemetry().boost_ratio() >= 1.0);
    }

    #[test]
    fn gives_rungs_back_when_headroom_appears() {
        let mut ctl = controller();
        run(&mut ctl, 400, OVER_BUDGET, &Signals::clean());
        let boosted = ctl.level();
        assert!(boosted > 0);
        run(&mut ctl, 600, UNDER_BUDGET, &Signals::clean());
        assert!(
            ctl.level() < boosted,
            "expected to give rungs back: {boosted} -> {}",
            ctl.level()
        );
    }

    #[test]
    fn holds_still_when_the_budget_is_met() {
        let mut ctl = controller();
        let target = ctl.perf().config().target_frame_ns;
        run(&mut ctl, 500, target, &Signals::clean());
        assert_eq!(ctl.level(), 0);
    }

    /// Frame time responds to the rung, as it does in a renderer.
    fn run_closed_loop(ctl: &mut BoostController, frames: u32, native_ns: u64, s: &Signals) -> Vec<u8> {
        let mut frame_ns = native_ns;
        let mut levels = Vec::with_capacity(frames as usize);
        for _ in 0..frames {
            let plan = ctl.plan(frame_ns);
            ctl.resolve(s);
            frame_ns = (native_ns as f64 * plan.rung.relative_cost() as f64) as u64;
            levels.push(plan.level);
        }
        levels
    }

    #[test]
    fn does_not_hunt_between_rungs() {
        // The reason the governor inverts the cost model instead of stepping
        // toward the budget. Adjacent rungs differ in cost by up to 1.76x, so a
        // bang-bang controller overshoots going up and undershoots coming down,
        // and oscillates between two rungs indefinitely — visibly, because each
        // flip changes render resolution.
        for native_ms in [20.0f64, 35.0, 55.0, 70.0, 120.0, 400.0] {
            let mut ctl = controller();
            let native_ns = (native_ms * 1e6) as u64;
            let levels = run_closed_loop(&mut ctl, 2000, native_ns, &Signals::clean());
            let settled = &levels[1500..];
            let first = settled[0];
            assert!(
                settled.iter().all(|l| *l == first),
                "hunted at {native_ms} ms native: settled to {:?}",
                &settled[..40.min(settled.len())]
            );
        }
    }

    #[test]
    fn settles_on_a_rung_that_actually_meets_the_budget() {
        // Settling is necessary but not sufficient: it has to settle on the
        // right rung, and on the *least* aggressive one that works, so no image
        // quality is given away for headroom nobody asked for.
        let mut ctl = controller();
        let native_ns = 70_000_000u64;
        run_closed_loop(&mut ctl, 2000, native_ns, &Signals::clean());

        let level = ctl.level();
        let achieved = native_ns as f64 * rung(level).relative_cost() as f64;
        let budget = ctl.perf().config().target_frame_ns as f64 * 1.05;
        assert!(achieved <= budget, "rung {level} misses the budget: {achieved} ns");

        if level > 0 {
            let one_less = native_ns as f64 * rung(level - 1).relative_cost() as f64;
            assert!(one_less > budget, "rung {} would have done: overshot", level - 1);
        }
    }

    #[test]
    fn the_native_cost_estimate_is_stable_across_rungs() {
        // The premise of model inversion: measuring at one rung must predict
        // the others. If this drifts as the ladder is climbed, the cost model's
        // ratios are wrong and every decision resting on them is a guess.
        let native_ns = 90_000_000u64;
        for level in 0..=MAX_LEVEL {
            let mut perf = PerfGovernor::new(PerfConfig::default());
            let frame_ns = (native_ns as f64 * rung(level).relative_cost() as f64) as u64;
            for _ in 0..200 {
                perf.observe(frame_ns, level, level);
            }
            let est = perf.estimated_native_ns(level);
            let err = (est - native_ns as f64).abs() / native_ns as f64;
            assert!(err < 0.01, "rung {level} estimated {est} ns, off by {err:.3}");
        }
    }

    #[test]
    fn quality_vetoes_performance() {
        // The central rule. Frame time is screaming for help and every frame is
        // a disocclusion mess; the controller must still refuse to boost.
        let mut ctl = controller();
        let awful = Signals {
            disocclusion: 0.95,
            depth_complexity: 0.95,
            ..Signals::clean()
        };
        run(&mut ctl, 1000, OVER_BUDGET, &awful);
        assert_eq!(ctl.level(), 0, "boosted into a scene it cannot reconstruct");
        assert_eq!(ctl.ceiling(), 0);
    }

    #[test]
    fn a_generated_frame_is_discarded_not_shown() {
        let mut ctl = controller();
        run(&mut ctl, 900, OVER_BUDGET, &Signals::clean());
        // Force a temporal rung.
        let gen_level = level_by_name("performance_gen").unwrap();
        if ctl.level() < gen_level {
            run(&mut ctl, 2000, OVER_BUDGET, &Signals::clean());
        }
        assert!(ctl.rung().is_temporal(), "test needs a temporal rung");

        ctl.plan(OVER_BUDGET);
        let out = ctl.resolve(&Signals {
            disocclusion: 0.95,
            ..Signals::clean()
        });
        assert!(matches!(out.verdict, Verdict::Discard { .. }));
        // The whole point: the display gets one honest frame, not two frames
        // one of which is wrong.
        assert_eq!(out.frames_presented, 1);
    }

    #[test]
    fn native_frames_are_never_rejected() {
        // At rung 0 nothing was reconstructed. Judging the frame anyway pins
        // the ceiling at 0 forever, because escaping requires clean frames the
        // governor is busy refusing to grant itself.
        let mut q = QualityGovernor::new(QualityConfig::default());
        let hopeless = Signals {
            disocclusion: 1.0,
            motion_residual: 1.0,
            luma_shift: 1.0,
            camera_motion: 1.0,
            depth_complexity: 1.0,
            pacing_instability: 1.0,
        };
        for _ in 0..500 {
            assert!(
                matches!(q.evaluate(0, &hopeless), Verdict::Present { .. }),
                "native must always present"
            );
        }
        // And it must be able to climb back out and probe rung 1 again.
        assert!(q.ceiling() > 0, "ceiling never recovered from native");
    }

    #[test]
    fn difficult_content_settles_instead_of_collapsing() {
        // Content a spatial upscaler genuinely cannot handle should park the
        // controller low and keep it there — probing occasionally, never stuck
        // in a permanent rejection state.
        let mut ctl = controller();
        let unresolvable = Signals {
            depth_complexity: 1.0,
            camera_motion: 1.0,
            ..Signals::clean()
        };
        run(&mut ctl, 3000, OVER_BUDGET, &unresolvable);
        assert!(ctl.level() <= 1, "boosted into content it cannot resolve");
        assert!(
            ctl.telemetry().discard_rate() < 0.20,
            "thrashing rather than settling: {:.2}",
            ctl.telemetry().discard_rate()
        );
    }

    #[test]
    fn a_spatial_rung_degrades_rather_than_discarding() {
        // You cannot un-upscale a frame you already rendered.
        let mut q = QualityGovernor::new(QualityConfig::default());
        let spatial = level_by_name("balanced").unwrap();
        let v = q.evaluate(
            spatial,
            &Signals {
                depth_complexity: 1.0,
                camera_motion: 1.0,
                ..Signals::clean()
            },
        );
        assert!(matches!(v, Verdict::Degrade { .. }), "{v:?}");
    }

    #[test]
    fn a_scene_cut_retreats_to_spatial_not_to_native() {
        let mut q = QualityGovernor::new(QualityConfig::default());
        let gen = level_by_name("performance_gen").unwrap();
        let v = q.evaluate(gen, &Signals::scene_cut());
        assert_eq!(
            v,
            Verdict::Discard {
                reason: DiscardReason::SceneCut,
                confidence: 0.0
            }
        );
        // Surrendering the spatial boost too would spike frame time at exactly
        // the wrong moment.
        assert_eq!(q.ceiling(), highest_spatial_level());
        assert_eq!(q.cooldown(), QualityConfig::default().cut_cooldown_frames);
    }

    #[test]
    fn cooldown_blocks_an_immediate_reclimb() {
        let mut q = QualityGovernor::new(QualityConfig::default());
        let start = q.ceiling();
        q.evaluate(
            start,
            &Signals {
                disocclusion: 1.0,
                ..Signals::clean()
            },
        );
        let dropped = q.ceiling();
        assert!(dropped < start);
        // Comfortably more than growth_interval on its own, but short of
        // cooldown + growth_interval. Nothing should have moved.
        let cfg = QualityConfig::default();
        for _ in 0..(cfg.growth_interval + 5) {
            q.evaluate(dropped, &Signals::clean());
        }
        assert_eq!(q.ceiling(), dropped, "climbed back during cooldown");
    }

    #[test]
    fn recovers_after_the_cooldown_expires() {
        let mut q = QualityGovernor::new(QualityConfig::default());
        let start = q.ceiling();
        q.evaluate(
            start,
            &Signals {
                disocclusion: 1.0,
                ..Signals::clean()
            },
        );
        let dropped = q.ceiling();
        for _ in 0..3000 {
            q.evaluate(dropped, &Signals::clean());
        }
        assert!(q.ceiling() > dropped, "never recovered");
        assert_eq!(q.ceiling(), start, "should recover all the way");
    }

    #[test]
    fn riskier_rungs_earn_longer_probation() {
        let bad = Signals {
            disocclusion: 1.0,
            motion_residual: 1.0,
            depth_complexity: 1.0,
            camera_motion: 1.0,
            ..Signals::clean()
        };
        let mut low = QualityGovernor::new(QualityConfig::default());
        low.evaluate(level_by_name("quality").unwrap(), &bad);
        let mut high = QualityGovernor::new(QualityConfig::default());
        high.evaluate(level_by_name("ultra_performance_gen").unwrap(), &bad);
        assert!(high.cooldown() > low.cooldown());
    }

    #[test]
    fn recovery_is_gradual_not_a_snap_back() {
        // While the ceiling is held down, the perf governor must not accumulate
        // a demand it then satisfies all at once.
        let mut ctl = controller();
        let awful = Signals {
            disocclusion: 0.99,
            depth_complexity: 0.99,
            ..Signals::clean()
        };
        run(&mut ctl, 1500, OVER_BUDGET, &awful);
        assert_eq!(ctl.level(), 0);
        assert_eq!(ctl.desired(), 0, "perf demand should be pinned to the ceiling");

        // Now the scene calms down; the climb should be one rung at a time.
        let mut jumps = 0;
        let mut prev = ctl.level();
        for _ in 0..3000 {
            ctl.plan(OVER_BUDGET);
            ctl.resolve(&Signals::clean());
            if ctl.level() > prev + 1 {
                jumps += 1;
            }
            prev = ctl.level();
        }
        assert_eq!(jumps, 0, "level jumped more than one rung at a time");
        assert!(ctl.level() > 0, "never recovered");
    }

    #[test]
    fn backs_off_from_the_rung_that_failed() {
        // With the perf governor parked low, a failure at a low rung must pull
        // the ceiling beneath *that* rung, not merely one below an untested top.
        let mut q = QualityGovernor::new(QualityConfig::default());
        assert_eq!(q.ceiling(), MAX_LEVEL);
        let low = 1u8;
        q.evaluate(
            low,
            &Signals {
                depth_complexity: 1.0,
                camera_motion: 1.0,
                ..Signals::clean()
            },
        );
        assert_eq!(q.ceiling(), 0);
    }

    #[test]
    fn telemetry_tracks_the_whole_run() {
        let mut ctl = controller();
        run(&mut ctl, 200, OVER_BUDGET, &Signals::clean());
        let t = ctl.telemetry();
        assert_eq!(t.frames, 200);
        assert!(t.mean_relative_cost() <= 1.05);
        assert!(!t.summary().is_empty());
    }
}
