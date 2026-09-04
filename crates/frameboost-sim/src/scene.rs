//! A synthetic renderer, good enough to break reconstruction honestly.
//!
//! The simulator would prove nothing if it fed the controller invented signal
//! values — it would only demonstrate that a state machine transitions when you
//! tell it to. So it renders an actual scene, runs the actual reconstruction
//! passes over it, and measures the actual signals from the result.
//!
//! The scene is three parallax layers. That single choice is what makes it
//! useful: layers at different depths move at different screen rates, so any
//! camera motion at all exposes background that was hidden behind foreground
//! last frame. That is real disocclusion, arising from geometry rather than
//! from a constant someone picked, and it is the failure the whole system is
//! built around.
//!
//! Motion vectors are exact by construction — every surface's previous screen
//! position is known analytically. That is deliberate: it means every
//! motion-residual the simulator reports comes from content the vectors
//! genuinely do not describe (particles, a flash, a cut), never from vectors
//! that were merely approximate. A real renderer's vectors are worse than
//! this, so the scenarios here are the optimistic case.

use frameboost_core::frame::{DepthBuffer, GBuffer, ImageBuffer, MotionBuffer};

/// Depth and parallax rate of one layer.
#[derive(Clone, Copy)]
struct Layer {
    depth: f32,
    /// Screen movement per unit of camera movement. Near layers move most.
    parallax: f32,
    tint: [f32; 3],
}

const FOREGROUND: Layer = Layer {
    depth: 2.0,
    parallax: 1.0,
    tint: [1.0, 0.82, 0.55],
};
const MIDGROUND: Layer = Layer {
    depth: 12.0,
    parallax: 0.45,
    tint: [0.62, 0.78, 0.70],
};
const BACKGROUND: Layer = Layer {
    depth: 60.0,
    parallax: 0.12,
    tint: [0.45, 0.55, 0.85],
};

/// Where the camera is, and what the scene looks like right now.
#[derive(Clone, Copy, Debug)]
pub struct Frame {
    /// Camera position in output pixels, at parallax 1.
    pub camera: [f32; 2],
    /// Camera position last frame.
    pub prev_camera: [f32; 2],
    /// Additive luminance across the whole frame — an exposure change, a flash.
    pub flash: f32,
    /// Fraction of pixels carrying content the motion vectors do not describe.
    pub particles: f32,
    /// Changes when the scene cuts, reshuffling all procedural content.
    pub seed: u32,
    /// Frame index, used only to re-roll particles each frame.
    ///
    /// Without it the "unexplained content" is identical every frame, which
    /// makes it perfectly explainable — it reprojects onto itself and every
    /// consistency check passes. Static noise is texture, not an artifact
    /// source.
    pub tick: u32,
}

/// The scene being rendered.
#[derive(Clone, Copy, Debug)]
pub struct Scene {
    /// Output width in pixels — the resolution world coordinates are defined in.
    pub output_width: u32,
    /// Output height in pixels.
    pub output_height: u32,
    /// Vertical foreground bars per output width.
    ///
    /// A handful gives ordinary silhouettes. A hundred gives a chain-link
    /// fence: sub-pixel geometry that no spatial upscaler resolves and no
    /// reprojection survives.
    pub foreground_bars: u32,
    /// Bar width as a fraction of their spacing.
    pub bar_duty: f32,
}

impl Scene {
    /// A scene with ordinary, well-separated foreground objects.
    pub fn open(output_width: u32, output_height: u32) -> Self {
        Scene {
            output_width,
            output_height,
            foreground_bars: bars_for_spacing(output_width, 32.0),
            bar_duty: 0.28,
        }
    }

    /// A scene dominated by thin geometry.
    ///
    /// Bars roughly 2.5 output pixels wide: cleanly resolved at native,
    /// marginal at 0.77, gone by half resolution. Difficult content with a real
    /// answer somewhere on the ladder — sub-pixel bars at native would only
    /// demonstrate that the controller gives up, which is not interesting.
    pub fn fence(output_width: u32, output_height: u32) -> Self {
        Scene {
            output_width,
            output_height,
            foreground_bars: bars_for_spacing(output_width, 5.0),
            bar_duty: 0.5,
        }
    }

