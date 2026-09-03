//! Why the controller did what it did.
//!
//! An adaptive system that cannot explain itself is untunable. When a tester
//! reports "it went blurry in the hangar", the useful answer is not a frame
//! graph — it is "the ceiling dropped to `balanced` at frame 4120, dominant
//! signal `depth_complexity`, and it took 900 frames to climb back".

use std::collections::VecDeque;

use crate::level::{rung, LEVELS};
use crate::signals::SignalKind;

/// What the controller decided about one frame.
#[derive(Clone, Copy, Debug, PartialEq)]
pub enum DecisionKind {
    /// Reconstruction was trusted and shown.
    Presented,
    /// A generated frame was thrown away.
    Discarded,
    /// A real frame was shown, but the rung lost the controller's trust.
    Degraded,
}

/// One entry in the decision log.
#[derive(Clone, Copy, Debug)]
pub struct Decision {
    /// Monotonic frame index.
    pub frame: u64,
    /// Rung in use when the decision was made.
    pub level: u8,
    /// Quality ceiling after the decision.
    pub ceiling: u8,
    /// Confidence score for the frame.
    pub confidence: f32,
    /// What was decided.
    pub kind: DecisionKind,
    /// Signal that dominated the score. Meaningful mostly on rejections.
    pub dominant: SignalKind,
    /// Whether this frame was judged a hard cut.
    pub scene_cut: bool,
}

/// Rolling decision log plus lifetime counters.
#[derive(Clone, Debug)]
pub struct Telemetry {
    history: VecDeque<Decision>,
    capacity: usize,
    /// Frames the controller has resolved.
    pub frames: u64,
    /// Frames whose reconstruction was accepted.
    pub presented: u64,
    /// Generated frames thrown away.
    pub discarded: u64,
    /// Frames that were shown but cost the rung its trust.
    pub degraded: u64,
    /// Hard cuts detected.
    pub scene_cuts: u64,
    /// Rejections attributed to each signal, indexed as [`SignalKind::ALL`].
    pub rejections_by_signal: [u64; 6],
    /// Frames spent on each rung.
    pub frames_at_level: [u64; LEVELS],
    /// Total frames handed to the display, generated ones included.
    pub frames_presented_to_display: u64,
    /// Total frames the renderer actually drew.
    pub frames_rendered: u64,
}

impl Default for Telemetry {
    fn default() -> Self {
        Self::with_capacity(256)
    }
}

impl Telemetry {
    /// A log retaining the last `capacity` decisions.
    pub fn with_capacity(capacity: usize) -> Self {
        Telemetry {
            history: VecDeque::with_capacity(capacity),
            capacity: capacity.max(1),
            frames: 0,
            presented: 0,
            discarded: 0,
            degraded: 0,
            scene_cuts: 0,
            rejections_by_signal: [0; 6],
            frames_at_level: [0; LEVELS],
            frames_presented_to_display: 0,
            frames_rendered: 0,
        }
    }

    pub(crate) fn record(&mut self, d: Decision, presented_frames: u32) {
        self.frames += 1;
        self.frames_rendered += 1;
        self.frames_presented_to_display += presented_frames as u64;
        self.frames_at_level[(d.level as usize).min(LEVELS - 1)] += 1;

        match d.kind {
            DecisionKind::Presented => self.presented += 1,
            DecisionKind::Discarded => self.discarded += 1,
            DecisionKind::Degraded => self.degraded += 1,
        }
        if d.kind != DecisionKind::Presented {
            let idx = SignalKind::ALL
                .iter()
                .position(|k| *k == d.dominant)
                .unwrap_or(0);
            self.rejections_by_signal[idx] += 1;
        }
        if d.scene_cut {
            self.scene_cuts += 1;
        }

        if self.history.len() == self.capacity {
            self.history.pop_front();
        }
        self.history.push_back(d);
    }

    /// The retained decisions, oldest first.
    pub fn history(&self) -> impl Iterator<Item = &Decision> {
        self.history.iter()
    }

