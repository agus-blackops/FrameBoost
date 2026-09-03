//! Warp the previous frame into the current one, and find out where it breaks.
//!
//! This is the cheapest useful thing you can do with motion vectors, and its
//! by-product — the disocclusion mask — is the single most informative signal
//! the controller gets. Everything temporal is built on top of it.

use crate::frame::{GBuffer, Grid, ImageBuffer};
use crate::recon::{relative_depth_delta, soft_validity};

/// Tuning for [`reproject`].
#[derive(Clone, Copy, Debug)]
pub struct ReprojectConfig {
    /// Relative depth difference tolerated before a sample is rejected.
    ///
    /// Too tight and every surface at a grazing angle disoccludes, because
    /// depth changes fast across such a surface between frames. Too loose and
    /// foreground samples get taken from the background behind them, which is
    /// exactly the smear the whole exercise is meant to avoid. 5% is a
    /// reasonable place to start for a 60 Hz title.
    pub depth_tolerance: f32,
}

impl Default for ReprojectConfig {
    fn default() -> Self {
        ReprojectConfig {
            depth_tolerance: 0.05,
        }
    }
}

/// The result of warping one frame onto another.
#[derive(Clone, Debug)]
pub struct Reprojected {
    /// The previous frame's color, resampled into the current frame's pixels.
    pub color: ImageBuffer,
    /// Per-pixel confidence in `0..=1`.
    pub validity: Grid<f32>,
    /// Fraction of pixels with no usable source, in `0..=1`.
    ///
    /// Feed this to
    /// [`Signals::disocclusion`](crate::signals::Signals::disocclusion).
    pub disocclusion: f32,
}