    /// Render at an arbitrary resolution.
    ///
    /// World coordinates stay in output-pixel units whatever the render
    /// resolution is, so lowering the resolution shows the same scene with
    /// fewer samples — which is the point of a spatial rung, and the reason
    /// thin geometry starts disappearing when one engages.
    ///
    /// Motion vectors come back in *render* pixels, matching the buffer they
    /// belong to.
    pub fn render(&self, width: u32, height: u32, f: &Frame) -> GBuffer {
        let scale = self.output_width as f32 / width as f32;
        let scale_y = self.output_height as f32 / height as f32;

        let mut color = ImageBuffer::zeroed(width, height);
        let mut depth = DepthBuffer::new(width, height, BACKGROUND.depth);
        let mut motion = MotionBuffer::zeroed(width, height);

        let d_cam = [
            f.camera[0] - f.prev_camera[0],
            f.camera[1] - f.prev_camera[1],
        ];

        for y in 0..height {
            for x in 0..width {
                // Screen position in output-pixel units.
                let sx = (x as f32 + 0.5) * scale;
                let sy = (y as f32 + 0.5) * scale_y;

                let layer = self.layer_at(sx, sy, f);
                let wx = sx + f.camera[0] * layer.parallax;
                let wy = sy + f.camera[1] * layer.parallax;

                let mut c = self.shade(wx, wy, &layer, f.seed);
                if f.flash != 0.0 {
                    for ch in c.iter_mut().take(3) {
                        *ch = (*ch + f.flash).clamp(0.0, 1.0);
                    }
                }

                // Unexplained content: overlaid, and left out of the motion
                // vectors entirely — which is exactly what particles, alpha and
                // shader animation do in a real renderer.
                if f.particles > 0.0 {
                    let roll = f.seed.wrapping_add(f.tick.wrapping_mul(0x9e3779b1));
                    let r = hash_f(x ^ 0x9e37, y ^ 0x85eb, roll);
                    if r < f.particles {
                        let spark = hash_f(x, y, roll.wrapping_mul(2654435761));
                        c = [
                            (c[0] + spark).min(1.0),
                            (c[1] + spark * 0.7).min(1.0),
                            (c[2] + spark * 0.3).min(1.0),
                            1.0,
                        ];
                    }
                }

                color.set(x, y, c);
                depth.set(x, y, layer.depth);
                // Previous screen position, in render pixels.
                motion.set(
                    x,
                    y,
                    [
                        d_cam[0] * layer.parallax / scale,
                        d_cam[1] * layer.parallax / scale_y,
                    ],
                );
            }
        }

        GBuffer::new(color, depth, motion)
    }

    /// Front-to-back visibility test at a screen position.
    fn layer_at(&self, sx: f32, sy: f32, f: &Frame) -> Layer {
        let w = self.output_width as f32;
        let h = self.output_height as f32;

        // Foreground: vertical bars, fixed in world space.
        let bars = self.foreground_bars.max(1) as f32;
        let spacing = w / bars;
        let wx = sx + f.camera[0] * FOREGROUND.parallax;
        let phase = (wx / spacing).rem_euclid(1.0);
        if phase < self.bar_duty {
            return FOREGROUND;
        }

        // Midground: terrain occupying the lower third, with a wavy silhouette.
        let mwx = sx + f.camera[0] * MIDGROUND.parallax;
        let horizon = h * 0.66 + (mwx * 0.05).sin() * h * 0.06;
        if sy > horizon {
            return MIDGROUND;
        }

        BACKGROUND
    }

    /// Procedural texture, tinted per layer.
    ///
    /// Deliberately busy. Flat content would make photo-consistency trivially
    /// happy and every scenario would pass for the wrong reason.
    fn shade(&self, wx: f32, wy: f32, layer: &Layer, seed: u32) -> [f32; 4] {
        let n = value_noise(wx * 0.09, wy * 0.09, seed) * 0.55
            + value_noise(wx * 0.31, wy * 0.27, seed ^ 0x51ed) * 0.30
            + value_noise(wx * 0.77, wy * 0.71, seed ^ 0x2f9d) * 0.15;
        let stripes = 0.12 * ((wx * 0.22).sin() * (wy * 0.17).cos());
        let v = (0.28 + 0.62 * n + stripes).clamp(0.02, 0.98);
        [v * layer.tint[0], v * layer.tint[1], v * layer.tint[2], 1.0]
    }
}

/// Bar count giving a fixed bar spacing in output pixels.
///
/// Content has to be the same physical size at every output resolution, or the
/// simulator measures the resolution it was run at rather than the scenario.
/// A fixed bar *count* means the bars go sub-pixel as soon as anyone runs a
/// smaller configuration, and a scenario about difficult geometry silently
/// becomes a scenario about impossible geometry.
fn bars_for_spacing(output_width: u32, spacing_px: f32) -> u32 {
    ((output_width as f32 / spacing_px).round() as u32).max(2)
}

/// Bilinearly interpolated value noise. Deterministic, no dependencies.
fn value_noise(x: f32, y: f32, seed: u32) -> f32 {
    let xi = x.floor();
    let yi = y.floor();
    let tx = smoothstep(x - xi);
    let ty = smoothstep(y - yi);
    let (ix, iy) = (xi as i32, yi as i32);

    let c00 = lattice(ix, iy, seed);
    let c10 = lattice(ix + 1, iy, seed);
    let c01 = lattice(ix, iy + 1, seed);
    let c11 = lattice(ix + 1, iy + 1, seed);

    let top = c00 + (c10 - c00) * tx;
    let bot = c01 + (c11 - c01) * tx;
    top + (bot - top) * ty
}

