//! Robust contrast-adaptive sharpening.
//!
//! Upsampling costs acutance no matter how good the kernel is, so the pass
//! after it puts some back. The hard part is not sharpening — it is sharpening
//! without producing the halo that gives cheap sharpening away.
//!
//! A conventional unsharp mask subtracts a blur and clips whatever leaves the
//! representable range. Along a high-contrast edge that clipping *is* the halo:
//! the bright side saturates into a white rim, the dark side crushes to black.
//!
//! RCAS's idea is to not need the clip. For each pixel it computes, from the
//! local extremes, the largest sharpening lobe that will not push any channel
//! past `0` or `1`, and uses that. On flat regions the limit is generous and
//! the pass sharpens freely; against a near-white edge the limit approaches
//! zero and the pass backs off on its own. The strength is decided per pixel by
//! the image, not by a global slider someone has to tune per scene.
//!
//! Two details worth keeping when porting:
//!
//! - The lobe is computed per channel, and the **weakest** of the three is
//!   applied to all of them. Sharpening channels by different amounts moves
//!   them apart, which is visible as color fringing on exactly the edges this
//!   pass is aimed at.
//! - The `1 − max` term means the math assumes values in `0..=1`. On an HDR
//!   buffer, tonemap first — or the limit is computed against a ceiling that is
//!   not where clipping actually happens, and the pass will happily ring.

use crate::frame::ImageBuffer;

/// Most negative lobe permitted, from the FSR formulation.
///
/// The kernel is `(lobe·(b+d+f+h) + e) / (4·lobe + 1)`, whose denominator
/// reaches zero at `lobe = -0.25`. This bound keeps a margin from that
/// singularity; nearer to it the gain runs away and single pixels detonate.
const LOBE_LIMIT: f32 = -0.1875;

