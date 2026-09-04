//! Build a frame that was never rendered.
//!
//! # The geometry
//!
//! A surface's screen position is taken to be linear in time across one
//! rendered interval. With `mv` the motion vector convention of this crate —
//! the offset from a pixel in the *newer* frame back to the same surface in the
//! *older* one — a surface at `x1` in the newer frame traces:
//!
//! ```text
//!   x(s) = x1 + (1 - s) * mv        s = 0 at prev, s = 1 at next
//! ```
//!
//! Check the ends: `x(1) = x1`, and `x(0) = x1 + mv`, the previous position.
//! Inverting for the output pixel `q` we want to fill at time `t`, so that
//! `x(t) = q`:
//!
//! ```text
//!   sample next at  q - (1 - t) * mv
//!   sample prev at  q + t * mv
//! ```
//!
//! At `t = 0.5` those are `q + mv/2` and `q - mv/2`: symmetric, half the
//! displacement in either direction. Good sanity check when porting this.
//!
//! # The approximation
//!
//! Strictly, `mv` should be the motion of the surface that lands on `q` at time
//! `t`, and finding it means scattering vectors forward — expensive, and it
//! leaves gaps. This gathers instead, using `mv(q)` from the newer frame and
//! assuming the field is locally smooth. That assumption is fine across
//! surfaces and wrong across silhouettes, which is precisely where the
//! validity checks below have to earn their keep.
//!
//! # Failing safely
//!
//! Two checks decide whether a correspondence is believable: the two samples
//! must agree in depth, and they must agree in luminance. Depth catches
//! correspondences that cross a silhouette; photo-consistency catches
//! everything the motion vectors never described in the first place — particles,
//! alpha, shader animation, a light turning on. That second class is the one
//! that makes frame generation look broken, and G-buffer geometry alone will
//! never warn you about it.
//!
//! Where a correspondence is rejected, the pixel is *not* filled with a warped
//! sample. Warping a bad correspondence is how you get smear. It is filled from
//! believable neighbours if there are any, and otherwise held from the nearest
//! rendered frame in time — locally a duplicated frame, which is a far cheaper
//! error than an invented one.

use crate::frame::{GBuffer, Grid, ImageBuffer};
use crate::recon::{relative_depth_delta, soft_validity};
use crate::signals::luma;

/// Tuning for [`interpolate_midpoint`].
#[derive(Clone, Copy, Debug)]
pub struct InterpolateConfig {
    /// Relative depth disagreement tolerated between the two samples.
    pub depth_tolerance: f32,
    /// Luminance disagreement tolerated between the two samples, absolute.
    ///
    /// Generous on purpose. Two views of the same surface a frame apart really
    /// do differ — specular highlights slide, shadows crawl — and rejecting
    /// every one of those would reject most of the screen.
    pub photo_tolerance: f32,
    /// Dilation passes used to fill rejected pixels from believable neighbours.
    ///
    /// Each pass grows the valid region by one pixel, so this is the widest
    /// hole that gets filled rather than held. Small holes are worth filling;
    /// wide ones are a disocclusion the controller should be reacting to
    /// instead.
    pub hole_fill_passes: u32,
}

impl Default for InterpolateConfig {
    fn default() -> Self {
        InterpolateConfig {
            depth_tolerance: 0.05,
            photo_tolerance: 0.20,
            hole_fill_passes: 3,
        }
    }
}

/// A generated frame and how much of it was invented.
#[derive(Clone, Debug)]
pub struct Interpolated {
    /// The generated frame.
    pub color: ImageBuffer,
    /// Fraction of pixels whose correspondence was rejected, in `0..=1`.
    ///
    /// Counted *before* hole filling, deliberately. A filled hole still shows
    /// content no rendered frame contained; it looks better than a black gap
    /// but it is not evidence that reconstruction worked. Reporting the
    /// post-fill number would tell the controller everything is fine right up
    /// until a tester says otherwise.
    ///
    /// Feed this to
    /// [`Signals::disocclusion`](crate::signals::Signals::disocclusion).
    pub disocclusion: f32,
}

