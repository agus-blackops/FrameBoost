// Robust contrast-adaptive sharpening.
//
// Rather than sharpen and clip, compute per pixel the largest lobe that cannot
// push any channel past 0 or 1, and use that. Against a near-white edge the
// limit approaches zero and the pass backs off on its own — which is why it
// does not produce the halo that gives cheap sharpening away.

struct Params {
    width: u32,
    height: u32,
    sharpness: f32,
    _pad: u32,
};

@group(0) @binding(0) var src: texture_2d<f32>;
@group(0) @binding(1) var dst: texture_storage_2d<rgba32float, write>;
@group(0) @binding(2) var<uniform> params: Params;

// The kernel is (lobe*(b+d+f+h) + e) / (4*lobe + 1), whose denominator reaches
// zero at -0.25. This keeps a margin from that singularity; nearer to it the
// gain runs away and single pixels detonate.
const LOBE_LIMIT: f32 = -0.1875;

@compute @workgroup_size(8, 8, 1)
fn main(@builtin(global_invocation_id) gid: vec3<u32>) {
    if (gid.x >= params.width || gid.y >= params.height) {
        return;
    }
    let size = vec2<i32>(i32(params.width), i32(params.height));
    let p = vec2<i32>(i32(gid.x), i32(gid.y));

    let e = textureLoad(src, p, 0);
    if (params.sharpness <= 0.0) {
        textureStore(dst, p, e);
        return;
    }

    //     b
    //   d e f
    //     h
    let b = textureLoad(src, fb_clamp_coord(p + vec2<i32>(0, -1), size), 0);
    let d = textureLoad(src, fb_clamp_coord(p + vec2<i32>(-1, 0), size), 0);
    let f = textureLoad(src, fb_clamp_coord(p + vec2<i32>(1, 0), size), 0);
    let h = textureLoad(src, fb_clamp_coord(p + vec2<i32>(0, 1), size), 0);

    // Weakest lobe across the three channels. Sharpening channels by different
    // amounts moves them apart, which shows up as colour fringing on exactly
    // the edges this pass exists to improve.
    var lobe = 0.0;
    for (var c = 0; c < 3; c++) {
        let ring_min = min(min(b[c], d[c]), min(f[c], h[c]));
        let ring_max = max(max(b[c], d[c]), max(f[c], h[c]));
        let hit_min = ring_min / max(4.0 * ring_max, 1e-6);
        let hit_max = (1.0 - ring_max) / min(4.0 * ring_min - 4.0, -1e-6);
        let ch_lobe = max(-hit_min, hit_max);
        if (c == 0) {
            lobe = ch_lobe;
        } else {
            lobe = max(lobe, ch_lobe);
        }
    }

    lobe = clamp(lobe, LOBE_LIMIT, 0.0) * clamp(params.sharpness, 0.0, 1.0);
    let denom = 4.0 * lobe + 1.0;

    var color = e;
    if (abs(denom) > 1e-4) {
        let ring = b + d + f + h;
        for (var c = 0; c < 3; c++) {
            let v = (lobe * ring[c] + e[c]) / denom;
            if (v == v) {
                color[c] = v;
            }
        }
    }
    textureStore(dst, p, color);
}
