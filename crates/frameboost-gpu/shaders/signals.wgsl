// Per-frame-pair statistics, reduced on the GPU.
//
// Mirrors `frameboost_core::signals::measure_pair`. It runs here rather than on
// the CPU for one reason: reading a full-resolution frame back every frame to
// compute five numbers would cost more than everything the boost saves, and
// would stall the pipeline doing it. What comes back is a handful of floats per
// workgroup, which the host finishes summing.
//
// A tree reduction in workgroup memory. Out-of-range lanes contribute zero but
// still reach every barrier — leaving early from a subset of a workgroup is
// undefined behaviour, and the kind that works until it does not.

struct Params {
    width: u32,
    height: u32,
    _pad0: u32,
    _pad1: u32,
};

@group(0) @binding(0) var prev_color: texture_2d<f32>;
@group(0) @binding(1) var next_color: texture_2d<f32>;
@group(0) @binding(2) var next_depth: texture_2d<f32>;
@group(0) @binding(3) var next_motion: texture_2d<f32>;
@group(0) @binding(4) var<storage, read_write> partials: array<f32>;
@group(0) @binding(5) var<uniform> params: Params;

const METRICS: u32 = 5u;
const SLOTS: u32 = 8u;      // padded so each workgroup's block is 32 bytes
const LANES: u32 = 64u;

var<workgroup> acc: array<array<f32, 5>, 64>;

@compute @workgroup_size(8, 8, 1)
fn main(
    @builtin(global_invocation_id) gid: vec3<u32>,
    @builtin(local_invocation_id) lid: vec3<u32>,
    @builtin(workgroup_id) wid: vec3<u32>,
    @builtin(num_workgroups) num_wg: vec3<u32>,
) {
    let lane = lid.x + lid.y * 8u;
    let size = vec2<i32>(i32(params.width), i32(params.height));

    var residual = 0.0;
    var speed = 0.0;
    var luma_prev = 0.0;
    var luma_next = 0.0;
    var discontinuity = 0.0;

    if (gid.x < params.width && gid.y < params.height) {
        let p = vec2<i32>(i32(gid.x), i32(gid.y));

        let here = textureLoad(next_color, p, 0);
        let l_next = fb_luma(here);
        luma_next = l_next;
        luma_prev = fb_luma(textureLoad(prev_color, p, 0));

        let mv = textureLoad(next_motion, p, 0).xy;
        speed = length(mv);

        let src = vec2<f32>(p) + vec2<f32>(0.5, 0.5) + mv;
        let l_there = fb_luma(fb_bilinear4(prev_color, size, src));
        // Normalised by local magnitude, so bright regions do not dominate the
        // average purely for being bright.
        let denom = max(abs(l_next) + abs(l_there), 1e-3);
        residual = abs(l_next - l_there) / denom;

        let d = textureLoad(next_depth, p, 0).r;
        let dx = textureLoad(next_depth, fb_clamp_coord(p + vec2<i32>(1, 0), size), 0).r;
        let dy = textureLoad(next_depth, fb_clamp_coord(p + vec2<i32>(0, 1), size), 0).r;
        if (fb_relative_depth_delta(d, dx) > 0.10 || fb_relative_depth_delta(d, dy) > 0.10) {
            discontinuity = 1.0;
        }
    }

    acc[lane][0] = residual;
    acc[lane][1] = speed;
    acc[lane][2] = luma_prev;
    acc[lane][3] = luma_next;
    acc[lane][4] = discontinuity;
    workgroupBarrier();

    for (var stride = LANES / 2u; stride > 0u; stride = stride / 2u) {
        if (lane < stride) {
            for (var m = 0u; m < METRICS; m = m + 1u) {
                acc[lane][m] = acc[lane][m] + acc[lane + stride][m];
            }
        }
        workgroupBarrier();
    }

    if (lane == 0u) {
        let block = (wid.x + wid.y * num_wg.x) * SLOTS;
        for (var m = 0u; m < METRICS; m = m + 1u) {
            partials[block + m] = acc[0][m];
        }
    }
}
