//! The WGSL passes, checked against the CPU reference in `frameboost_core`.
//!
//! This is why both implementations exist. A shader that merely looks right in
//! a screenshot can be wrong in ways nobody finds for months — a disocclusion
//! mask inverted along one edge, a bilinear sample half a texel off, a depth
//! test comparing the wrong pair. Here the oracle says which.
//!
//! Every test skips itself if no adapter is available, so this suite is safe in
//! a headless environment. A software rasteriser is enough: these are ordinary
//! compute shaders.

use frameboost_core::frame::{DepthBuffer, GBuffer, ImageBuffer, MotionBuffer};
use frameboost_core::recon::{
    easu, interpolate_midpoint, rcas, reproject, EasuConfig, InterpolateConfig, ReprojectConfig,
};
use frameboost_core::signals::measure_pair;
use frameboost_gpu::{GpuContext, GpuGBuffer, Plane, ReconTarget, Reconstructor};

/// Absolute tolerance, for values near zero where a relative one is useless.
const EPS_ABS: f32 = 2e-4;

/// Relative tolerance.
///
/// A purely absolute bound is the wrong shape here. EASU with deringing
/// disabled legitimately overshoots past the input range — that overshoot is
/// what the clamp exists to remove — and in that regime the kernel normalises
/// by a small weight sum, which amplifies the difference between the CPU's
/// left-to-right summation and whatever order and fused multiply-adds the GPU
/// chose. 0.2% of magnitude covers that while still failing on any actual
/// divergence in the arithmetic, which shows up orders of magnitude larger.
const EPS_REL: f32 = 2e-3;

fn context() -> Option<GpuContext> {
    match GpuContext::new() {
        Ok(ctx) => {
            eprintln!("adapter: {} ({:?})", ctx.adapter_info().name, ctx.adapter_info().backend);
            Some(ctx)
        }
        Err(e) => {
            eprintln!("skipping GPU parity tests: {e}");
            None
        }
    }
}

macro_rules! gpu_test {
    ($ctx:ident) => {
        let Some($ctx) = context() else { return };
    };
}

/// A scene with edges, two depth layers moving at different rates, and enough
/// texture that photo-consistency has something to disagree about.
fn scene(w: u32, h: u32, shift: f32) -> GBuffer {
    let mut color = ImageBuffer::zeroed(w, h);
    let mut depth = DepthBuffer::new(w, h, 40.0);
    let mut motion = MotionBuffer::zeroed(w, h);

    for y in 0..h {
        for x in 0..w {
            let fx = x as f32;
            let fy = y as f32;
            // Foreground slab: moves at full rate, near depth.
            let foreground = (fx + shift * 0.0) > w as f32 * 0.35
                && fx < w as f32 * 0.60
                && fy > h as f32 * 0.20;

            let (base, d, parallax) = if foreground {
                (0.75, 2.0, 1.0)
            } else {
                (0.30, 40.0, 0.25)
            };

            let tex = 0.18 * ((fx * 0.7).sin() * (fy * 0.5).cos())
                + 0.10 * ((fx * 0.13 + fy * 0.21).sin());
            let v = (base + tex).clamp(0.02, 0.98);
            color.set(x, y, [v, v * 0.85, v * 0.6, 1.0]);
            depth.set(x, y, d);
            motion.set(x, y, [shift * parallax, 0.0]);
        }
    }
    GBuffer::new(color, depth, motion)
}

fn upload_color(ctx: &GpuContext, img: &ImageBuffer) -> Plane {
    let p = Plane::color(ctx, img.width(), img.height(), false);
    p.upload_color(ctx, img);
    p
}

