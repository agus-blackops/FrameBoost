//! Present-time scheduling for generated frames.
//!
//! Frame generation that ignores pacing makes things worse while the counter
//! says they got better. Render at 60 Hz, generate one frame per rendered
//! frame, and hand both to the swapchain as soon as they exist: the pair
//! arrives back-to-back and then nothing arrives for 16 ms. The display shows
//! 120 distinct images per second, spaced 0 ms and 16 ms apart. That reads as
//! *judder*, and it is worse than the 60 Hz it replaced.
//!
//! So a generated frame has a deadline, and the pacer's job is to compute it
//! and to notice when it cannot be met.
//!
//! # The latency that interpolation actually costs
//!
//! To place an image between A and B you must already have B. So A cannot be
//! shown when it is ready — it has to be held until B exists, or there is no
//! slot left to put the middle in. The entire presentation timeline slips by
//! one rendered-frame interval:
//!
//! ```text
//!   rendered:   A-------B-------C-------
//!   presented:  --------A---M---B---M---
//!                       ^ one full interval late
//! ```
//!
//! That delay is not an implementation shortcoming to optimize away; it is what
//! interpolation is. It is why [`GenMode::Extrapolate`] exists as an option
//! despite being less accurate, and why doubling a frame counter can make a
//! game feel worse to play. [`BoostRung::added_latency_frames`] carries the
//! same fact for the controller.
//!
//! # Feeding instability back into the loop
//!
//! [`FramePacer::instability`] measures how irregular the rendered intervals
//! have been, and it is meant to be handed to
//! [`Signals::pacing_instability`](crate::signals::Signals::pacing_instability).
//! When render times are erratic the pacer's estimate of the next interval is
//! wrong, generated frames land at the wrong moments, and the right response is
//! to stop generating them — a decision the controller can only make if the
//! pacer tells it.

use crate::level::{BoostRung, GenMode};

/// Which image a slot presents.
#[derive(Clone, Copy, Debug, PartialEq)]
pub enum SlotKind {
    /// A frame the renderer actually drew.
    ///
    /// Under [`GenMode::Interpolate`] this is the *older* endpoint of the
    /// interval, released only now that the newer one exists.
    Rendered,
    /// A frame that was never rendered.
    Generated {
        /// Zero-based index among the generated frames of this interval.
        index: u32,
        /// Position between the endpoints. `0.5` is the midpoint of an
        /// interpolated pair; values above `1.0` are extrapolated past the
        /// newest rendered frame.
        t: f32,
    },
}

/// One scheduled presentation.
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct PresentSlot {
    /// Deadline, in the same clock the caller feeds [`FramePacer::on_rendered`].
    pub at_ns: u64,
    /// What to show.
    pub kind: SlotKind,
}

/// How many recent intervals feed the instability estimate.
const WINDOW: usize = 16;

/// Coefficient of variation at which [`FramePacer::instability`] saturates.
///
/// A 25% spread in render intervals means the next-interval estimate is
/// routinely off by a quarter of a frame, which is more than enough to put a
/// generated frame visibly in the wrong place.
const CV_SATURATION: f32 = 0.25;

/// Estimates the rendered-frame interval and places generated frames in it.
#[derive(Clone, Debug)]
pub struct FramePacer {
    interval_ema_ns: f64,
    alpha: f64,
    last_render_ns: Option<u64>,
    window: [f64; WINDOW],
    filled: usize,
    cursor: usize,
}

impl FramePacer {
    /// A pacer seeded with an expected interval.
    pub fn new(expected_interval_ns: u64) -> Self {
        FramePacer {
            interval_ema_ns: expected_interval_ns.max(1) as f64,
            alpha: 0.15,
            last_render_ns: None,
            window: [expected_interval_ns.max(1) as f64; WINDOW],
            filled: 0,
            cursor: 0,
        }
    }