    /// The most recent decision, if any.
    pub fn last(&self) -> Option<&Decision> {
        self.history.back()
    }

    /// Presented frames per rendered frame over the run.
    ///
    /// The headline number, and the one to distrust on its own: it counts
    /// frames, not whether they were worth showing. Read it next to
    /// [`Telemetry::discard_rate`].
    pub fn boost_ratio(&self) -> f32 {
        if self.frames_rendered == 0 {
            return 1.0;
        }
        self.frames_presented_to_display as f32 / self.frames_rendered as f32
    }

    /// Fraction of frames whose reconstruction was rejected.
    ///
    /// A healthy run sits low but not at zero. Exactly zero over a long,
    /// varied run usually means the thresholds are too loose to catch anything,
    /// not that reconstruction was flawless.
    pub fn discard_rate(&self) -> f32 {
        if self.frames == 0 {
            return 0.0;
        }
        (self.discarded + self.degraded) as f32 / self.frames as f32
    }

    /// Mean GPU cost per presented frame relative to native rendering.
    pub fn mean_relative_cost(&self) -> f32 {
        if self.frames == 0 {
            return 1.0;
        }
        let total: f32 = self
            .frames_at_level
            .iter()
            .enumerate()
            .map(|(lvl, n)| rung(lvl as u8).relative_cost() * *n as f32)
            .sum();
        total / self.frames as f32
    }

    /// Signal responsible for the most rejections, if there were any.
    pub fn worst_signal(&self) -> Option<SignalKind> {
        let (idx, count) = self
            .rejections_by_signal
            .iter()
            .enumerate()
            .max_by_key(|(_, c)| **c)?;
        (*count > 0).then(|| SignalKind::ALL[idx])
    }

    /// A one-line summary.
    pub fn summary(&self) -> String {
        format!(
            "{} frames, boost {:.2}x, cost {:.2}x native, {:.1}% rejected{}",
            self.frames,
            self.boost_ratio(),
            self.mean_relative_cost(),
            self.discard_rate() * 100.0,
            match self.worst_signal() {
                Some(s) => format!(" (mostly {})", s.name()),
                None => String::new(),
            }
        )
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn decision(frame: u64, level: u8, kind: DecisionKind) -> Decision {
        Decision {
            frame,
            level,
            ceiling: 6,
            confidence: 0.9,
            kind,
            dominant: SignalKind::Disocclusion,
            scene_cut: false,
        }
    }

    #[test]
    fn history_is_bounded() {
        let mut t = Telemetry::with_capacity(4);
        for i in 0..10 {
            t.record(decision(i, 0, DecisionKind::Presented), 1);
        }
        assert_eq!(t.history().count(), 4);
        assert_eq!(t.last().unwrap().frame, 9);
        // Counters must survive eviction from the ring.
        assert_eq!(t.frames, 10);
    }

    #[test]
    fn boost_ratio_counts_generated_frames() {
        let mut t = Telemetry::default();
        for i in 0..10 {
            t.record(decision(i, 5, DecisionKind::Presented), 2);
        }
        assert!((t.boost_ratio() - 2.0).abs() < 1e-6);
    }

    #[test]
    fn discarding_pulls_the_boost_ratio_back_down() {
        let mut t = Telemetry::default();
        for i in 0..5 {
            t.record(decision(i, 5, DecisionKind::Presented), 2);
        }
        for i in 5..10 {
            t.record(decision(i, 5, DecisionKind::Discarded), 1);
        }
        assert!((t.boost_ratio() - 1.5).abs() < 1e-6);
        assert!((t.discard_rate() - 0.5).abs() < 1e-6);
    }

    #[test]
    fn worst_signal_is_none_when_nothing_was_rejected() {
        let mut t = Telemetry::default();
        t.record(decision(0, 0, DecisionKind::Presented), 1);
        assert!(t.worst_signal().is_none());
    }
}