fn compare(label: &str, cpu: &ImageBuffer, gpu: &ImageBuffer) {
    assert_eq!(
        (cpu.width(), cpu.height()),
        (gpu.width(), gpu.height()),
        "{label}: dimensions differ"
    );
    // Scored as a fraction of the allowance, so the reported worst offender is
    // the one that came closest to failing rather than merely the largest
    // absolute difference — which on an overshooting pass is always in the
    // brightest region regardless of whether anything is wrong.
    let mut worst = 0.0f32;
    let mut worst_at = (0u32, 0u32, 0usize);
    let mut worst_delta = 0.0f32;
    for y in 0..cpu.height() {
        for x in 0..cpu.width() {
            let (a, b) = (cpu.at(x, y), gpu.at(x, y));
            for c in 0..4 {
                let delta = (a[c] - b[c]).abs();
                let allowed = EPS_ABS + EPS_REL * a[c].abs().max(b[c].abs());
                let score = delta / allowed;
                if score > worst {
                    worst = score;
                    worst_delta = delta;
                    worst_at = (x, y, c);
                }
            }
        }
    }
    assert!(
        worst <= 1.0,
        "{label}: difference {worst_delta} at pixel {:?} channel {} is {worst:.2}x the          allowance (cpu {:?}, gpu {:?})",
        (worst_at.0, worst_at.1),
        worst_at.2,
        cpu.at(worst_at.0, worst_at.1),
        gpu.at(worst_at.0, worst_at.1)
    );
}

#[test]
fn every_shader_compiles() {
    gpu_test!(ctx);
    // Constructing this compiles all seven pipelines; a WGSL error panics here
    // with the compiler's message rather than at some later dispatch.
    let _ = Reconstructor::new(&ctx);
}

#[test]
fn easu_matches_the_cpu_reference() {
    gpu_test!(ctx);
    let recon = Reconstructor::new(&ctx);
    let src = scene(48, 32, 0.0).color;
    let (dw, dh) = (96, 64);

    let gpu_src = upload_color(&ctx, &src);
    let gpu_dst = Plane::color(&ctx, dw, dh, true);
    recon.easu(&ctx, &gpu_src, &gpu_dst, EasuConfig::default());

    let expected = easu(&src, dw, dh, EasuConfig::default());
    compare("easu", &expected, &gpu_dst.read_color(&ctx));
}

#[test]
fn easu_matches_with_deringing_disabled() {
    // The deringing clamp masks a lot. Without it the kernel's negative lobes
    // reach the output unmodified, so this is the stricter comparison of the
    // two — and the one that would catch a wrong tap weight.
    gpu_test!(ctx);
    let recon = Reconstructor::new(&ctx);
    let cfg = EasuConfig {
        deringing: false,
        ..Default::default()
    };
    let src = scene(40, 40, 0.0).color;

    let gpu_src = upload_color(&ctx, &src);
    let gpu_dst = Plane::color(&ctx, 71, 53, true);
    recon.easu(&ctx, &gpu_src, &gpu_dst, cfg);

    // 71 is deliberately not a multiple of the 256-byte copy alignment or the
    // workgroup size: readback padding and partial workgroups are exactly where
    // a GPU port shears.
    let expected = easu(&src, 71, 53, cfg);
    compare("easu no-deringing", &expected, &gpu_dst.read_color(&ctx));
}

#[test]
fn rcas_matches_the_cpu_reference() {
    gpu_test!(ctx);
    let recon = Reconstructor::new(&ctx);
    let src = scene(53, 37, 0.0).color;

    let gpu_src = upload_color(&ctx, &src);
    let gpu_dst = Plane::color(&ctx, src.width(), src.height(), true);
    recon.rcas(&ctx, &gpu_src, &gpu_dst, 0.7);

    compare("rcas", &rcas(&src, 0.7), &gpu_dst.read_color(&ctx));
}

#[test]
fn reprojection_matches_the_cpu_reference() {
    gpu_test!(ctx);
    let recon = Reconstructor::new(&ctx);
    let prev = scene(64, 48, 0.0);
    let cur = scene(64, 48, -5.0);

    let gpu_prev = GpuGBuffer::from_cpu(&ctx, &prev);
    let gpu_cur = GpuGBuffer::from_cpu(&ctx, &cur);
    let target = ReconTarget::new(&ctx, 64, 48);
    let gpu_disocclusion =
        recon.reproject(&ctx, &gpu_prev, &gpu_cur, &target, ReprojectConfig::default());

    let expected = reproject(&prev, &cur, ReprojectConfig::default());
    compare("reproject", &expected.color, &target.color().read_color(&ctx));
    assert!(
        (expected.disocclusion - gpu_disocclusion).abs() < 1e-5,
        "disocclusion differs: cpu {} vs gpu {}",
        expected.disocclusion,
        gpu_disocclusion
    );
    assert!(
        gpu_disocclusion > 0.0,
        "test scene produced no disocclusion, so it proves nothing"
    );
}