    /// A pacer for a target refresh rate.
    pub fn for_target_fps(fps: f32) -> Self {
        Self::new((1_000_000_000.0 / fps.max(1.0)) as u64)
    }

    /// Discard the interval history.
    ///
    /// Call this whenever something legitimately changes the rendered-frame
    /// interval — a rung change above all, but equally a resolution change, a
    /// v-sync toggle, or coming back from a loading screen.
    ///
    /// Without it the pacer feeds a false alarm straight back into the
    /// controller. Changing rung changes frame cost by design, so the interval
    /// window ends up holding a mix of the old rung's intervals and the new
    /// one's; the dispersion between them reads as instability, instability
    /// rejects the frame, the rejection drops the rung, and the drop is another
    /// interval change. The loop sustains itself indefinitely on nothing but
    /// its own actions, and every scenario ends up looking the same because the
    /// controller never stays anywhere long enough for the content to matter.
    ///
    /// [`FramePacer::instability`] is meant to report *unexplained* variation
    /// in render times. Variation the controller itself caused is explained,
    /// and must not come back to it as evidence.
    pub fn reset(&mut self) {
        self.last_render_ns = None;
        self.filled = 0;
        self.cursor = 0;
        // The EMA is kept: it is the best estimate available for the next
        // interval, and it re-converges. Only the variance window, which is
        // what would carry the stale intervals into the instability figure, is
        // thrown away.
    }

    /// Record that a rendered frame completed.
    pub fn on_rendered(&mut self, now_ns: u64) {
        if let Some(prev) = self.last_render_ns {
            // saturating_sub, because a caller feeding a wall clock can hand us
            // a timestamp that went backwards. A negative interval would poison
            // the EMA permanently; a zero one is merely ignored below.
            let dt = now_ns.saturating_sub(prev) as f64;
            if dt > 0.0 {
                self.interval_ema_ns += self.alpha * (dt - self.interval_ema_ns);
                self.window[self.cursor] = dt;
                self.cursor = (self.cursor + 1) % WINDOW;
                self.filled = (self.filled + 1).min(WINDOW);
            }
        }
        self.last_render_ns = Some(now_ns);
    }

    /// Smoothed rendered-frame interval, in nanoseconds. Never zero.
    pub fn interval_ns(&self) -> u64 {
        (self.interval_ema_ns.max(1.0)) as u64
    }

    /// How irregular recent intervals have been, in `0..=1`.
    ///
    /// Feed this to [`Signals::pacing_instability`](crate::signals::Signals::pacing_instability).
    /// Returns 0 until enough intervals have been seen to say anything: a pacer
    /// that reports instability before it has evidence would suppress frame
    /// generation for the first second of every session.
    pub fn instability(&self) -> f32 {
        if self.filled < 4 {
            return 0.0;
        }
        let n = self.filled as f64;
        let samples = &self.window[..self.filled];
        let mean = samples.iter().sum::<f64>() / n;
        if mean <= 0.0 {
            return 0.0;
        }
        let var = samples.iter().map(|s| (s - mean) * (s - mean)).sum::<f64>() / n;
        let cv = (var.sqrt() / mean) as f32;
        (cv / CV_SATURATION).clamp(0.0, 1.0)
    }

    /// Extra latency the rung adds at the current interval, in nanoseconds.
    pub fn added_latency_ns(&self, rung: &BoostRung) -> u64 {
        (self.interval_ns() as f64 * rung.added_latency_frames() as f64) as u64
    }

