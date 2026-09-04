//! The compute pipelines.
//!
//! Every shader here computes what the corresponding function in
//! [`frameboost_core::recon`] computes. That is not a coincidence to be
//! maintained by good intentions: the tests run both over the same inputs and
//! compare, so a divergence fails the build rather than shipping as a subtle
//! difference in how the game looks on one code path.

use bytemuck::{Pod, Zeroable};
use frameboost_core::recon::{EasuConfig, InterpolateConfig, ReprojectConfig};
use frameboost_core::Signals;
use wgpu::util::DeviceExt;

use crate::context::GpuContext;
use crate::plane::{GpuGBuffer, Plane};

const WORKGROUP: u32 = 8;

const COMMON: &str = include_str!("../shaders/common.wgsl");

/// What a binding slot holds.
#[derive(Clone, Copy)]
enum Slot {
    /// A sampled texture, read with `textureLoad`.
    Tex,
    /// A write-only `rgba32float` storage texture.
    StoreColor,
    /// A write-only `r32float` storage texture.
    StoreScalar,
    /// A read-write storage buffer.
    Storage,
    /// A uniform buffer.
    Uniform,
}

/// A compute pipeline and its layout.
struct Pass {
    pipeline: wgpu::ComputePipeline,
    layout: wgpu::BindGroupLayout,
}

impl Pass {
    fn new(ctx: &GpuContext, label: &str, body: &str, slots: &[Slot]) -> Self {
        let entries: Vec<wgpu::BindGroupLayoutEntry> = slots
            .iter()
            .enumerate()
            .map(|(i, slot)| wgpu::BindGroupLayoutEntry {
                binding: i as u32,
                visibility: wgpu::ShaderStages::COMPUTE,
                ty: match slot {
                    // `filterable: false` because 32-bit float formats are not
                    // filterable without an optional feature — and none of these
                    // shaders sample, they all `textureLoad`, so asking for
                    // filtering would cost portability to buy nothing.
                    Slot::Tex => wgpu::BindingType::Texture {
                        sample_type: wgpu::TextureSampleType::Float { filterable: false },
                        view_dimension: wgpu::TextureViewDimension::D2,
                        multisampled: false,
                    },
                    Slot::StoreColor => wgpu::BindingType::StorageTexture {
                        access: wgpu::StorageTextureAccess::WriteOnly,
                        format: wgpu::TextureFormat::Rgba32Float,
                        view_dimension: wgpu::TextureViewDimension::D2,
                    },
                    Slot::StoreScalar => wgpu::BindingType::StorageTexture {
                        access: wgpu::StorageTextureAccess::WriteOnly,
                        format: wgpu::TextureFormat::R32Float,
                        view_dimension: wgpu::TextureViewDimension::D2,
                    },
                    Slot::Storage => wgpu::BindingType::Buffer {
                        ty: wgpu::BufferBindingType::Storage { read_only: false },
                        has_dynamic_offset: false,
                        min_binding_size: None,
                    },
                    Slot::Uniform => wgpu::BindingType::Buffer {
                        ty: wgpu::BufferBindingType::Uniform,
                        has_dynamic_offset: false,
                        min_binding_size: None,
                    },
                },
                count: None,
            })
            .collect();

        let layout = ctx
            .device()
            .create_bind_group_layout(&wgpu::BindGroupLayoutDescriptor {
                label: Some(label),
                entries: &entries,
            });

        let module = ctx
            .device()
            .create_shader_module(wgpu::ShaderModuleDescriptor {
                label: Some(label),
                source: wgpu::ShaderSource::Wgsl(format!("{COMMON}\n{body}").into()),
            });

        let pipeline_layout =
            ctx.device()
                .create_pipeline_layout(&wgpu::PipelineLayoutDescriptor {
                    label: Some(label),
                    bind_group_layouts: &[&layout],
                    push_constant_ranges: &[],
                });

        let pipeline = ctx
            .device()
            .create_compute_pipeline(&wgpu::ComputePipelineDescriptor {
                label: Some(label),
                layout: Some(&pipeline_layout),
                module: &module,
                entry_point: Some("main"),
                compilation_options: Default::default(),
                cache: None,
            });

        Pass { pipeline, layout }
    }

