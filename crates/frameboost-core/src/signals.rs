//! Per-frame evidence that reconstruction is going wrong.
//!
//! This is the `inf`/`NaN` check of the analogy. Loss scaling has it easy: an
//! overflowed gradient is a bit pattern, and the test is exact. There is no bit
//! pattern for "this generated frame has a smear where the character's arm
//! was", so the check has to be assembled from cheap proxies that correlate
//! with the artifacts people actually see.
//!
//! Six signals, each normalized to `0..=1` where 0 is benign:
//!
//! | Signal | What it catches |
//! |---|---|
//! | [`SignalKind::Disocclusion`] | Pixels with no source in either endpoint — the holes frame generation has to invent. The single strongest predictor of visible smear. |
//! | [`SignalKind::MotionResidual`] | Change the motion vectors do not explain: particles, alpha, shader animation, lighting. Exactly the content frame generation destroys, and exactly what the G-buffer will not warn you about. |
//! | [`SignalKind::LumaShift`] | Global brightness change between endpoints — an exposure adaptation, a muzzle flash, a cut. |
//! | [`SignalKind::CameraMotion`] | Screen-space velocity. Fast motion enlarges every other error and pushes more of the frame into disocclusion. |
//! | [`SignalKind::DepthComplexity`] | Density of depth discontinuities. Foliage, fences, wires and hair are where reprojection reliably falls apart. |
//! | [`SignalKind::PacingInstability`] | Variance in rendered-frame intervals. A generated frame shown at the wrong moment is worse than no generated frame at all. |
//!
//! # Why the weights depend on the rung
//!
//! A spatial upscaler does not reproject, so disocclusion and motion residual
//! say nothing about whether its output is good — scoring them would make the
//! controller abandon a perfectly healthy rung during a fast pan. What actually
//! hurts a spatial upscaler is thin geometry and motion-induced shimmer. So
//! [`SignalWeights`] carries two weight sets and [`Signals::confidence`] picks
//! by [`BoostRung::is_temporal`].
//!
//! # Why penalties are multiplicative, with deadbands
//!
//! Multiplicative, because these failures are not additive nuisances that
//! average out: any *one* of them at full strength is enough to ruin the frame
//! on its own, and a weight above 1.0 lets a single signal veto outright.
//!
//! With deadbands, because every real frame has some of all six. A few percent
//! of the screen disoccludes every time the camera moves; that is not a defect
//! and must cost nothing. Each signal is therefore free below a threshold and
//! only its excess is scored. Without deadbands, six benign signals multiply
//! into a rejection and the controller never leaves the bottom rung.

use crate::frame::GBuffer;
use crate::level::BoostRung;

/// Which signal is being talked about.
#[derive(Clone, Copy, PartialEq, Eq, Debug)]
pub enum SignalKind {
    /// Fraction of output pixels with no valid source.
    Disocclusion,
    /// Change unexplained by the motion vectors.
    MotionResidual,
    /// Global luminance change between endpoints.
    LumaShift,
    /// Screen-space camera velocity.
    CameraMotion,
    /// Density of depth discontinuities.
    DepthComplexity,
    /// Variance in rendered-frame intervals.
    PacingInstability,
}

impl SignalKind {
    /// All signals, in the order [`Signals::as_array`] uses.
    pub const ALL: [SignalKind; 6] = [
        SignalKind::Disocclusion,
        SignalKind::MotionResidual,
        SignalKind::LumaShift,
        SignalKind::CameraMotion,
        SignalKind::DepthComplexity,
        SignalKind::PacingInstability,
    ];

    /// Short stable name, for logs.
    pub const fn name(self) -> &'static str {
        match self {
            SignalKind::Disocclusion => "disocclusion",
            SignalKind::MotionResidual => "motion_residual",
            SignalKind::LumaShift => "luma_shift",
            SignalKind::CameraMotion => "camera_motion",
            SignalKind::DepthComplexity => "depth_complexity",
            SignalKind::PacingInstability => "pacing_instability",
        }
    }
}

