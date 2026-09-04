// One dilation pass over rejected pixels.
//
// Each pass grows the believable region by a pixel, so the host's pass count is
// the widest hole that gets filled rather than held. Small holes are worth
// filling; a wide one is a disocclusion the controller should be reacting to
// instead, and papering over it would only hide that.
//
// Ping-ponged by the host rather than done in place: a pixel filled earlier in
// this pass must not be a source for one filled later in the same pass, or the
// fill smears directionally along whatever order the invocations happened to
// run in.

struct Params {
    width: u32,
    height: u32,
    _pad0: u32,
    _pad1: u32,
};

@group(0) @binding(0) var in_color: texture_2d<f32>;
@group(0) @binding(1) var in_validity: texture_2d<f32>;
@group(0) @binding(2) var out_color: texture_storage_2d<rgba32float, write>;
@group(0) @binding(3) var out_validity: texture_storage_2d<r32float, write>;
@group(0) @binding(4) var<uniform> params: Params;

@compute @workgroup_size(8, 8, 1)
fn main(@builtin(global_invocation_id) gid: vec3<u32>) {
    if (gid.x >= params.width || gid.y >= params.height) {
        return;
    }
    let size = vec2<i32>(i32(params.width), i32(params.height));
    let p = vec2<i32>(i32(gid.x), i32(gid.y));

    let v = textureLoad(in_validity, p, 0).r;
    let c = textureLoad(in_color, p, 0);
    if (v > 0.0) {
        textureStore(out_color, p, c);
        textureStore(out_validity, p, vec4<f32>(v, 0.0, 0.0, 0.0));
        return;
    }

    var acc = vec4<f32>(0.0);
    var weight = 0.0;
    for (var dy = -1; dy <= 1; dy++) {
        for (var dx = -1; dx <= 1; dx++) {
            if (dx == 0 && dy == 0) {
                continue;
            }
            let n = p + vec2<i32>(dx, dy);
            if (n.x < 0 || n.y < 0 || n.x >= size.x || n.y >= size.y) {
                continue;
            }
            let nv = textureLoad(in_validity, n, 0).r;
            if (nv <= 0.0) {
                continue;
            }
            acc += textureLoad(in_color, n, 0) * nv;
            weight += nv;
        }
    }

    if (weight > 0.0) {
        // Just under 1: filled pixels are usable sources for the next pass but
        // must not outweigh genuine samples.
        textureStore(out_color, p, acc / weight);
        textureStore(out_validity, p, vec4<f32>(0.5, 0.0, 0.0, 0.0));
    } else {
        textureStore(out_color, p, c);
        textureStore(out_validity, p, vec4<f32>(0.0));
    }
}