    /// The presentation slots unblocked by the frame that finished at
    /// `rendered_at_ns`.
    ///
    /// - [`GenMode::None`]: one slot, immediately.
    /// - [`GenMode::Interpolate`]: the *previous* rendered frame, released now,
    ///   followed by the generated frames spread evenly across the interval
    ///   that just closed.
    /// - [`GenMode::Extrapolate`]: the frame just rendered, immediately,
    ///   followed by generated frames projected forward into the interval that
    ///   has not happened yet.
    ///
    /// Slots come back in presentation order and are always strictly
    /// increasing in time.
    pub fn slots_for(&self, rung: &BoostRung, rendered_at_ns: u64) -> Vec<PresentSlot> {
        let count = rung.generated_per_rendered;
        if count == 0 || matches!(rung.gen_mode, GenMode::None) {
            return vec![PresentSlot {
                at_ns: rendered_at_ns,
                kind: SlotKind::Rendered,
            }];
        }

        let interval = self.interval_ns() as f64;
        let step = interval / (count + 1) as f64;
        let mut slots = Vec::with_capacity(count as usize + 1);

        slots.push(PresentSlot {
            at_ns: rendered_at_ns,
            kind: SlotKind::Rendered,
        });

        for i in 0..count {
            let offset = step * (i + 1) as f64;
            let frac = offset / interval;
            let t = match rung.gen_mode {
                // Between the two endpoints we hold.
                GenMode::Interpolate => frac as f32,
                // Past the newest endpoint, into time that has not happened.
                GenMode::Extrapolate => 1.0 + frac as f32,
                GenMode::None => unreachable!("handled above"),
            };
            slots.push(PresentSlot {
                at_ns: rendered_at_ns + offset as u64,
                kind: SlotKind::Generated { index: i, t },
            });
        }

        slots
    }

    /// How far a presentation missed its slot, in nanoseconds.
    ///
    /// Signed: negative is early, positive is late. Both are visible; early is
    /// generally worse, because a frame shown before its moment shortens the
    /// interval on both sides of it.
    pub fn pacing_error_ns(slot: &PresentSlot, actual_ns: u64) -> i64 {
        actual_ns as i64 - slot.at_ns as i64
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::level::{level_by_name, rung, BoostRung};

    const HZ60: u64 = 16_666_667;

    fn gen_rung() -> &'static BoostRung {
        rung(level_by_name("performance_gen").unwrap())
    }

    fn extrapolating() -> BoostRung {
        BoostRung {
            gen_mode: GenMode::Extrapolate,
            ..*gen_rung()
        }
    }

    #[test]
    fn learns_the_interval() {
        let mut p = FramePacer::for_target_fps(30.0);
        let mut t = 0u64;
        for _ in 0..200 {
            p.on_rendered(t);
            t += HZ60;
        }
        let learned = p.interval_ns() as f64;
        assert!(
            (learned - HZ60 as f64).abs() / (HZ60 as f64) < 0.02,
            "learned {learned}"
        );
    }

    #[test]
    fn steady_rendering_is_stable() {
        let mut p = FramePacer::for_target_fps(60.0);
        let mut t = 0u64;
        for _ in 0..64 {
            p.on_rendered(t);
            t += HZ60;
        }
        assert!(p.instability() < 0.01);
    }

    #[test]
    fn erratic_rendering_reports_instability() {
        let mut p = FramePacer::for_target_fps(60.0);
        let mut t = 0u64;
        for i in 0..64 {
            p.on_rendered(t);
            // Alternate 8 ms and 25 ms: a mean near 60 Hz built out of
            // intervals that are nowhere near it.
            t += if i % 2 == 0 { 8_000_000 } else { 25_000_000 };
        }
        assert!(p.instability() > 0.5, "instability {}", p.instability());
    }

    #[test]
    fn a_reset_clears_the_instability_history() {
        // A rung change is not display jitter, and must not be reported as any.
        let mut p = FramePacer::for_target_fps(60.0);
        let mut t = 0u64;
        for _ in 0..32 {
            p.on_rendered(t);
            t += 25_000_000;
        }
        // The rung changes: frames get much cheaper.
        p.reset();
        for _ in 0..32 {
            p.on_rendered(t);
            t += 11_000_000;
        }
        assert!(
            p.instability() < 0.05,
            "a clean run after a reset still looks unstable: {}",
            p.instability()
        );
    }