    fn dispatch(
        &self,
        ctx: &GpuContext,
        entries: &[wgpu::BindGroupEntry],
        width: u32,
        height: u32,
    ) {
        let bind_group = ctx.device().create_bind_group(&wgpu::BindGroupDescriptor {
            label: None,
            layout: &self.layout,
            entries,
        });
        let mut encoder = ctx
            .device()
            .create_command_encoder(&wgpu::CommandEncoderDescriptor { label: None });
        {
            let mut cpass = encoder.begin_compute_pass(&wgpu::ComputePassDescriptor {
                label: None,
                timestamp_writes: None,
            });
            cpass.set_pipeline(&self.pipeline);
            cpass.set_bind_group(0, &bind_group, &[]);
            cpass.dispatch_workgroups(
                width.div_ceil(WORKGROUP),
                height.div_ceil(WORKGROUP),
                1,
            );
        }
        ctx.run(encoder);
    }
}

#[repr(C)]
#[derive(Copy, Clone, Pod, Zeroable)]
struct EasuParams {
    src_w: u32,
    src_h: u32,
    dst_w: u32,
    dst_h: u32,
    anisotropy: f32,
    deringing: u32,
    _p0: u32,
    _p1: u32,
}

#[repr(C)]
#[derive(Copy, Clone, Pod, Zeroable)]
struct RcasParams {
    width: u32,
    height: u32,
    sharpness: f32,
    _p: u32,
}

#[repr(C)]
#[derive(Copy, Clone, Pod, Zeroable)]
struct ReprojectParams {
    width: u32,
    height: u32,
    depth_tolerance: f32,
    _p: u32,
}

#[repr(C)]
#[derive(Copy, Clone, Pod, Zeroable)]
struct InterpolateParams {
    width: u32,
    height: u32,
    t: f32,
    depth_tolerance: f32,
    photo_tolerance: f32,
    _p0: u32,
    _p1: u32,
    _p2: u32,
}

#[repr(C)]
#[derive(Copy, Clone, Pod, Zeroable)]
struct SizeParams {
    width: u32,
    height: u32,
    _p0: u32,
    _p1: u32,
}

#[repr(C)]
#[derive(Copy, Clone, Pod, Zeroable)]
struct MaskParams {
    width: u32,
    height: u32,
    threshold: f32,
    _p: u32,
}

/// Somewhere to put a temporal reconstruction, plus the scratch its hole
/// filling ping-pongs through.
pub struct ReconTarget {
    color: Plane,
    validity: Plane,
    scratch_color: Plane,
    scratch_validity: Plane,
}

impl ReconTarget {
    /// Allocate for a given size.
    pub fn new(ctx: &GpuContext, width: u32, height: u32) -> Self {
        ReconTarget {
            color: Plane::color(ctx, width, height, true),
            validity: Plane::scalar(ctx, width, height, true),
            scratch_color: Plane::color(ctx, width, height, true),
            scratch_validity: Plane::scalar(ctx, width, height, true),
        }
    }

    /// The reconstructed frame.
    pub fn color(&self) -> &Plane {
        &self.color
    }

    /// Per-pixel confidence, after hole filling.
    pub fn validity(&self) -> &Plane {
        &self.validity
    }
}

/// The reconstruction passes.
pub struct Reconstructor {
    easu: Pass,
    rcas: Pass,
    reproject: Pass,
    interpolate: Pass,
    hole_fill: Pass,
    signals: Pass,
    reduce_mask: Pass,
}