/// Generate the frame at time `t` between `prev` and `next`.
///
/// `t` is clamped to `0..=1`; `0.5` is the midpoint. Both frames must be the
/// same size, and `next.motion` must follow this crate's convention (see
/// [`crate::frame`]).
///
/// # Panics
/// If the two frames differ in size.
pub fn interpolate_midpoint(
    prev: &GBuffer,
    next: &GBuffer,
    t: f32,
    cfg: InterpolateConfig,
) -> Interpolated {
    assert_eq!(
        (prev.width(), prev.height()),
        (next.width(), next.height()),
        "interpolation requires matching dimensions"
    );

    let t = t.clamp(0.0, 1.0);
    let (w, h) = (next.width(), next.height());
    let mut color = ImageBuffer::zeroed(w, h);
    let mut valid = Grid::<f32>::zeroed(w, h);
    let mut rejected = 0u32;

    for y in 0..h {
        for x in 0..w {
            let mv = next.motion.at(x, y);
            let qx = x as f32 + 0.5;
            let qy = y as f32 + 0.5;

            let nx = qx - (1.0 - t) * mv[0];
            let ny = qy - (1.0 - t) * mv[1];
            let px = qx + t * mv[0];
            let py = qy + t * mv[1];

            let in_next = next.color.contains(nx, ny);
            let in_prev = prev.color.contains(px, py);

            if in_next && in_prev {
                let cn = next.color.sample_bilinear(nx, ny);
                let cp = prev.color.sample_bilinear(px, py);

                // Point-sampled: filtering depth across a silhouette produces a
                // value that matches neither side and defeats the test.
                let dn = next.depth.at_clamped(nx.floor() as i32, ny.floor() as i32);
                let dp = prev.depth.at_clamped(px.floor() as i32, py.floor() as i32);

                let depth_ok = soft_validity(relative_depth_delta(dn, dp), cfg.depth_tolerance);
                let photo_ok =
                    soft_validity((luma(cn) - luma(cp)).abs(), cfg.photo_tolerance.max(1e-4));
                let confidence = depth_ok * photo_ok;

                if confidence > 0.0 {
                    color.set(x, y, lerp(cp, cn, t));
                    valid.set(x, y, confidence);
                    continue;
                }
            }

            // No believable correspondence. Hold the nearest frame in time
            // rather than warping something we do not trust.
            color.set(x, y, hold(prev, next, x, y, t));
            valid.set(x, y, 0.0);
            rejected += 1;
        }
    }

    let disocclusion = rejected as f32 / (w * h) as f32;
    fill_holes(&mut color, &mut valid, cfg.hole_fill_passes);

    Interpolated {
        color,
        disocclusion,
    }
}

#[inline]
fn lerp(a: [f32; 4], b: [f32; 4], t: f32) -> [f32; 4] {
    let mut out = [0.0; 4];
    for i in 0..4 {
        out[i] = a[i] + (b[i] - a[i]) * t;
    }
    out
}

#[inline]
fn hold(prev: &GBuffer, next: &GBuffer, x: u32, y: u32, t: f32) -> [f32; 4] {
    if t < 0.5 {
        prev.color.at(x, y)
    } else {
        next.color.at(x, y)
    }
}

