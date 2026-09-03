//! Edge-adaptive spatial upsampling.
//!
//! Bilinear upsampling is isotropic: it blurs equally in every direction, which
//! means it blurs *across* edges just as enthusiastically as along them. Edges
//! are the only thing in the frame the eye is actually measuring sharpness by,
//! so isotropic filtering spends all its error budget in the worst possible
//! place.
//!
//! The fix, and the idea behind FSR's EASU: work out which way the local edge
//! runs, then stretch the reconstruction kernel *along* it and squeeze it
//! *across* it. Averaging along an edge is free — the samples agree. Averaging
//! across one is what softness is made of.
//!
//! # How the direction is found
//!
//! From the structure tensor of the luma gradient over the tap neighbourhood.
//! Its principal eigenvector is the gradient direction (across the edge), the
//! other is the edge direction, and the normalized eigenvalue gap is a direct
//! measure of how edge-like the neighbourhood is:
//!
//! ```text
//!   anisotropy = (λ1 - λ2) / (λ1 + λ2)
//! ```
//!
//! One number, `0` on flat or isotropic texture and `1` on a clean straight
//! edge, which is exactly the knob the kernel needs. Using it rather than raw
//! gradient magnitude matters: magnitude alone cannot tell a real edge from
//! busy texture, and stretching a kernel along a direction that noise picked
//! produces streaking.
//!
//! # The kernel
//!
//! A windowed approximation of a Lanczos-2 lobe, evaluated on the squared
//! distance after the anisotropic warp:
//!
//! ```text
//!   wB = (2/5·d² − 1)²·25/16 − 9/16       wA = (½·d² − 1)²      w = wA·wB
//! ```
//!
//! Positive out to `d² = 1`, negative from there to `d² = 2`, exactly zero at
//! both ends. The negative lobe is what puts acutance back; it is also what
//! would ring, which is what the deringing clamp is for.
//!
//! # A note on fidelity
//!
//! This is the *structure* of EASU — direction, anisotropy, warped kernel,
//! deringing clamp — written to be read and tested. It is not a port of AMD's
//! implementation and will not match it pixel for pixel; the real one is
//! packed, approximated and hand-tuned in ways that make it fast and
//! unreadable. Judge this one by the tests at the bottom of the file.

use crate::frame::ImageBuffer;
use crate::signals::luma;

/// Tuning for [`easu`].
#[derive(Clone, Copy, Debug)]
pub struct EasuConfig {
    /// How far the kernel is warped at full anisotropy, in `0..=2`.
    ///
    /// At `1.0` a clean edge doubles the kernel's reach along itself and halves
    /// it across. Higher resolves edges harder and streaks more on anything
    /// that only looked like an edge.
    pub anisotropy_strength: f32,
    /// Clamp the result to the range of the four nearest taps.
    ///
    /// Keep this on. The kernel's negative lobe is what makes the output look
    /// resolved rather than smeared, and it is also what overshoots into a
    /// bright halo along every high-contrast edge. The clamp keeps the first
    /// and discards the second; disable it only to see what it is doing.
    pub deringing: bool,
}

impl Default for EasuConfig {
    fn default() -> Self {
        EasuConfig {
            anisotropy_strength: 1.0,
            deringing: true,
        }
    }
}

/// Kernel support: the squared distance at which the weight reaches zero.
const CLAMP_D2: f32 = 2.0;