impl Reconstructor {
    /// Compile every pipeline.
    pub fn new(ctx: &GpuContext) -> Self {
        use Slot::*;
        Reconstructor {
            easu: Pass::new(
                ctx,
                "easu",
                include_str!("../shaders/easu.wgsl"),
                &[Tex, StoreColor, Uniform],
            ),
            rcas: Pass::new(
                ctx,
                "rcas",
                include_str!("../shaders/rcas.wgsl"),
                &[Tex, StoreColor, Uniform],
            ),
            reproject: Pass::new(
                ctx,
                "reproject",
                include_str!("../shaders/reproject.wgsl"),
                &[Tex, Tex, Tex, Tex, Tex, StoreColor, StoreScalar, Uniform],
            ),
            interpolate: Pass::new(
                ctx,
                "interpolate",
                include_str!("../shaders/interpolate.wgsl"),
                &[Tex, Tex, Tex, Tex, Tex, StoreColor, StoreScalar, Uniform],
            ),
            hole_fill: Pass::new(
                ctx,
                "hole_fill",
                include_str!("../shaders/hole_fill.wgsl"),
                &[Tex, Tex, StoreColor, StoreScalar, Uniform],
            ),
            signals: Pass::new(
                ctx,
                "signals",
                include_str!("../shaders/signals.wgsl"),
                &[Tex, Tex, Tex, Tex, Storage, Uniform],
            ),
            reduce_mask: Pass::new(
                ctx,
                "reduce_mask",
                include_str!("../shaders/reduce_mask.wgsl"),
                &[Tex, Storage, Uniform],
            ),
        }
    }

    /// Edge-adaptive upsample `src` into `dst`.
    pub fn easu(&self, ctx: &GpuContext, src: &Plane, dst: &Plane, cfg: EasuConfig) {
        let params = uniform(
            ctx,
            &EasuParams {
                src_w: src.width(),
                src_h: src.height(),
                dst_w: dst.width(),
                dst_h: dst.height(),
                anisotropy: cfg.anisotropy_strength,
                deringing: u32::from(cfg.deringing),
                _p0: 0,
                _p1: 0,
            },
        );
        self.easu.dispatch(
            ctx,
            &[
                entry(0, src.view()),
                entry(1, dst.view()),
                buf(2, &params),
            ],
            dst.width(),
            dst.height(),
        );
    }

    /// Contrast-limited sharpen.
    pub fn rcas(&self, ctx: &GpuContext, src: &Plane, dst: &Plane, sharpness: f32) {
        let params = uniform(
            ctx,
            &RcasParams {
                width: src.width(),
                height: src.height(),
                sharpness,
                _p: 0,
            },
        );
        self.rcas.dispatch(
            ctx,
            &[
                entry(0, src.view()),
                entry(1, dst.view()),
                buf(2, &params),
            ],
            src.width(),
            src.height(),
        );
    }

    /// Warp `prev` into `cur`'s pixel grid. Returns the disocclusion fraction.
    pub fn reproject(
        &self,
        ctx: &GpuContext,
        prev: &GpuGBuffer,
        cur: &GpuGBuffer,
        out: &ReconTarget,
        cfg: ReprojectConfig,
    ) -> f32 {
        let (w, h) = (cur.width(), cur.height());
        let params = uniform(
            ctx,
            &ReprojectParams {
                width: w,
                height: h,
                depth_tolerance: cfg.depth_tolerance,
                _p: 0,
            },
        );
        self.reproject.dispatch(
            ctx,
            &[
                entry(0, prev.color.view()),
                entry(1, prev.depth.view()),
                entry(2, cur.color.view()),
                entry(3, cur.depth.view()),
                entry(4, cur.motion.view()),
                entry(5, out.color.view()),
                entry(6, out.validity.view()),
                buf(7, &params),
            ],
            w,
            h,
        );
        self.mask_fraction(ctx, &out.validity)
    }