    #[test]
    fn without_a_reset_a_step_change_does_look_unstable() {
        // The behaviour reset() exists to suppress. Worth pinning down: if this
        // ever stops being true, reset() has become dead code.
        let mut p = FramePacer::for_target_fps(60.0);
        let mut t = 0u64;
        for _ in 0..12 {
            p.on_rendered(t);
            t += 25_000_000;
        }
        for _ in 0..6 {
            p.on_rendered(t);
            t += 11_000_000;
        }
        assert!(p.instability() > 0.3, "{}", p.instability());
    }

    #[test]
    fn says_nothing_before_it_has_evidence() {
        let mut p = FramePacer::for_target_fps(60.0);
        p.on_rendered(0);
        p.on_rendered(HZ60);
        assert_eq!(p.instability(), 0.0);
    }

    #[test]
    fn a_backwards_clock_does_not_poison_the_estimate() {
        let mut p = FramePacer::for_target_fps(60.0);
        p.on_rendered(1_000_000_000);
        p.on_rendered(0); // clock jumped back
        p.on_rendered(HZ60);
        assert!(p.interval_ns() > 0);
        assert!((p.interval_ns() as f64) < 1e11);
    }

    #[test]
    fn generated_frames_are_evenly_spaced() {
        let mut p = FramePacer::for_target_fps(60.0);
        let mut t = 0u64;
        for _ in 0..32 {
            p.on_rendered(t);
            t += HZ60;
        }
        let slots = p.slots_for(gen_rung(), t);
        assert_eq!(slots.len(), 2);
        assert_eq!(slots[0].kind, SlotKind::Rendered);
        // The midpoint sits at half an interval, not immediately after.
        let gap = slots[1].at_ns - slots[0].at_ns;
        assert!(
            (gap as i64 - (HZ60 / 2) as i64).abs() < 200_000,
            "gap {gap}"
        );
        match slots[1].kind {
            SlotKind::Generated { index, t } => {
                assert_eq!(index, 0);
                assert!((t - 0.5).abs() < 1e-4);
            }
            other => panic!("{other:?}"),
        }
    }

    #[test]
    fn slots_are_strictly_increasing() {
        let mut p = FramePacer::for_target_fps(60.0);
        p.on_rendered(0);
        p.on_rendered(HZ60);
        for r in [gen_rung(), &extrapolating()] {
            let slots = p.slots_for(r, 5 * HZ60);
            for w in slots.windows(2) {
                assert!(w[1].at_ns > w[0].at_ns, "{slots:?}");
            }
        }
    }

    #[test]
    fn extrapolation_projects_past_the_newest_frame() {
        let p = FramePacer::for_target_fps(60.0);
        let slots = p.slots_for(&extrapolating(), 0);
        match slots[1].kind {
            SlotKind::Generated { t, .. } => assert!(t > 1.0, "t {t}"),
            other => panic!("{other:?}"),
        }
    }

    #[test]
    fn only_interpolation_costs_latency() {
        let mut p = FramePacer::for_target_fps(60.0);
        p.on_rendered(0);
        p.on_rendered(HZ60);
        assert!(p.added_latency_ns(gen_rung()) > HZ60 / 2);
        assert_eq!(p.added_latency_ns(&extrapolating()), 0);
        assert_eq!(p.added_latency_ns(rung(0)), 0);
    }

    #[test]
    fn no_generation_means_present_immediately() {
        let p = FramePacer::for_target_fps(60.0);
        let slots = p.slots_for(rung(0), 12_345);
        assert_eq!(
            slots,
            vec![PresentSlot {
                at_ns: 12_345,
                kind: SlotKind::Rendered
            }]
        );
    }

    #[test]
    fn pacing_error_is_signed() {
        let slot = PresentSlot {
            at_ns: 1_000,
            kind: SlotKind::Rendered,
        };
        assert_eq!(FramePacer::pacing_error_ns(&slot, 1_200), 200);
        assert_eq!(FramePacer::pacing_error_ns(&slot, 800), -200);
    }
}