/// One frame's worth of evidence. Every field is normalized to `0..=1`.
#[derive(Clone, Copy, Debug, Default, PartialEq)]
pub struct Signals {
    /// Fraction of output pixels that had no valid source in either endpoint.
    pub disocclusion: f32,
    /// Normalized residual after reprojecting one endpoint onto the other.
    pub motion_residual: f32,
    /// Normalized mean-luminance delta between endpoints.
    pub luma_shift: f32,
    /// Screen-space camera velocity, normalized against a reference speed.
    pub camera_motion: f32,
    /// Fraction of pixels sitting on a depth discontinuity.
    pub depth_complexity: f32,
    /// Normalized dispersion of recent rendered-frame intervals.
    pub pacing_instability: f32,
}

impl Signals {
    /// A frame with nothing wrong with it.
    pub fn clean() -> Self {
        Signals {
            disocclusion: 0.02,
            motion_residual: 0.03,
            luma_shift: 0.01,
            camera_motion: 0.10,
            depth_complexity: 0.12,
            pacing_instability: 0.02,
        }
    }

    /// A hard cut: the motion vectors explain nothing and the image changed
    /// wholesale.
    pub fn scene_cut() -> Self {
        Signals {
            disocclusion: 0.85,
            motion_residual: 0.95,
            luma_shift: 0.70,
            camera_motion: 0.40,
            depth_complexity: 0.30,
            pacing_instability: 0.05,
        }
    }

    /// Field order matching [`SignalKind::ALL`].
    pub fn as_array(&self) -> [f32; 6] {
        [
            self.disocclusion,
            self.motion_residual,
            self.luma_shift,
            self.camera_motion,
            self.depth_complexity,
            self.pacing_instability,
        ]
    }

    /// Clamp every field into `0..=1`.
    ///
    /// Measurement is approximate and callers pass in numbers from all sorts of
    /// places; scoring an out-of-range signal would silently corrupt the
    /// confidence product rather than fail loudly.
    pub fn sanitized(&self) -> Self {
        let c = |v: f32| if v.is_finite() { v.clamp(0.0, 1.0) } else { 1.0 };
        Signals {
            disocclusion: c(self.disocclusion),
            motion_residual: c(self.motion_residual),
            luma_shift: c(self.luma_shift),
            camera_motion: c(self.camera_motion),
            depth_complexity: c(self.depth_complexity),
            pacing_instability: c(self.pacing_instability),
        }
    }

    /// Whether this looks like a hard cut rather than continuous motion.
    ///
    /// Requires *both* an unexplained image and a luminance jump. Either alone
    /// is common and benign — a fast whip pan wrecks the residual without being
    /// a cut, and a fade wrecks the luma without being one either. Together
    /// they are close to diagnostic.
    ///
    /// This is the one condition that bypasses the confidence score entirely.
    /// Interpolating across a cut does not produce a slightly wrong frame; it
    /// produces a frame that dissolves between two unrelated images, and no
    /// amount of gradual back-off is the right response.
    pub fn is_scene_cut(&self, w: &SignalWeights) -> bool {
        let s = self.sanitized();
        s.motion_residual >= w.cut_residual && s.luma_shift >= w.cut_luma
    }

    /// Score this frame for a given rung.
    pub fn confidence(&self, rung: &BoostRung, w: &SignalWeights) -> Confidence {
        let s = self.sanitized().as_array();
        let weights = if rung.is_temporal() {
            &w.temporal
        } else {
            &w.spatial
        };

        let mut value = 1.0f32;
        let mut dominant = SignalKind::Disocclusion;
        let mut dominant_penalty = 0.0f32;

        for (i, kind) in SignalKind::ALL.iter().enumerate() {
            let penalty = weights[i].penalty(s[i]);
            if penalty > dominant_penalty {
                dominant_penalty = penalty;
                dominant = *kind;
            }
            value *= (1.0 - penalty).clamp(0.0, 1.0);
        }

        Confidence {
            value,
            dominant,
            dominant_penalty,
        }
    }
}