    /// Generate the frame at `t` between `prev` and `next`.
    ///
    /// Returns the disocclusion fraction, measured *before* hole filling — a
    /// filled hole still shows content no rendered frame contained.
    pub fn interpolate(
        &self,
        ctx: &GpuContext,
        prev: &GpuGBuffer,
        next: &GpuGBuffer,
        t: f32,
        out: &ReconTarget,
        cfg: InterpolateConfig,
    ) -> f32 {
        let (w, h) = (next.width(), next.height());
        let params = uniform(
            ctx,
            &InterpolateParams {
                width: w,
                height: h,
                t,
                depth_tolerance: cfg.depth_tolerance,
                photo_tolerance: cfg.photo_tolerance,
                _p0: 0,
                _p1: 0,
                _p2: 0,
            },
        );
        self.interpolate.dispatch(
            ctx,
            &[
                entry(0, prev.color.view()),
                entry(1, prev.depth.view()),
                entry(2, next.color.view()),
                entry(3, next.depth.view()),
                entry(4, next.motion.view()),
                entry(5, out.color.view()),
                entry(6, out.validity.view()),
                buf(7, &params),
            ],
            w,
            h,
        );

        let disocclusion = self.mask_fraction(ctx, &out.validity);
        self.fill_holes(ctx, out, cfg.hole_fill_passes);
        disocclusion
    }

    /// Grow the believable region outward, one pixel per pass.
    fn fill_holes(&self, ctx: &GpuContext, out: &ReconTarget, passes: u32) {
        if passes == 0 {
            return;
        }
        let (w, h) = (out.color.width(), out.color.height());
        let params = uniform(
            ctx,
            &SizeParams {
                width: w,
                height: h,
                _p0: 0,
                _p1: 0,
            },
        );

        // Ping-pong rather than fill in place: a pixel filled earlier in a pass
        // must not become a source for one filled later in the same pass, or
        // the fill smears along whatever order the invocations happened to run.
        for i in 0..passes {
            let (src_c, src_v, dst_c, dst_v) = if i % 2 == 0 {
                (
                    &out.color,
                    &out.validity,
                    &out.scratch_color,
                    &out.scratch_validity,
                )
            } else {
                (
                    &out.scratch_color,
                    &out.scratch_validity,
                    &out.color,
                    &out.validity,
                )
            };
            self.hole_fill.dispatch(
                ctx,
                &[
                    entry(0, src_c.view()),
                    entry(1, src_v.view()),
                    entry(2, dst_c.view()),
                    entry(3, dst_v.view()),
                    buf(4, &params),
                ],
                w,
                h,
            );
        }

        // An odd number of passes leaves the answer in scratch. This has to be
        // an actual copy: running one more hole_fill would perform another
        // dilation, so the pass count would silently be one higher than asked
        // for and the GPU path would stop matching the CPU oracle.
        if passes % 2 == 1 {
            out.color.copy_from(ctx, &out.scratch_color);
            out.validity.copy_from(ctx, &out.scratch_validity);
        }
    }

    /// Fraction of a validity mask at or below zero.
    pub fn mask_fraction(&self, ctx: &GpuContext, mask: &Plane) -> f32 {
        let (w, h) = (mask.width(), mask.height());
        let groups = w.div_ceil(WORKGROUP) * h.div_ceil(WORKGROUP);
        let partials = storage(ctx, groups as u64 * 4 * 4);
        let params = uniform(
            ctx,
            &MaskParams {
                width: w,
                height: h,
                threshold: 0.0,
                _p: 0,
            },
        );
        self.reduce_mask.dispatch(
            ctx,
            &[entry(0, mask.view()), buf(1, &partials), buf(2, &params)],
            w,
            h,
        );
        let sums = read_buffer(ctx, &partials, groups as usize * 4);
        let invalid: f32 = sums.chunks_exact(4).map(|c| c[0]).sum();
        invalid / (w * h) as f32
    }