/// Sharpen in place-equivalent fashion, returning a new buffer.
///
/// `sharpness` scales the computed lobe, `0` (off) to `1` (as much as the local
/// contrast allows). Alpha is passed through: sharpening coverage produces
/// edge artifacts in whatever composites the result.
pub fn rcas(img: &ImageBuffer, sharpness: f32) -> ImageBuffer {
    let sharpness = sharpness.clamp(0.0, 1.0);
    let (w, h) = (img.width(), img.height());
    let mut out = ImageBuffer::zeroed(w, h);

    if sharpness <= 0.0 {
        return img.clone();
    }

    for y in 0..h {
        for x in 0..w {
            let (xi, yi) = (x as i32, y as i32);
            //     b
            //   d e f
            //     h
            let e = img.at(x, y);
            let b = img.at_clamped(xi, yi - 1);
            let d = img.at_clamped(xi - 1, yi);
            let f = img.at_clamped(xi + 1, yi);
            let hh = img.at_clamped(xi, yi + 1);

            // Weakest lobe across the three color channels.
            let mut lobe = 0.0f32;
            let mut first = true;
            for c in 0..3 {
                let ring_min = b[c].min(d[c]).min(f[c]).min(hh[c]);
                let ring_max = b[c].max(d[c]).max(f[c]).max(hh[c]);

                // Headroom before the dark side crushes, and before the bright
                // side blows out.
                let hit_min = ring_min / (4.0 * ring_max).max(1e-6);
                let hit_max = (1.0 - ring_max) / (4.0 * ring_min - 4.0).min(-1e-6);
                let ch_lobe = (-hit_min).max(hit_max);

                lobe = if first { ch_lobe } else { lobe.max(ch_lobe) };
                first = false;
            }

            let lobe = (lobe.clamp(LOBE_LIMIT, 0.0)) * sharpness;
            let denom = 4.0 * lobe + 1.0;

            let mut color = e;
            if denom.abs() > 1e-4 {
                for c in 0..3 {
                    let sharpened = (lobe * (b[c] + d[c] + f[c] + hh[c]) + e[c]) / denom;
                    color[c] = if sharpened.is_finite() { sharpened } else { e[c] };
                }
            }
            out.set(x, y, color);
        }
    }

    out
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::signals::luma;

    fn constant(w: u32, h: u32, v: f32) -> ImageBuffer {
        ImageBuffer::new(w, h, [v, v, v, 1.0])
    }

    /// A step edge softened by one 1-2-1 pass — a transition with real
    /// curvature at its shoulders, which is what sharpening acts on.
    fn soft_edge(w: u32, h: u32) -> ImageBuffer {
        let mut hard = ImageBuffer::new(w, h, [0.15, 0.15, 0.15, 1.0]);
        for y in 0..h {
            for x in w / 2..w {
                hard.set(x, y, [0.85, 0.85, 0.85, 1.0]);
            }
        }
        let mut img = ImageBuffer::zeroed(w, h);
        for y in 0..h {
            for x in 0..w {
                let (xi, yi) = (x as i32, y as i32);
                let l = hard.at_clamped(xi - 1, yi);
                let c = hard.at(x, y);
                let r = hard.at_clamped(xi + 1, yi);
                let mut v = [0.0f32; 4];
                for ch in 0..3 {
                    v[ch] = 0.25 * l[ch] + 0.5 * c[ch] + 0.25 * r[ch];
                }
                v[3] = 1.0;
                img.set(x, y, v);
            }
        }
        img
    }

    /// Steepest adjacent step along a scanline.
    ///
    /// Total variation would be the obvious metric and is useless here: across
    /// a monotonic transition it telescopes to `max - min`, which sharpening
    /// leaves untouched by construction. What sharpening does is redistribute
    /// that same total into fewer, steeper steps — so the peak gradient is the
    /// thing to measure.
    fn max_gradient(img: &ImageBuffer, row: u32) -> f32 {
        let mut peak = 0.0f32;
        for x in 1..img.width() {
            peak = peak.max((luma(img.at(x, row)) - luma(img.at(x - 1, row))).abs());
        }
        peak
    }

    #[test]
    fn zero_sharpness_is_the_identity() {
        let src = soft_edge(16, 4);
        assert_eq!(rcas(&src, 0.0), src);
    }

    #[test]
    fn a_flat_image_is_untouched() {
        // There is nothing to sharpen, and inventing something would be a bug
        // visible on every wall and every patch of sky.
        let src = constant(8, 8, 0.5);
        let out = rcas(&src, 1.0);
        for y in 0..8 {
            for x in 0..8 {
                assert!((luma(out.at(x, y)) - 0.5).abs() < 1e-4);
            }
        }
    }

    #[test]
    fn sharpening_steepens_the_transition() {
        let src = soft_edge(32, 4);
        let out = rcas(&src, 1.0);
        let (before, after) = (max_gradient(&src, 2), max_gradient(&out, 2));
        assert!(after > before * 1.02, "no sharpening happened: {before} -> {after}");
    }

    #[test]
    fn a_linear_ramp_is_left_alone() {
        // Sharpening is a second-derivative operation. A constant-slope ramp
        // has none, so touching it would mean the pass is adding contrast that
        // was never in the image.
        let mut src = ImageBuffer::zeroed(16, 3);
        for y in 0..3 {
            for x in 0..16 {
                let v = 0.2 + 0.03 * x as f32;
                src.set(x, y, [v, v, v, 1.0]);
            }
        }
        let out = rcas(&src, 1.0);
        for x in 2..14 {
            let d = luma(out.at(x, 1)) - luma(src.at(x, 1));
            assert!(d.abs() < 1e-3, "x={x} moved by {d}");
        }
    }

    #[test]
    fn nothing_is_pushed_out_of_range() {
        // The whole premise: the limit is computed so the clamp is never needed.
        // If this fails, the pass is producing exactly the halo it exists to
        // avoid.
        let src = soft_edge(32, 4);
        let out = rcas(&src, 1.0);
        for y in 0..4 {
            for x in 0..32 {
                for c in 0..3 {
                    let v = out.at(x, y)[c];
                    assert!(v >= -1e-4 && v <= 1.0 + 1e-4, "({x},{y}) ch{c} = {v}");
                }
            }
        }
    }

    #[test]
    fn extremes_do_not_explode() {
        // Pure black next to pure white leaves no headroom in either direction;
        // the limit must collapse to nothing rather than divide by nearly zero.
        let mut src = ImageBuffer::new(9, 9, [0.0, 0.0, 0.0, 1.0]);
        for y in 0..9 {
            for x in 4..9 {
                src.set(x, y, [1.0, 1.0, 1.0, 1.0]);
            }
        }
        let out = rcas(&src, 1.0);
        for y in 0..9 {
            for x in 0..9 {
                for c in 0..3 {
                    let v = out.at(x, y)[c];
                    assert!(v.is_finite(), "({x},{y}) ch{c} = {v}");
                    assert!(v >= -1e-4 && v <= 1.0 + 1e-4, "({x},{y}) ch{c} = {v}");
                }
            }
        }
    }

    #[test]
    fn alpha_is_passed_through() {
        let mut src = ImageBuffer::new(8, 8, [0.2, 0.4, 0.6, 0.33]);
        src.set(4, 4, [0.9, 0.9, 0.9, 0.77]);
        let out = rcas(&src, 1.0);
        assert!((out.at(0, 0)[3] - 0.33).abs() < 1e-6);
        assert!((out.at(4, 4)[3] - 0.77).abs() < 1e-6);
    }

    #[test]
    fn sharpness_scales_monotonically() {
        let src = soft_edge(32, 4);
        let weak = max_gradient(&rcas(&src, 0.25), 2);
        let strong = max_gradient(&rcas(&src, 1.0), 2);
        assert!(strong > weak, "weak {weak}, strong {strong}");
    }
}