/// Resample `src` to `out_w` x `out_h` with an edge-adaptive kernel.
///
/// # Panics
/// If either output dimension is zero.
pub fn easu(src: &ImageBuffer, out_w: u32, out_h: u32, cfg: EasuConfig) -> ImageBuffer {
    assert!(out_w > 0 && out_h > 0, "output dimensions must be non-zero");

    let (sw, sh) = (src.width(), src.height());
    let mut out = ImageBuffer::zeroed(out_w, out_h);
    let sx_scale = sw as f32 / out_w as f32;
    let sy_scale = sh as f32 / out_h as f32;
    let strength = cfg.anisotropy_strength.clamp(0.0, 2.0);

    for oy in 0..out_h {
        for ox in 0..out_w {
            // Output pixel center mapped into source pixel space.
            let sx = (ox as f32 + 0.5) * sx_scale;
            let sy = (oy as f32 + 0.5) * sy_scale;

            // 4x4 gather around it. `bx`,`by` index the tap below-left of the
            // sample, so the taps span bx-1 ..= bx+2.
            let bx = (sx - 0.5).floor() as i32;
            let by = (sy - 0.5).floor() as i32;

            let mut taps = [[0.0f32; 4]; 16];
            let mut lum = [0.0f32; 16];
            for j in 0..4i32 {
                for i in 0..4i32 {
                    let c = src.at_clamped(bx - 1 + i, by - 1 + j);
                    let k = (j * 4 + i) as usize;
                    taps[k] = c;
                    lum[k] = luma(c);
                }
            }

            let (edge_dir, anisotropy) = analyze(&lum);
            let stretch = 1.0 + anisotropy * strength;
            let perp = [-edge_dir[1], edge_dir[0]];

            let mut acc = [0.0f32; 4];
            let mut wsum = 0.0f32;
            for j in 0..4i32 {
                for i in 0..4i32 {
                    let tap_x = (bx - 1 + i) as f32 + 0.5;
                    let tap_y = (by - 1 + j) as f32 + 0.5;
                    let d = [tap_x - sx, tap_y - sy];

                    // Warp into the edge frame: wider along, narrower across.
                    let along = (d[0] * edge_dir[0] + d[1] * edge_dir[1]) / stretch;
                    let across = (d[0] * perp[0] + d[1] * perp[1]) * stretch;
                    let d2 = (along * along + across * across).min(CLAMP_D2);

                    let w = tap_weight(d2);
                    let c = taps[(j * 4 + i) as usize];
                    for ch in 0..4 {
                        acc[ch] += c[ch] * w;
                    }
                    wsum += w;
                }
            }

            let mut color = if wsum.abs() > 1e-5 {
                let inv = 1.0 / wsum;
                [acc[0] * inv, acc[1] * inv, acc[2] * inv, acc[3] * inv]
            } else {
                // Degenerate weights: fall back to something defined rather
                // than dividing by ~0 and painting a NaN into the frame.
                src.sample_bilinear(sx, sy)
            };

            if cfg.deringing {
                // The four nearest taps are indices 5, 6, 9, 10 of the 4x4.
                for ch in 0..4 {
                    let mut lo = f32::INFINITY;
                    let mut hi = f32::NEG_INFINITY;
                    for k in [5usize, 6, 9, 10] {
                        lo = lo.min(taps[k][ch]);
                        hi = hi.max(taps[k][ch]);
                    }
                    color[ch] = color[ch].clamp(lo, hi);
                }
            }

            out.set(ox, oy, color);
        }
    }

    out
}

/// Windowed Lanczos-2-like tap weight on squared distance.
#[inline]
fn tap_weight(d2: f32) -> f32 {
    let b = 0.4 * d2 - 1.0;
    let a = 0.5 * d2 - 1.0;
    (b * b * (25.0 / 16.0) - (9.0 / 16.0)) * (a * a)
}

