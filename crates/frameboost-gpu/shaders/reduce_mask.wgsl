// Count how much of a validity mask is unusable.
//
// The disocclusion figure the controller reacts to. Counted before any hole
// filling, deliberately: a filled hole still shows content no rendered frame
// contained, and reporting the post-fill number would tell the controller
// everything is fine right up until a tester says otherwise.

struct Params {
    width: u32,
    height: u32,
    threshold: f32,
    _pad: u32,
};

@group(0) @binding(0) var mask: texture_2d<f32>;
@group(0) @binding(1) var<storage, read_write> partials: array<f32>;
@group(0) @binding(2) var<uniform> params: Params;

const SLOTS: u32 = 4u;
const LANES: u32 = 64u;

var<workgroup> acc: array<f32, 64>;

@compute @workgroup_size(8, 8, 1)
fn main(
    @builtin(global_invocation_id) gid: vec3<u32>,
    @builtin(local_invocation_id) lid: vec3<u32>,
    @builtin(workgroup_id) wid: vec3<u32>,
    @builtin(num_workgroups) num_wg: vec3<u32>,
) {
    let lane = lid.x + lid.y * 8u;

    var invalid = 0.0;
    if (gid.x < params.width && gid.y < params.height) {
        let v = textureLoad(mask, vec2<i32>(i32(gid.x), i32(gid.y)), 0).r;
        if (v <= params.threshold) {
            invalid = 1.0;
        }
    }
    acc[lane] = invalid;
    workgroupBarrier();

    for (var stride = LANES / 2u; stride > 0u; stride = stride / 2u) {
        if (lane < stride) {
            acc[lane] = acc[lane] + acc[lane + stride];
        }
        workgroupBarrier();
    }

    if (lane == 0u) {
        partials[(wid.x + wid.y * num_wg.x) * SLOTS] = acc[0];
    }
}