/// Warp `prev` into the pixel grid of `current` along `current`'s motion
/// vectors.
///
/// A gather, not a scatter: each destination pixel reads one source position,
/// `p + mv(p)`. That is both cheaper and better-behaved than splatting — no
/// atomics, no gaps from pixels nothing happened to land on.
///
/// A sample is rejected when it falls outside the previous frame, or when the
/// depth there disagrees with the depth here by more than the tolerance. The
/// second case is the interesting one: it is a surface that was hidden last
/// frame and is not any more, and there is genuinely no information about what
/// it looked like. Every temporal artifact people complain about lives in those
/// pixels.
///
/// Depth is point-sampled while color is filtered. Filtering depth across a
/// silhouette averages foreground and background into a value that matches
/// neither, which turns the depth test — the one thing standing between you and
/// a smear — into a coin flip exactly at the edges where it matters.
///
/// # Panics
/// If the two frames differ in size.
pub fn reproject(prev: &GBuffer, current: &GBuffer, cfg: ReprojectConfig) -> Reprojected {
    assert_eq!(
        (prev.width(), prev.height()),
        (current.width(), current.height()),
        "reprojection requires matching dimensions"
    );

    let (w, h) = (current.width(), current.height());
    let mut color = ImageBuffer::zeroed(w, h);
    let mut validity = Grid::<f32>::zeroed(w, h);
    let mut invalid = 0u32;

    for y in 0..h {
        for x in 0..w {
            let mv = current.motion.at(x, y);
            let sx = x as f32 + 0.5 + mv[0];
            let sy = y as f32 + 0.5 + mv[1];

            if !prev.color.contains(sx, sy) {
                // Off-screen last frame: it is new, and nothing is known about it.
                color.set(x, y, current.color.at(x, y));
                validity.set(x, y, 0.0);
                invalid += 1;
                continue;
            }

            let src_depth = prev.depth.at_clamped(sx.floor() as i32, sy.floor() as i32);
            let dst_depth = current.depth.at(x, y);
            let v = soft_validity(
                relative_depth_delta(src_depth, dst_depth),
                cfg.depth_tolerance,
            );

            if v <= 0.0 {
                color.set(x, y, current.color.at(x, y));
                validity.set(x, y, 0.0);
                invalid += 1;
            } else {
                color.set(x, y, prev.color.sample_bilinear(sx, sy));
                validity.set(x, y, v);
            }
        }
    }

    Reprojected {
        color,
        validity,
        disocclusion: invalid as f32 / (w * h) as f32,
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::frame::{DepthBuffer, MotionBuffer};

    /// A frame with a vertical white stripe at column `stripe`, at depth 1.0,
    /// on a background at depth 10.0.
    fn striped(w: u32, h: u32, stripe: u32, mv: [f32; 2]) -> GBuffer {
        let mut color = ImageBuffer::new(w, h, [0.0, 0.0, 0.0, 1.0]);
        let mut depth = DepthBuffer::new(w, h, 10.0);
        for y in 0..h {
            color.set(stripe, y, [1.0, 1.0, 1.0, 1.0]);
            depth.set(stripe, y, 1.0);
        }
        GBuffer::new(color, depth, MotionBuffer::new(w, h, mv))
    }

    #[test]
    fn exact_motion_vectors_reproduce_the_frame() {
        // Content moved 4 px right, so every pixel's previous position is 4 px
        // to its left: mv = -4.
        let prev = striped(16, 4, 5, [0.0, 0.0]);
        let current = striped(16, 4, 9, [-4.0, 0.0]);
        let r = reproject(&prev, &current, ReprojectConfig::default());

        // Away from the newly exposed edge, the warp should land on the stripe.
        for y in 0..4 {
            let c = r.color.at(9, y);
            assert!(c[0] > 0.99, "stripe not reprojected: {c:?}");
            assert!(r.validity.at(9, y) > 0.99);
        }
    }

    #[test]
    fn the_leading_edge_is_disoccluded() {
        let prev = striped(16, 4, 5, [0.0, 0.0]);
        let current = striped(16, 4, 9, [-4.0, 0.0]);
        let r = reproject(&prev, &current, ReprojectConfig::default());

        // Columns 0..4 sourced from off-screen: nothing is known about them.
        for x in 0..4 {
            assert_eq!(r.validity.at(x, 0), 0.0, "column {x} should be disoccluded");
        }
        // 4 of 16 columns.
        assert!((r.disocclusion - 0.25).abs() < 1e-5, "{}", r.disocclusion);
    }

    #[test]
    fn disoccluded_pixels_fall_back_to_the_current_frame() {
        // Never leave a hole. A wrong-but-plausible pixel beats a black one.
        let prev = striped(16, 4, 5, [0.0, 0.0]);
        let mut current = striped(16, 4, 9, [-4.0, 0.0]);
        for y in 0..4 {
            current.color.set(1, y, [0.25, 0.5, 0.75, 1.0]);
        }
        let r = reproject(&prev, &current, ReprojectConfig::default());
        assert_eq!(r.color.at(1, 0), [0.25, 0.5, 0.75, 1.0]);
    }

    #[test]
    fn depth_mismatch_rejects_the_sample() {
        // Motion vectors say "sample here", depth says "that is a different
        // surface". Depth wins; this is the test that prevents background
        // bleeding over foreground.
        let w = 8;
        let prev = GBuffer::new(
            ImageBuffer::new(w, 1, [1.0, 0.0, 0.0, 1.0]),
            DepthBuffer::new(w, 1, 100.0),
            MotionBuffer::zeroed(w, 1),
        );
        let current = GBuffer::new(
            ImageBuffer::new(w, 1, [0.0, 1.0, 0.0, 1.0]),
            DepthBuffer::new(w, 1, 1.0),
            MotionBuffer::zeroed(w, 1),
        );
        let r = reproject(&prev, &current, ReprojectConfig::default());
        assert_eq!(r.disocclusion, 1.0);
        assert_eq!(r.color.at(3, 0), [0.0, 1.0, 0.0, 1.0]);
    }

    #[test]
    fn a_static_scene_reprojects_perfectly() {
        let prev = striped(16, 4, 5, [0.0, 0.0]);
        let current = striped(16, 4, 5, [0.0, 0.0]);
        let r = reproject(&prev, &current, ReprojectConfig::default());
        assert_eq!(r.disocclusion, 0.0);
        assert_eq!(r.color, prev.color);
    }
}