/// The result of scoring a frame.
#[derive(Clone, Copy, Debug)]
pub struct Confidence {
    /// How much to trust this reconstruction, in `0..=1`.
    pub value: f32,
    /// The signal that contributed the largest single penalty.
    ///
    /// Carried so that a rejection can say *why*. A controller that only
    /// reports "confidence 0.31" is untunable; one that reports "confidence
    /// 0.31, disocclusion" tells you which knob to reach for.
    pub dominant: SignalKind,
    /// That signal's penalty, in `0..=1`.
    pub dominant_penalty: f32,
}

/// Weight and deadband for one signal.
#[derive(Clone, Copy, Debug)]
pub struct SignalWeight {
    /// Multiplier on the signal's excess. Above 1.0, the signal can veto alone.
    pub weight: f32,
    /// Value below which the signal is free.
    pub deadband: f32,
}

impl SignalWeight {
    /// Construct a weight.
    pub const fn new(weight: f32, deadband: f32) -> Self {
        SignalWeight { weight, deadband }
    }

    /// Ignore this signal entirely.
    pub const fn ignored() -> Self {
        SignalWeight {
            weight: 0.0,
            deadband: 1.0,
        }
    }

    /// Penalty contributed by a signal value, in `0..=1`.
    ///
    /// Excess above the deadband is rescaled so that a signal at 1.0 always
    /// yields full excess regardless of where the deadband sits — otherwise
    /// raising a deadband would quietly weaken the signal at its extreme, which
    /// is the one place it must not be weakened.
    pub fn penalty(&self, value: f32) -> f32 {
        if self.weight <= 0.0 {
            return 0.0;
        }
        let span = (1.0 - self.deadband).max(1e-4);
        let excess = ((value - self.deadband) / span).clamp(0.0, 1.0);
        (self.weight * excess).clamp(0.0, 1.0)
    }
}

/// How each signal is scored, per rung class.
#[derive(Clone, Copy, Debug)]
pub struct SignalWeights {
    /// Weights for rungs that only upscale spatially.
    pub spatial: [SignalWeight; 6],
    /// Weights for rungs that reproject across time.
    pub temporal: [SignalWeight; 6],
    /// Motion residual at or above which a cut is possible.
    pub cut_residual: f32,
    /// Luma shift at or above which a cut is possible.
    pub cut_luma: f32,
}

impl Default for SignalWeights {
    fn default() -> Self {
        SignalWeights {
            // A spatial upscaler is blind to everything temporal. What breaks it
            // is sub-pixel detail it cannot resolve: thin geometry, and the
            // shimmer that appears when such geometry moves.
            spatial: [
                SignalWeight::ignored(),          // disocclusion: no reprojection
                SignalWeight::ignored(),          // motion residual: not used
                SignalWeight::ignored(),          // luma shift: harmless
                SignalWeight::new(0.35, 0.45),    // camera motion: shimmer
                SignalWeight::new(0.80, 0.35),    // depth complexity: thin geometry
                SignalWeight::ignored(),          // pacing: no generated frames
            ],
            // Frame generation is exposed to all of it. Disocclusion and motion
            // residual carry weights above 1.0: either one, saturated, is on its
            // own sufficient reason to throw the frame away.
            temporal: [
                SignalWeight::new(1.70, 0.06),
                SignalWeight::new(1.50, 0.10),
                SignalWeight::new(1.00, 0.08),
                SignalWeight::new(0.50, 0.25),
                SignalWeight::new(0.60, 0.30),
                SignalWeight::new(0.90, 0.15),
            ],
            cut_residual: 0.55,
            cut_luma: 0.40,
        }
    }
}

