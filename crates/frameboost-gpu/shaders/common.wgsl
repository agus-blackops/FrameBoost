// Shared helpers, textually prepended to every FrameBoost shader.
//
// The arithmetic here mirrors `frameboost_core::recon` deliberately and
// exactly. That crate's CPU implementations are the oracle these shaders are
// tested against, so any divergence — a different luma weight, a bilinear
// sample offset by half a texel — is a bug in one of the two, and the tests
// are what say which.

fn fb_luma(c: vec4<f32>) -> f32 {
    return 0.2126 * c.r + 0.7152 * c.g + 0.0722 * c.b;
}

// Relative, not absolute: a 10 cm discrepancy is a disocclusion on a weapon at
// arm's length and rounding error on a mountain 4 km away.
fn fb_relative_depth_delta(a: f32, b: f32) -> f32 {
    let scale = max(max(abs(a), abs(b)), 1e-4);
    return min(abs(a - b) / scale, 1.0);
}

// Soft rather than binary: a hard mask stamps its own edges into the output,
// and the boundary becomes a visible contour that crawls with the camera.
fn fb_soft_validity(error: f32, tolerance: f32) -> f32 {
    let t = max(tolerance, 1e-6);
    if (error <= t) {
        return 1.0;
    }
    if (error >= 2.0 * t) {
        return 0.0;
    }
    return 1.0 - (error - t) / t;
}

fn fb_clamp_coord(p: vec2<i32>, size: vec2<i32>) -> vec2<i32> {
    return clamp(p, vec2<i32>(0, 0), size - vec2<i32>(1, 1));
}

fn fb_contains(p: vec2<f32>, size: vec2<i32>) -> bool {
    return p.x >= 0.0 && p.y >= 0.0 && p.x < f32(size.x) && p.y < f32(size.y);
}

// Pixel (x, y) has its centre at (x + 0.5, y + 0.5). Every continuous
// coordinate in FrameBoost is in that space; an offset of half a texel here
// reads downstream as a persistent softness that is miserable to track down.
fn fb_bilinear4(t: texture_2d<f32>, size: vec2<i32>, p: vec2<f32>) -> vec4<f32> {
    let f = p - vec2<f32>(0.5, 0.5);
    let base = floor(f);
    let frac = f - base;
    let b = vec2<i32>(base);

    let c00 = textureLoad(t, fb_clamp_coord(b, size), 0);
    let c10 = textureLoad(t, fb_clamp_coord(b + vec2<i32>(1, 0), size), 0);
    let c01 = textureLoad(t, fb_clamp_coord(b + vec2<i32>(0, 1), size), 0);
    let c11 = textureLoad(t, fb_clamp_coord(b + vec2<i32>(1, 1), size), 0);

    let top = mix(c00, c10, frac.x);
    let bot = mix(c01, c11, frac.x);
    return mix(top, bot, frac.y);
}
