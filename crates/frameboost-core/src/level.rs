//! The boost ladder.
//!
//! Boost is not a switch. It is an ordered ladder of rungs, each cheaper and
//! riskier than the one below it, and the controller's whole job is deciding
//! which rung the current frame has earned.
//!
//! Two orthogonal levers are folded into that single order:
//!
//! - **Spatial**: render at a fraction of output resolution and upscale.
//! - **Temporal**: render every Nth frame and generate the ones in between.
//!
//! Folding them into one ordering is a deliberate simplification. It means the
//! controller only ever makes one decision ("how aggressive?") instead of
//! searching a 2-D space every frame, and it means backing off is always
//! unambiguous: step down. The ordering below puts all the spatial rungs first
//! because spatial reconstruction degrades *gracefully* (a soft frame) while
//! temporal reconstruction degrades *catastrophically* (an invented frame that
//! never existed). Given the choice, spend the last of your budget on pixels,
//! not on frames.

/// How a rung produces frames the renderer never drew.
#[derive(Clone, Copy, PartialEq, Eq, Debug)]
pub enum GenMode {
    /// No frame generation. Every presented frame was rendered.
    None,
    /// Generate between two rendered frames.
    ///
    /// Both endpoints are known, so the result is well-constrained — but the
    /// later endpoint must exist before the middle can be shown, which costs a
    /// full rendered-frame interval of latency. See
    /// [`BoostRung::added_latency_frames`].
    Interpolate,
    /// Generate past the newest rendered frame by extending motion forward.
    ///
    /// Adds no latency, because nothing is held back. Pays for it in accuracy:
    /// there is no second endpoint to constrain the result, so anything that
    /// changes direction — or simply appears — is invented wrong.
    Extrapolate,
}

/// Reconstruction overhead per presented frame, as a fraction of a native frame.
///
/// Upscaling and generation are not free; a rung that halves render cost does
/// not halve frame time. Roughly what an FSR2-class pass costs at 4K on a
/// mid-range part.
pub const RECON_COST: f32 = 0.04;

/// One rung of the boost ladder.
#[derive(Clone, Copy, Debug)]
pub struct BoostRung {
    /// Stable identifier, for logs and config files.
    pub name: &'static str,
    /// Linear render resolution as a fraction of output. `0.5` renders a
    /// quarter of the pixels.
    pub render_scale: f32,
    /// Frames generated per rendered frame. `0` disables frame generation.
    pub generated_per_rendered: u32,
    /// How generated frames are produced.
    pub gen_mode: GenMode,
    /// A priori artifact risk in `0..=1`, before any per-frame evidence.
    ///
    /// This is not used to reject frames — [`crate::signals`] does that from
    /// actual measurements. It weights how long the controller waits before
    /// trying this rung again after it has failed: rungs that fail expensively
    /// should be retried cautiously.
    pub a_priori_risk: f32,
}

impl BoostRung {
    /// Presented frames per rendered frame.
    pub const fn presented_per_rendered(&self) -> u32 {
        1 + self.generated_per_rendered
    }

    /// GPU cost per *presented* frame, relative to rendering natively.
    ///
    /// Render cost scales with pixel count (the square of the linear scale),
    /// amortized over every frame the rung presents, plus the fixed
    /// reconstruction pass.
    pub fn relative_cost(&self) -> f32 {
        let render = self.render_scale * self.render_scale;
        render / self.presented_per_rendered() as f32 + RECON_COST
    }

    /// Extra latency this rung adds, in rendered-frame intervals.
    ///
    /// Interpolation cannot present the middle of an interval until it has both
    /// ends, so the whole presentation timeline slips by one rendered frame.
    /// This is the honest cost of interpolated frame generation and it is why a
    /// doubled frame counter can still feel worse to play.
    pub fn added_latency_frames(&self) -> f32 {
        match self.gen_mode {
            GenMode::None | GenMode::Extrapolate => 0.0,
            GenMode::Interpolate => 1.0,
        }
    }

    /// Whether this rung reprojects across time, and is therefore exposed to
    /// disocclusion, motion-vector error, and scene cuts.
    pub const fn is_temporal(&self) -> bool {
        !matches!(self.gen_mode, GenMode::None)
    }

    /// Render dimensions for a given output size. Always at least 1x1.
    pub fn render_size(&self, out_w: u32, out_h: u32) -> (u32, u32) {
        let w = ((out_w as f32 * self.render_scale).round() as u32).max(1);
        let h = ((out_h as f32 * self.render_scale).round() as u32).max(1);
        (w, h)
    }
}

