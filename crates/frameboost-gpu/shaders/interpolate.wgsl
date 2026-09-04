// Build a frame that was never rendered.
//
// A surface's position is linear in time across one rendered interval:
//
//     x(s) = x1 + (1 - s) * mv        s = 0 at prev, s = 1 at next
//
// Inverting for the output pixel q we want to fill at time t, so x(t) = q:
//
//     sample next at  q - (1 - t) * mv
//     sample prev at  q + t * mv
//
// At t = 0.5 those are q + mv/2 and q - mv/2. Good sanity check when porting.
//
// The vector used is mv(q) from the newer frame — a gather approximation that
// assumes the field is locally smooth. It is fine across surfaces and wrong
// across silhouettes, which is where the two validity checks earn their keep:
// depth catches correspondences crossing a silhouette, photo-consistency
// catches everything the motion vectors never described at all (particles,
// alpha, shader animation, a light turning on). That second class is what makes
// shipped frame generation look broken, and geometry alone never warns you.

struct Params {
    width: u32,
    height: u32,
    t: f32,
    depth_tolerance: f32,
    photo_tolerance: f32,
    _pad0: u32,
    _pad1: u32,
    _pad2: u32,
};

@group(0) @binding(0) var prev_color: texture_2d<f32>;
@group(0) @binding(1) var prev_depth: texture_2d<f32>;
@group(0) @binding(2) var next_color: texture_2d<f32>;
@group(0) @binding(3) var next_depth: texture_2d<f32>;
@group(0) @binding(4) var next_motion: texture_2d<f32>;
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
    let t = clamp(params.t, 0.0, 1.0);

    let mv = textureLoad(next_motion, p, 0).xy;
    let q = vec2<f32>(p) + vec2<f32>(0.5, 0.5);
    let pn = q - (1.0 - t) * mv;
    let pp = q + t * mv;

    if (fb_contains(pn, size) && fb_contains(pp, size)) {
        let cn = fb_bilinear4(next_color, size, pn);
        let cp = fb_bilinear4(prev_color, size, pp);
        let dn = textureLoad(next_depth, fb_clamp_coord(vec2<i32>(floor(pn)), size), 0).r;
        let dp = textureLoad(prev_depth, fb_clamp_coord(vec2<i32>(floor(pp)), size), 0).r;

        let depth_ok = fb_soft_validity(
            fb_relative_depth_delta(dn, dp),
            params.depth_tolerance,
        );
        let photo_ok = fb_soft_validity(
            abs(fb_luma(cn) - fb_luma(cp)),
            max(params.photo_tolerance, 1e-4),
        );
        let confidence = depth_ok * photo_ok;

        if (confidence > 0.0) {
            textureStore(out_color, p, mix(cp, cn, t));
            textureStore(out_validity, p, vec4<f32>(confidence, 0.0, 0.0, 0.0));
            return;
        }
    }

    // No believable correspondence. Hold the nearest frame in time rather than
    // warping something we do not trust — warping a bad correspondence is
    // precisely how you get smear.
    var held: vec4<f32>;
    if (t < 0.5) {
        held = textureLoad(prev_color, p, 0);
    } else {
        held = textureLoad(next_color, p, 0);
    }
    textureStore(out_color, p, held);
    textureStore(out_validity, p, vec4<f32>(0.0));
}
