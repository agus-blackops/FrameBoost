// Warp the previous frame into the current one, and find out where it breaks.
//
// A gather, not a scatter: each destination reads one source at p + mv(p). No
// atomics, and no gaps left by pixels nothing happened to land on.
//
// The disocclusion mask this produces is the single most informative thing the
// controller gets, and every temporal artefact anyone complains about lives in
// the pixels it marks.

struct Params {
    width: u32,
    height: u32,
    depth_tolerance: f32,
    _pad: u32,
};

@group(0) @binding(0) var prev_color: texture_2d<f32>;
@group(0) @binding(1) var prev_depth: texture_2d<f32>;
@group(0) @binding(2) var cur_color: texture_2d<f32>;
@group(0) @binding(3) var cur_depth: texture_2d<f32>;
@group(0) @binding(4) var cur_motion: texture_2d<f32>;
@group(0) @binding(5) var out_color: texture_storage_2d<rgba32float, write>;
@group(0) @binding(6) var out_validity: texture_storage_2d<r32float, write>;
@group(0) @binding(7) var<uniform> params: Params;

@compute @workgroup_size(8, 8, 1)
fn main(@builtin(global_invocation_id) gid: vec3<u32>) {
    if (gid.x >= params.width || gid.y >= params.height) {
        return;
    }
    let size = vec2<i32>(i32(params.width), i32(params.height));
    let p = vec2<i32>(i32(gid.x), i32(gid.y));

    let mv = textureLoad(cur_motion, p, 0).xy;
    let here = textureLoad(cur_color, p, 0);
    let src = vec2<f32>(p) + vec2<f32>(0.5, 0.5) + mv;

    if (!fb_contains(src, size)) {
        // Off-screen last frame: it is new, and nothing is known about it.
        // Never leave a hole — a wrong-but-plausible pixel beats a black one.
        textureStore(out_color, p, here);
        textureStore(out_validity, p, vec4<f32>(0.0));
        return;
    }

    // Depth is point-sampled while colour is filtered. Filtering depth across a
    // silhouette averages foreground and background into a value matching
    // neither, which turns the one test standing between you and a smear into a
    // coin flip exactly at the edges where it matters.
    let src_depth = textureLoad(prev_depth, fb_clamp_coord(vec2<i32>(floor(src)), size), 0).r;
    let dst_depth = textureLoad(cur_depth, p, 0).r;
    let v = fb_soft_validity(
        fb_relative_depth_delta(src_depth, dst_depth),
        params.depth_tolerance,
    );

    if (v <= 0.0) {
        textureStore(out_color, p, here);
        textureStore(out_validity, p, vec4<f32>(0.0));
    } else {
        textureStore(out_color, p, fb_bilinear4(prev_color, size, src));
        textureStore(out_validity, p, vec4<f32>(v, 0.0, 0.0, 0.0));
    }
}