/// The ladder, ordered from honest to aggressive.
///
/// Scales follow the familiar preset spacing so the rungs mean something to
/// anyone who has shipped an upscaler. The two temporal rungs sit at the top
/// because they are the only ones that can invent a frame outright.
pub const LADDER: &[BoostRung] = &[
    BoostRung {
        name: "native",
        render_scale: 1.0,
        generated_per_rendered: 0,
        gen_mode: GenMode::None,
        a_priori_risk: 0.0,
    },
    BoostRung {
        name: "ultra_quality",
        render_scale: 0.77,
        generated_per_rendered: 0,
        gen_mode: GenMode::None,
        a_priori_risk: 0.10,
    },
    BoostRung {
        name: "quality",
        render_scale: 0.67,
        generated_per_rendered: 0,
        gen_mode: GenMode::None,
        a_priori_risk: 0.18,
    },
    BoostRung {
        name: "balanced",
        render_scale: 0.59,
        generated_per_rendered: 0,
        gen_mode: GenMode::None,
        a_priori_risk: 0.28,
    },
    BoostRung {
        name: "performance",
        render_scale: 0.50,
        generated_per_rendered: 0,
        gen_mode: GenMode::None,
        a_priori_risk: 0.40,
    },
    BoostRung {
        name: "performance_gen",
        render_scale: 0.50,
        generated_per_rendered: 1,
        gen_mode: GenMode::Interpolate,
        a_priori_risk: 0.65,
    },
    BoostRung {
        name: "ultra_performance_gen",
        render_scale: 0.33,
        generated_per_rendered: 1,
        gen_mode: GenMode::Interpolate,
        a_priori_risk: 0.85,
    },
];

/// Number of rungs on the ladder.
pub const LEVELS: usize = LADDER.len();

/// Highest valid rung index.
pub const MAX_LEVEL: u8 = (LADDER.len() - 1) as u8;

/// Lowest rung that reprojects across time.
///
/// The boundary between "degrades softly" and "can invent a frame outright",
/// and therefore the floor the controller retreats to when temporal
/// reconstruction specifically has failed. Dropping all the way to native
/// instead would throw away the spatial boost too, spiking frame time at the
/// exact moment — a cut, a whip pan — when the renderer can least afford it.
pub fn first_temporal_level() -> u8 {
    LADDER
        .iter()
        .position(|r| r.is_temporal())
        .map(|i| i as u8)
        .unwrap_or(MAX_LEVEL)
}

/// Highest rung that does not reproject across time.
pub fn highest_spatial_level() -> u8 {
    first_temporal_level().saturating_sub(1)
}

/// Look up a rung, saturating at the top of the ladder.
pub fn rung(level: u8) -> &'static BoostRung {
    &LADDER[(level as usize).min(LADDER.len() - 1)]
}

/// Find a rung index by name.
pub fn level_by_name(name: &str) -> Option<u8> {
    LADDER.iter().position(|r| r.name == name).map(|i| i as u8)
}

/// What the controller wants the renderer to do this frame.
#[derive(Clone, Copy, Debug)]
pub struct BoostPlan {
    /// Index into [`LADDER`].
    pub level: u8,
    /// The rung itself.
    pub rung: &'static BoostRung,
    /// Width the renderer should draw at.
    pub render_width: u32,
    /// Height the renderer should draw at.
    pub render_height: u32,
    /// Whether a frame should be generated after this rendered frame.
    pub generate: bool,
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn ladder_is_monotonic() {
        // The ladder only works as a ladder if stepping up is unambiguously
        // cheaper and unambiguously riskier. Anything else and "step down to be
        // safe" stops meaning what the controller assumes it means.
        for w in LADDER.windows(2) {
            let (lo, hi) = (&w[0], &w[1]);
            assert!(
                hi.relative_cost() < lo.relative_cost(),
                "{} is not cheaper than {}",
                hi.name,
                lo.name
            );
            assert!(
                hi.a_priori_risk > lo.a_priori_risk,
                "{} is not riskier than {}",
                hi.name,
                lo.name
            );
        }
    }

    #[test]
    fn spatial_rungs_precede_temporal_rungs() {
        let first_temporal = LADDER.iter().position(|r| r.is_temporal()).unwrap();
        assert!(LADDER[..first_temporal].iter().all(|r| !r.is_temporal()));
        assert!(LADDER[first_temporal..].iter().all(|r| r.is_temporal()));
    }

    #[test]
    fn interpolation_costs_a_frame_of_latency() {
        let gen = rung(level_by_name("performance_gen").unwrap());
        assert_eq!(gen.added_latency_frames(), 1.0);
        assert_eq!(rung(0).added_latency_frames(), 0.0);
    }

    #[test]
    fn native_costs_about_one_frame() {
        assert!((rung(0).relative_cost() - (1.0 + RECON_COST)).abs() < 1e-6);
    }

    #[test]
    fn render_size_never_degenerates() {
        let top = rung(MAX_LEVEL);
        assert_eq!(top.render_size(1, 1), (1, 1));
    }
}