/// Grow the believable region outward, one pixel per pass.
///
/// Each pass replaces rejected pixels that border believable ones with a
/// confidence-weighted average of those neighbours, then promotes them so the
/// next pass can build on them. Rejected pixels with no believable neighbour at
/// all keep the held color they already have.
fn fill_holes(color: &mut ImageBuffer, valid: &mut Grid<f32>, passes: u32) {
    let (w, h) = (color.width(), color.height());
    for _ in 0..passes {
        let mut filled_any = false;
        let snapshot_color = color.clone();
        let snapshot_valid = valid.clone();

        for y in 0..h {
            for x in 0..w {
                if snapshot_valid.at(x, y) > 0.0 {
                    continue;
                }
                let mut acc = [0.0f32; 4];
                let mut weight = 0.0f32;
                for dy in -1i32..=1 {
                    for dx in -1i32..=1 {
                        if dx == 0 && dy == 0 {
                            continue;
                        }
                        let (nx, ny) = (x as i32 + dx, y as i32 + dy);
                        if nx < 0 || ny < 0 || nx >= w as i32 || ny >= h as i32 {
                            continue;
                        }
                        let v = snapshot_valid.at(nx as u32, ny as u32);
                        if v <= 0.0 {
                            continue;
                        }
                        let c = snapshot_color.at(nx as u32, ny as u32);
                        for i in 0..4 {
                            acc[i] += c[i] * v;
                        }
                        weight += v;
                    }
                }
                if weight > 0.0 {
                    for v in acc.iter_mut() {
                        *v /= weight;
                    }
                    color.set(x, y, acc);
                    // Slightly below 1: filled pixels are usable sources for the
                    // next pass but should not outweigh genuine samples.
                    valid.set(x, y, 0.5);
                    filled_any = true;
                }
            }
        }

        if !filled_any {
            break;
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::frame::{DepthBuffer, MotionBuffer};

    /// A frame with a white stripe at column `stripe`, foreground depth on it.
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
    fn the_feature_lands_at_the_midpoint() {
        // Stripe centers: 5.5 in prev, 9.5 in next. Midpoint 7.5, the exact
        // center of pixel 7. Motion vectors point backwards by 4.
        let prev = striped(24, 4, 5, [0.0, 0.0]);
        let next = striped(24, 4, 9, [-4.0, 0.0]);
        let out = interpolate_midpoint(&prev, &next, 0.5, InterpolateConfig::default());

        let here = luma(out.color.at(7, 2));
        for x in [5u32, 6, 8, 9] {
            let there = luma(out.color.at(x, 2));
            assert!(
                here > there + 0.3,
                "stripe should be at column 7, not {x}: {here} vs {there}"
            );
        }
    }

    #[test]
    fn endpoints_are_reproduced() {
        // t=0 and t=1 must return the frames themselves. If they do not, the
        // sampling geometry is wrong and every t in between is wrong too.
        let prev = striped(24, 4, 5, [0.0, 0.0]);
        let next = striped(24, 4, 9, [-4.0, 0.0]);

        let at_zero = interpolate_midpoint(&prev, &next, 0.0, InterpolateConfig::default());
        assert!(luma(at_zero.color.at(5, 2)) > 0.9, "t=0 should equal prev");

        let at_one = interpolate_midpoint(&prev, &next, 1.0, InterpolateConfig::default());
        assert!(luma(at_one.color.at(9, 2)) > 0.9, "t=1 should equal next");
    }

    #[test]
    fn a_static_scene_generates_nothing_new() {
        let prev = striped(16, 4, 5, [0.0, 0.0]);
        let next = striped(16, 4, 5, [0.0, 0.0]);
        let out = interpolate_midpoint(&prev, &next, 0.5, InterpolateConfig::default());
        assert_eq!(out.disocclusion, 0.0);
        for y in 0..4 {
            for x in 0..16 {
                let d = luma(out.color.at(x, y)) - luma(next.color.at(x, y));
                assert!(d.abs() < 1e-4, "({x},{y}) drifted by {d}");
            }
        }
    }

    #[test]
    fn edges_that_leave_the_frame_are_reported() {
        let prev = striped(16, 4, 5, [0.0, 0.0]);
        let next = striped(16, 4, 9, [-4.0, 0.0]);
        let out = interpolate_midpoint(&prev, &next, 0.5, InterpolateConfig::default());
        // Both endpoints are sampled 2 px either side, so 2 columns at each
        // edge have no correspondence: 4 of 16.
        assert!(out.disocclusion > 0.2, "{}", out.disocclusion);
    }

    #[test]
    fn content_the_motion_vectors_do_not_explain_is_caught() {
        // A muzzle flash: the geometry says nothing moved, and the image
        // changed completely. This is the class of failure that makes frame
        // generation look broken, and depth alone would sail straight past it.
        let w = 16;
        let prev = GBuffer::new(
            ImageBuffer::new(w, 4, [0.0, 0.0, 0.0, 1.0]),
            DepthBuffer::new(w, 4, 5.0),
            MotionBuffer::zeroed(w, 4),
        );
        let next = GBuffer::new(
            ImageBuffer::new(w, 4, [1.0, 1.0, 1.0, 1.0]),
            DepthBuffer::new(w, 4, 5.0),
            MotionBuffer::zeroed(w, 4),
        );
        let out = interpolate_midpoint(&prev, &next, 0.5, InterpolateConfig::default());
        assert!(
            out.disocclusion > 0.9,
            "photo-consistency missed it: {}",
            out.disocclusion
        );
    }

    #[test]
    fn rejected_pixels_are_never_left_black() {
        let prev = striped(16, 4, 5, [0.0, 0.0]);
        let next = striped(16, 4, 9, [-9.0, 0.0]);
        let out = interpolate_midpoint(&prev, &next, 0.5, InterpolateConfig::default());
        for y in 0..4 {
            for x in 0..16 {
                assert_eq!(out.color.at(x, y)[3], 1.0, "alpha lost at ({x},{y})");
            }
        }
    }

    #[test]
    fn hole_filling_does_not_change_the_reported_disocclusion() {
        // The metric must describe what was invented, not what was papered over.
        let prev = striped(24, 4, 5, [0.0, 0.0]);
        let next = striped(24, 4, 9, [-4.0, 0.0]);
        let unfilled = interpolate_midpoint(
            &prev,
            &next,
            0.5,
            InterpolateConfig {
                hole_fill_passes: 0,
                ..Default::default()
            },
        );
        let filled = interpolate_midpoint(&prev, &next, 0.5, InterpolateConfig::default());
        assert_eq!(unfilled.disocclusion, filled.disocclusion);
    }
}