/// Measure the rung-independent signals from a pair of consecutive G-buffers.
///
/// `next.motion` is taken to point from each pixel in `next` back to where that
/// surface was in `prev`, in pixels — the convention the rest of this crate
/// uses. Returns [`Signals`] with [`Signals::disocclusion`] and
/// [`Signals::pacing_instability`] left at zero: the first is produced by
/// reconstruction itself (see [`crate::recon::interpolate_midpoint`]) and the
/// second by [`crate::pacing::FramePacer`].
///
/// `reference_speed` is the screen-space velocity in pixels per frame that
/// should read as full [`Signals::camera_motion`]. Roughly a tenth of screen
/// width per frame is a sensible starting point: fast enough to be a whip pan,
/// slow enough that ordinary strafing does not saturate it.
pub fn measure_pair(prev: &GBuffer, next: &GBuffer, reference_speed: f32) -> Signals {
    debug_assert_eq!(prev.width(), next.width());
    debug_assert_eq!(prev.height(), next.height());

    let (w, h) = (next.width(), next.height());
    let n = (w * h) as f32;

    let mut residual = 0.0f64;
    let mut residual_norm = 0.0f64;
    let mut speed = 0.0f64;
    let mut luma_prev = 0.0f64;
    let mut luma_next = 0.0f64;
    let mut discontinuities = 0u32;

    for y in 0..h {
        for x in 0..w {
            let here = next.color.at(x, y);
            let l_next = luma(here);
            luma_next += l_next as f64;
            luma_prev += luma(prev.color.at(x, y)) as f64;

            let mv = next.motion.at(x, y);
            speed += ((mv[0] * mv[0] + mv[1] * mv[1]).sqrt()) as f64;

            // Reproject: where this surface was in the previous frame.
            let sx = x as f32 + 0.5 + mv[0];
            let sy = y as f32 + 0.5 + mv[1];
            let there = prev.color.sample_bilinear(sx, sy);
            let l_there = luma(there);

            // Normalize by local magnitude so bright areas do not dominate the
            // average purely for being bright.
            let denom = (l_next.abs() + l_there.abs()).max(1e-3);
            residual += ((l_next - l_there).abs() / denom) as f64;
            residual_norm += 1.0;

            // Depth discontinuity: a large relative step to the right or down.
            let d = next.depth.at(x, y);
            let dx = next.depth.at((x + 1).min(w - 1), y);
            let dy = next.depth.at(x, (y + 1).min(h - 1));
            let rel = |a: f32, b: f32| (a - b).abs() / a.abs().max(b.abs()).max(1e-4);
            if rel(d, dx) > 0.10 || rel(d, dy) > 0.10 {
                discontinuities += 1;
            }
        }
    }

    let mean_residual = if residual_norm > 0.0 {
        (residual / residual_norm) as f32
    } else {
        0.0
    };
    let mean_speed = (speed / n as f64) as f32;
    let luma_delta = ((luma_next - luma_prev).abs() / n as f64) as f32;

    Signals {
        disocclusion: 0.0,
        // The raw quantity is a mean of |Δluma| / (|a| + |b|), so it is 0 for
        // identical images and tends to about 1/3 for unrelated ones — that is
        // the practical range, not 0..1, and the scaling has to be anchored to
        // it or the useful part of the signal is squeezed into a sliver.
        //
        // Measured on the simulator's scenarios: a clean pair with exact motion
        // vectors sits near 0.013, and a frame full of content the vectors do
        // not describe reaches 0.175. Saturating at 0.25 puts the clean case
        // comfortably inside the deadband and the severe case unambiguously
        // past rejection, instead of landing both within a rounding error of
        // the threshold.
        motion_residual: (mean_residual * 4.0).clamp(0.0, 1.0),
        // Scale so a mean-luma jump of 0.25 saturates — an exposure change that
        // large between adjacent frames is not something to interpolate through.
        luma_shift: (luma_delta * 4.0).clamp(0.0, 1.0),
        camera_motion: (mean_speed / reference_speed.max(1e-3)).clamp(0.0, 1.0),
        depth_complexity: (discontinuities as f32 / n).clamp(0.0, 1.0),
        pacing_instability: 0.0,
    }
}