fn smoothstep(t: f32) -> f32 {
    t * t * (3.0 - 2.0 * t)
}

fn lattice(x: i32, y: i32, seed: u32) -> f32 {
    hash_f(x as u32, y as u32, seed)
}

/// Wang-style integer hash, mapped to `0..1`.
fn hash_f(x: u32, y: u32, seed: u32) -> f32 {
    let mut h =
        x.wrapping_mul(0x9e3779b1) ^ y.wrapping_mul(0x85ebca6b) ^ seed.wrapping_mul(0xc2b2ae35);
    h ^= h >> 16;
    h = h.wrapping_mul(0x7feb352d);
    h ^= h >> 15;
    h = h.wrapping_mul(0x846ca68b);
    h ^= h >> 16;
    h as f32 / u32::MAX as f32
}

#[cfg(test)]
mod tests {
    use super::*;
    use frameboost_core::recon::{reproject, ReprojectConfig};

    fn frame(cam: f32, prev: f32) -> Frame {
        Frame {
            camera: [cam, 0.0],
            prev_camera: [prev, 0.0],
            flash: 0.0,
            particles: 0.0,
            seed: 7,
            tick: 0,
        }
    }

    #[test]
    fn a_still_camera_produces_no_motion() {
        let scene = Scene::open(160, 90);
        let g = scene.render(160, 90, &frame(0.0, 0.0));
        for y in 0..90 {
            for x in 0..160 {
                assert_eq!(g.motion.at(x, y), [0.0, 0.0]);
            }
        }
    }

    #[test]
    fn panning_disoccludes_at_foreground_silhouettes() {
        // The reason the scene has parallax at all. If layers moved together
        // there would be nothing for reconstruction to get wrong.
        let scene = Scene::open(160, 90);
        let prev = scene.render(160, 90, &frame(0.0, 0.0));
        let cur = scene.render(160, 90, &frame(6.0, 0.0));
        let r = reproject(&prev, &cur, ReprojectConfig::default());
        assert!(
            r.disocclusion > 0.02,
            "expected real disocclusion, got {}",
            r.disocclusion
        );
        assert!(
            r.disocclusion < 0.60,
            "a 6 px pan should not disocclude most of the screen: {}",
            r.disocclusion
        );
    }

    #[test]
    fn motion_vectors_are_exact_for_a_static_scene() {
        // With no camera movement, reprojection must be perfect. Any
        // disocclusion here would be a bug in the scene, and every scenario
        // built on it would be measuring that bug.
        let scene = Scene::open(160, 90);
        let a = scene.render(160, 90, &frame(3.0, 3.0));
        let b = scene.render(160, 90, &frame(3.0, 3.0));
        let r = reproject(&a, &b, ReprojectConfig::default());
        assert_eq!(r.disocclusion, 0.0);
    }

    #[test]
    fn a_fence_has_far_more_depth_complexity_than_open_terrain() {
        let open = Scene::open(160, 90).render(160, 90, &frame(0.0, 0.0));
        let fence = Scene::fence(160, 90).render(160, 90, &frame(0.0, 0.0));
        let count = |g: &GBuffer| {
            let mut n = 0;
            for y in 0..g.height() {
                for x in 1..g.width() {
                    if (g.depth.at(x, y) - g.depth.at(x - 1, y)).abs() > 0.1 {
                        n += 1;
                    }
                }
            }
            n
        };
        assert!(count(&fence) > count(&open) * 3);
    }

    #[test]
    fn rendering_smaller_shows_the_same_scene() {
        // A spatial rung must change the sample count, not the framing.
        let scene = Scene::open(160, 90);
        let full = scene.render(160, 90, &frame(0.0, 0.0));
        let half = scene.render(80, 45, &frame(0.0, 0.0));
        let mean = |g: &GBuffer| {
            let mut s = 0.0;
            for y in 0..g.height() {
                for x in 0..g.width() {
                    s += g.color.at(x, y)[1];
                }
            }
            s / (g.width() * g.height()) as f32
        };
        assert!((mean(&full) - mean(&half)).abs() < 0.05);
    }

    #[test]
    fn motion_vectors_scale_with_render_resolution() {
        let scene = Scene::open(160, 90);
        let full = scene.render(160, 90, &frame(8.0, 0.0));
        let half = scene.render(80, 45, &frame(8.0, 0.0));
        // Same displacement in world terms is half as many pixels at half res.
        let a = full.motion.at(80, 10)[0].abs();
        let b = half.motion.at(40, 5)[0].abs();
        assert!(a > 0.0 && (a / 2.0 - b).abs() < 0.2, "{a} vs {b}");
    }
}
