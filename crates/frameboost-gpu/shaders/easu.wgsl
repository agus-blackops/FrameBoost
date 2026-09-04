// Edge-adaptive spatial upsampling.
//
// Bilinear filtering is isotropic, so it blurs across edges exactly as hard as
// along them — and edges are the only thing the eye measures sharpness by. This
// works out which way the local edge runs from the structure tensor of the luma
// gradient, then stretches the reconstruction kernel along it and squeezes it
// across. See `frameboost_core::recon::easu` for the derivation.

struct Params {
    src_w: u32,
    src_h: u32,
    dst_w: u32,
    dst_h: u32,
    anisotropy: f32,
    deringing: u32,
    _pad0: u32,
    _pad1: u32,
};

@group(0) @binding(0) var src: texture_2d<f32>;
@group(0) @binding(1) var dst: texture_storage_2d<rgba32float, write>;
@group(0) @binding(2) var<uniform> params: Params;

const CLAMP_D2: f32 = 2.0;

// Windowed Lanczos-2-like weight on squared distance: positive to d2 = 1,
// negative from there to 2, exactly zero at both ends. The negative lobe is
// what restores acutance, and what would ring without the deringing clamp.
fn tap_weight(d2: f32) -> f32 {
    let b = 0.4 * d2 - 1.0;
    let a = 0.5 * d2 - 1.0;
    return (b * b * (25.0 / 16.0) - (9.0 / 16.0)) * (a * a);
}

@compute @workgroup_size(8, 8, 1)
fn main(@builtin(global_invocation_id) gid: vec3<u32>) {
    if (gid.x >= params.dst_w || gid.y >= params.dst_h) {
        return;
    }
    let src_size = vec2<i32>(i32(params.src_w), i32(params.src_h));

    // Output pixel centre, mapped into source pixel space.
    let s = vec2<f32>(
        (f32(gid.x) + 0.5) * f32(params.src_w) / f32(params.dst_w),
        (f32(gid.y) + 0.5) * f32(params.src_h) / f32(params.dst_h),
    );
    let b = vec2<i32>(floor(s - vec2<f32>(0.5, 0.5)));

    var taps: array<vec4<f32>, 16>;
    var lum: array<f32, 16>;
    for (var j = 0; j < 4; j++) {
        for (var i = 0; i < 4; i++) {
            let c = textureLoad(
                src,
                fb_clamp_coord(b + vec2<i32>(i - 1, j - 1), src_size),
                0,
            );
            let k = j * 4 + i;
            taps[k] = c;
            lum[k] = fb_luma(c);
        }
    }

    // Structure tensor over the four inner taps — the only ones with
    // neighbours on both sides inside a 4x4.
    var jxx = 0.0;
    var jyy = 0.0;
    var jxy = 0.0;
    for (var j = 1; j < 3; j++) {
        for (var i = 1; i < 3; i++) {
            let gx = 0.5 * (lum[j * 4 + i + 1] - lum[j * 4 + i - 1]);
            let gy = 0.5 * (lum[(j + 1) * 4 + i] - lum[(j - 1) * 4 + i]);
            jxx += gx * gx;
            jyy += gy * gy;
            jxy += gx * gy;
        }
    }

    let trace = jxx + jyy;
    var edge_dir = vec2<f32>(1.0, 0.0);
    var anisotropy = 0.0;
    if (trace > 1e-8) {
        let diff = jxx - jyy;
        let disc = sqrt(max(diff * diff + 4.0 * jxy * jxy, 0.0));
        let l1 = 0.5 * (trace + disc);
        let l2 = 0.5 * (trace - disc);
        // 0 on flat or isotropic texture, 1 on a clean straight edge. Using the
        // eigenvalue gap rather than raw gradient magnitude is what keeps busy
        // texture from being mistaken for an edge and streaked along.
        anisotropy = clamp((l1 - l2) / max(l1 + l2, 1e-8), 0.0, 1.0);

        var g = vec2<f32>(1.0, 0.0);
        if (abs(jxy) > 1e-8) {
            g = vec2<f32>(l1 - jyy, jxy);
        } else if (jxx < jyy) {
            g = vec2<f32>(0.0, 1.0);
        }
        let glen = length(g);
        if (glen > 1e-8) {
            g = g / glen;
        }
        // The gradient runs across the edge; the edge runs perpendicular to it.
        edge_dir = vec2<f32>(-g.y, g.x);
    }

    let stretch = 1.0 + anisotropy * clamp(params.anisotropy, 0.0, 2.0);
    let perp = vec2<f32>(-edge_dir.y, edge_dir.x);

    var acc = vec4<f32>(0.0);
    var wsum = 0.0;
    for (var j = 0; j < 4; j++) {
        for (var i = 0; i < 4; i++) {
            let tap_centre = vec2<f32>(b + vec2<i32>(i - 1, j - 1)) + vec2<f32>(0.5, 0.5);
            let d = tap_centre - s;
            // Wider along the edge, narrower across it.
            let along = dot(d, edge_dir) / stretch;
            let across = dot(d, perp) * stretch;
            let d2 = min(along * along + across * across, CLAMP_D2);
            let w = tap_weight(d2);
            acc += taps[j * 4 + i] * w;
            wsum += w;
        }
    }

    var color: vec4<f32>;
    if (abs(wsum) > 1e-5) {
        color = acc / wsum;
    } else {
        color = fb_bilinear4(src, src_size, s);
    }

    if (params.deringing != 0u) {
        // Clamp to the four nearest taps: indices 5, 6, 9, 10 of the 4x4.
        var lo = taps[5];
        var hi = taps[5];
        lo = min(min(lo, taps[6]), min(taps[9], taps[10]));
        hi = max(max(hi, taps[6]), max(taps[9], taps[10]));
        color = clamp(color, lo, hi);
    }

    textureStore(dst, vec2<i32>(i32(gid.x), i32(gid.y)), color);
}