/// Edge direction and anisotropy from the structure tensor of a 4x4 luma patch.
///
/// Gradients are central differences at the four inner taps — the only ones
/// with neighbours on both sides in a 4x4.
fn analyze(lum: &[f32; 16]) -> ([f32; 2], f32) {
    let at = |i: i32, j: i32| lum[(j * 4 + i) as usize];

    let (mut jxx, mut jyy, mut jxy) = (0.0f32, 0.0f32, 0.0f32);
    for j in 1..3i32 {
        for i in 1..3i32 {
            let gx = 0.5 * (at(i + 1, j) - at(i - 1, j));
            let gy = 0.5 * (at(i, j + 1) - at(i, j - 1));
            jxx += gx * gx;
            jyy += gy * gy;
            jxy += gx * gy;
        }
    }

    // Eigenvalues of the symmetric 2x2 tensor.
    let trace = jxx + jyy;
    let diff = jxx - jyy;
    let disc = (diff * diff + 4.0 * jxy * jxy).max(0.0).sqrt();
    let l1 = 0.5 * (trace + disc);
    let l2 = 0.5 * (trace - disc);

    if trace <= 1e-8 {
        // Flat: no direction to speak of. Anisotropy 0 leaves the kernel
        // isotropic, which is the right answer for a flat neighbourhood.
        return ([1.0, 0.0], 0.0);
    }

    let anisotropy = ((l1 - l2) / (l1 + l2).max(1e-8)).clamp(0.0, 1.0);

    // Principal eigenvector: the gradient, across the edge. The edge itself
    // runs perpendicular to it.
    let (gx, gy) = if jxy.abs() > 1e-8 {
        (l1 - jyy, jxy)
    } else if jxx >= jyy {
        (1.0, 0.0)
    } else {
        (0.0, 1.0)
    };
    let len = (gx * gx + gy * gy).sqrt();
    let grad = if len > 1e-8 {
        [gx / len, gy / len]
    } else {
        [1.0, 0.0]
    };

    ([-grad[1], grad[0]], anisotropy)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn constant(w: u32, h: u32, v: f32) -> ImageBuffer {
        ImageBuffer::new(w, h, [v, v, v, 1.0])
    }

    /// A vertical step edge: dark left, bright right.
    fn step_edge(w: u32, h: u32, at: u32) -> ImageBuffer {
        let mut img = ImageBuffer::new(w, h, [0.1, 0.1, 0.1, 1.0]);
        for y in 0..h {
            for x in at..w {
                img.set(x, y, [0.9, 0.9, 0.9, 1.0]);
            }
        }
        img
    }

    fn bilinear(src: &ImageBuffer, out_w: u32, out_h: u32) -> ImageBuffer {
        let mut out = ImageBuffer::zeroed(out_w, out_h);
        for oy in 0..out_h {
            for ox in 0..out_w {
                let sx = (ox as f32 + 0.5) * src.width() as f32 / out_w as f32;
                let sy = (oy as f32 + 0.5) * src.height() as f32 / out_h as f32;
                out.set(ox, oy, src.sample_bilinear(sx, sy));
            }
        }
        out
    }

    /// Width of the transition, in output pixels, along a horizontal scanline.
    /// Smaller is sharper.
    fn transition_width(img: &ImageBuffer, row: u32) -> u32 {
        let (lo, hi) = (0.1f32, 0.9f32);
        let low_t = lo + 0.15 * (hi - lo);
        let high_t = lo + 0.85 * (hi - lo);
        let mut count = 0;
        for x in 0..img.width() {
            let v = luma(img.at(x, row));
            if v > low_t && v < high_t {
                count += 1;
            }
        }
        count
    }

    #[test]
    fn output_has_the_requested_size() {
        let out = easu(&constant(8, 8, 0.5), 19, 7, EasuConfig::default());
        assert_eq!((out.width(), out.height()), (19, 7));
    }

    #[test]
    fn a_flat_image_stays_flat() {
        // The kernel's negative lobe must sum away to nothing on flat input,
        // or every uniform surface picks up a texture that is not there.
        let out = easu(&constant(8, 8, 0.42), 24, 24, EasuConfig::default());
        for y in 0..out.height() {
            for x in 0..out.width() {
                let c = out.at(x, y);
                assert!((c[0] - 0.42).abs() < 1e-3, "({x},{y}) = {c:?}");
                assert!((c[3] - 1.0).abs() < 1e-3, "alpha drifted: {c:?}");
            }
        }
    }

    #[test]
    fn edges_stay_sharper_than_bilinear() {
        // The entire justification for the pass.
        let src = step_edge(16, 16, 8);
        let e = easu(&src, 48, 48, EasuConfig::default());
        let b = bilinear(&src, 48, 48);
        let (we, wb) = (transition_width(&e, 24), transition_width(&b, 24));
        assert!(we < wb, "easu {we} px transition vs bilinear {wb} px");
    }

    #[test]
    fn deringing_prevents_overshoot() {
        // Without the clamp the negative lobe overshoots past the input range
        // and paints a halo. With it, nothing may exceed the source extremes.
        let src = step_edge(16, 16, 8);
        let out = easu(&src, 48, 48, EasuConfig::default());
        for y in 0..out.height() {
            for x in 0..out.width() {
                let v = luma(out.at(x, y));
                assert!(v >= 0.1 - 1e-3 && v <= 0.9 + 1e-3, "({x},{y}) rang to {v}");
            }
        }
    }

    #[test]
    fn anisotropy_is_high_on_an_edge_and_low_on_flat() {
        let mut flat = [0.5f32; 16];
        assert!(analyze(&flat).1 < 1e-3);

        // A clean vertical edge in the patch.
        for j in 0..4 {
            for i in 0..4 {
                flat[j * 4 + i] = if i < 2 { 0.0 } else { 1.0 };
            }
        }
        let (dir, aniso) = analyze(&flat);
        assert!(aniso > 0.9, "anisotropy {aniso}");
        // A vertical edge runs vertically: the direction should be near ±Y.
        assert!(dir[1].abs() > 0.9, "edge direction {dir:?}");
    }

    #[test]
    fn a_single_pixel_source_does_not_panic() {
        let out = easu(&constant(1, 1, 0.3), 8, 8, EasuConfig::default());
        assert_eq!((out.width(), out.height()), (8, 8));
        assert!((out.at(4, 4)[0] - 0.3).abs() < 1e-3);
    }

    #[test]
    fn downscaling_is_defined_even_though_it_is_not_the_point() {
        let out = easu(&step_edge(32, 32, 16), 8, 8, EasuConfig::default());
        for y in 0..8 {
            for x in 0..8 {
                assert!(out.at(x, y).iter().all(|v| v.is_finite()));
            }
        }
    }
}