#[test]
fn interpolation_matches_the_cpu_reference() {
    gpu_test!(ctx);
    let recon = Reconstructor::new(&ctx);
    let prev = scene(64, 48, 0.0);
    let next = scene(64, 48, -6.0);
    let cfg = InterpolateConfig::default();

    let gpu_prev = GpuGBuffer::from_cpu(&ctx, &prev);
    let gpu_next = GpuGBuffer::from_cpu(&ctx, &next);
    let target = ReconTarget::new(&ctx, 64, 48);
    let gpu_disocclusion = recon.interpolate(&ctx, &gpu_prev, &gpu_next, 0.5, &target, cfg);

    let expected = interpolate_midpoint(&prev, &next, 0.5, cfg);
    compare(
        "interpolate",
        &expected.color,
        &target.color().read_color(&ctx),
    );
    assert!(
        (expected.disocclusion - gpu_disocclusion).abs() < 1e-5,
        "disocclusion differs: cpu {} vs gpu {}",
        expected.disocclusion,
        gpu_disocclusion
    );
    assert!(gpu_disocclusion > 0.0, "no disocclusion to compare");
}

#[test]
fn interpolation_matches_with_an_odd_hole_fill_count() {
    // An odd count ends the ping-pong in scratch. If the copy back were another
    // dilation rather than a copy, the GPU would quietly run one more pass than
    // asked for — and would still look plausible.
    gpu_test!(ctx);
    let recon = Reconstructor::new(&ctx);
    let prev = scene(56, 40, 0.0);
    let next = scene(56, 40, -7.0);
    let cfg = InterpolateConfig {
        hole_fill_passes: 3,
        ..Default::default()
    };

    let gpu_prev = GpuGBuffer::from_cpu(&ctx, &prev);
    let gpu_next = GpuGBuffer::from_cpu(&ctx, &next);
    let target = ReconTarget::new(&ctx, 56, 40);
    recon.interpolate(&ctx, &gpu_prev, &gpu_next, 0.5, &target, cfg);

    let expected = interpolate_midpoint(&prev, &next, 0.5, cfg);
    compare(
        "interpolate odd passes",
        &expected.color,
        &target.color().read_color(&ctx),
    );
}

#[test]
fn signal_measurement_matches_the_cpu_reference() {
    // The reduction runs on the GPU because reading a full frame back every
    // frame to compute five numbers would cost more than the boost saves. It
    // therefore has to produce the same five numbers.
    gpu_test!(ctx);
    let recon = Reconstructor::new(&ctx);
    let prev = scene(70, 45, 0.0);
    let next = scene(70, 45, -4.0);
    let reference_speed = 7.0;

    let gpu_prev = GpuGBuffer::from_cpu(&ctx, &prev);
    let gpu_next = GpuGBuffer::from_cpu(&ctx, &next);
    let got = recon.measure_pair(&ctx, &gpu_prev, &gpu_next, reference_speed);
    let expected = measure_pair(&prev, &next, reference_speed);

    for (name, a, b) in [
        ("motion_residual", expected.motion_residual, got.motion_residual),
        ("luma_shift", expected.luma_shift, got.luma_shift),
        ("camera_motion", expected.camera_motion, got.camera_motion),
        (
            "depth_complexity",
            expected.depth_complexity,
            got.depth_complexity,
        ),
    ] {
        assert!(
            (a - b).abs() < 1e-3,
            "{name}: cpu {a} vs gpu {b}\ncpu {expected:?}\ngpu {got:?}"
        );
    }
    assert!(
        expected.motion_residual > 0.0 && expected.depth_complexity > 0.0,
        "test scene is too bland to prove anything: {expected:?}"
    );
}