    /// Measure the rung-independent signals from a frame pair.
    ///
    /// Runs on the GPU because reading a full-resolution frame back every frame
    /// to compute five numbers would cost more than the boost saves, and would
    /// stall the pipeline doing it. Only a few floats per workgroup come back.
    ///
    /// Leaves [`Signals::disocclusion`] and
    /// [`Signals::pacing_instability`](frameboost_core::Signals::pacing_instability)
    /// at zero: the first comes from [`Reconstructor::interpolate`], the second
    /// from the pacer.
    pub fn measure_pair(
        &self,
        ctx: &GpuContext,
        prev: &GpuGBuffer,
        next: &GpuGBuffer,
        reference_speed: f32,
    ) -> Signals {
        let (w, h) = (next.width(), next.height());
        let groups = w.div_ceil(WORKGROUP) * h.div_ceil(WORKGROUP);
        let partials = storage(ctx, groups as u64 * 8 * 4);
        let params = uniform(
            ctx,
            &SizeParams {
                width: w,
                height: h,
                _p0: 0,
                _p1: 0,
            },
        );
        self.signals.dispatch(
            ctx,
            &[
                entry(0, prev.color.view()),
                entry(1, next.color.view()),
                entry(2, next.depth.view()),
                entry(3, next.motion.view()),
                buf(4, &partials),
                buf(5, &params),
            ],
            w,
            h,
        );

        let sums = read_buffer(ctx, &partials, groups as usize * 8);
        let mut total = [0.0f64; 5];
        for block in sums.chunks_exact(8) {
            for (t, v) in total.iter_mut().zip(block.iter()) {
                *t += *v as f64;
            }
        }

        let n = (w * h) as f64;
        let mean_residual = (total[0] / n) as f32;
        let mean_speed = (total[1] / n) as f32;
        let luma_delta = ((total[3] - total[2]).abs() / n) as f32;
        let discontinuities = (total[4] / n) as f32;

        // Same anchoring as the CPU path; see `frameboost_core::signals`.
        Signals {
            disocclusion: 0.0,
            motion_residual: (mean_residual * 4.0).clamp(0.0, 1.0),
            luma_shift: (luma_delta * 4.0).clamp(0.0, 1.0),
            camera_motion: (mean_speed / reference_speed.max(1e-3)).clamp(0.0, 1.0),
            depth_complexity: discontinuities.clamp(0.0, 1.0),
            pacing_instability: 0.0,
        }
    }
}

fn entry<'a>(binding: u32, view: &'a wgpu::TextureView) -> wgpu::BindGroupEntry<'a> {
    wgpu::BindGroupEntry {
        binding,
        resource: wgpu::BindingResource::TextureView(view),
    }
}

fn buf(binding: u32, buffer: &wgpu::Buffer) -> wgpu::BindGroupEntry<'_> {
    wgpu::BindGroupEntry {
        binding,
        resource: buffer.as_entire_binding(),
    }
}

fn uniform<T: Pod>(ctx: &GpuContext, value: &T) -> wgpu::Buffer {
    ctx.device()
        .create_buffer_init(&wgpu::util::BufferInitDescriptor {
            label: Some("params"),
            contents: bytemuck::bytes_of(value),
            usage: wgpu::BufferUsages::UNIFORM,
        })
}

fn storage(ctx: &GpuContext, size: u64) -> wgpu::Buffer {
    ctx.device().create_buffer(&wgpu::BufferDescriptor {
        label: Some("partials"),
        size: size.max(4),
        usage: wgpu::BufferUsages::STORAGE | wgpu::BufferUsages::COPY_SRC,
        mapped_at_creation: false,
    })
}

fn read_buffer(ctx: &GpuContext, buffer: &wgpu::Buffer, floats: usize) -> Vec<f32> {
    let size = (floats * 4) as u64;
    let staging = ctx.device().create_buffer(&wgpu::BufferDescriptor {
        label: Some("partials readback"),
        size,
        usage: wgpu::BufferUsages::COPY_DST | wgpu::BufferUsages::MAP_READ,
        mapped_at_creation: false,
    });
    let mut encoder = ctx
        .device()
        .create_command_encoder(&wgpu::CommandEncoderDescriptor { label: None });
    encoder.copy_buffer_to_buffer(buffer, 0, &staging, 0, size);
    ctx.run(encoder);

    let slice = staging.slice(..);
    slice.map_async(wgpu::MapMode::Read, |_| {});
    let _ = ctx.device().poll(wgpu::PollType::Wait);
    let mapped = slice.get_mapped_range();
    let out = bytemuck::cast_slice::<u8, f32>(&mapped).to_vec();
    drop(mapped);
    staging.unmap();
    out
}