/// Rec. 709 relative luminance.
#[inline]
pub fn luma(c: [f32; 4]) -> f32 {
    0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::level::{level_by_name, rung};

    fn spatial() -> &'static BoostRung {
        rung(level_by_name("quality").unwrap())
    }
    fn temporal() -> &'static BoostRung {
        rung(level_by_name("performance_gen").unwrap())
    }

    #[test]
    fn a_clean_frame_is_trusted_everywhere() {
        let w = SignalWeights::default();
        let s = Signals::clean();
        // The deadbands exist precisely so that this holds. If it ever fails,
        // the controller can never climb and the whole crate is pointless.
        assert!(s.confidence(spatial(), &w).value > 0.95);
        assert!(s.confidence(temporal(), &w).value > 0.95);
    }

    #[test]
    fn disocclusion_sinks_temporal_but_not_spatial() {
        let w = SignalWeights::default();
        let s = Signals {
            disocclusion: 0.75,
            ..Signals::clean()
        };
        assert!(s.confidence(temporal(), &w).value < 0.15);
        // A spatial upscaler does not reproject, so a screen full of
        // disocclusion is simply not its problem.
        assert!(s.confidence(spatial(), &w).value > 0.95);
    }

    #[test]
    fn thin_geometry_sinks_spatial() {
        let w = SignalWeights::default();
        let s = Signals {
            depth_complexity: 0.95,
            ..Signals::clean()
        };
        assert!(s.confidence(spatial(), &w).value < 0.6);
    }

    #[test]
    fn a_saturated_veto_signal_zeroes_confidence_alone() {
        let w = SignalWeights::default();
        for (kind, s) in [
            (
                SignalKind::Disocclusion,
                Signals {
                    disocclusion: 1.0,
                    ..Default::default()
                },
            ),
            (
                SignalKind::MotionResidual,
                Signals {
                    motion_residual: 1.0,
                    ..Default::default()
                },
            ),
        ] {
            let c = s.confidence(temporal(), &w);
            assert_eq!(c.value, 0.0, "{kind:?} should be able to veto alone");
            assert_eq!(c.dominant, kind);
        }
    }

    #[test]
    fn dominant_signal_is_reported() {
        let w = SignalWeights::default();
        let s = Signals {
            disocclusion: 0.10,
            motion_residual: 0.60,
            ..Signals::clean()
        };
        assert_eq!(s.confidence(temporal(), &w).dominant, SignalKind::MotionResidual);
    }

    #[test]
    fn a_cut_needs_both_residual_and_luma() {
        let w = SignalWeights::default();
        assert!(Signals::scene_cut().is_scene_cut(&w));
        // A whip pan wrecks the residual without being a cut.
        let whip = Signals {
            motion_residual: 0.90,
            luma_shift: 0.05,
            ..Signals::clean()
        };
        assert!(!whip.is_scene_cut(&w));
        // A fade wrecks the luma without being one either.
        let fade = Signals {
            motion_residual: 0.10,
            luma_shift: 0.80,
            ..Signals::clean()
        };
        assert!(!fade.is_scene_cut(&w));
    }

    #[test]
    fn deadband_rescaling_preserves_the_extreme() {
        // Raising a deadband must not weaken a saturated signal, or tuning for
        // fewer false positives would quietly disarm the true ones.
        for deadband in [0.0, 0.3, 0.6, 0.9] {
            let sw = SignalWeight::new(1.0, deadband);
            assert!((sw.penalty(1.0) - 1.0).abs() < 1e-5);
            assert_eq!(sw.penalty(deadband), 0.0);
        }
    }

    #[test]
    fn non_finite_signals_fail_closed() {
        let w = SignalWeights::default();
        let s = Signals {
            disocclusion: f32::NAN,
            ..Signals::clean()
        };
        // A NaN from a broken reduction must read as "maximally bad", never as
        // a comparison that silently returns false and lets the frame through.
        assert_eq!(s.confidence(temporal(), &w).value, 0.0);
    }
}
