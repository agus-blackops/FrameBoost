//! Texture planes, and moving pixels between them and [`frameboost_core`].
//!
//! Three formats, matching the three things a renderer hands over: `rgba32float`
//! for linear colour, `r32float` for linear view-space depth, `rg32float` for
//! motion in pixels. All 32-bit because these are reference implementations
//! whose output is compared against the CPU oracle, and a 16-bit intermediate
//! would put the difference between a correct port and a broken one below the
//! noise floor of the comparison. Production would use packed formats and
//! measure the error that costs.

use frameboost_core::frame::{DepthBuffer, GBuffer, Grid, ImageBuffer, MotionBuffer};

use crate::context::GpuContext;

/// Copy alignment required for texture-to-buffer transfers.
const COPY_ALIGN: u32 = wgpu::COPY_BYTES_PER_ROW_ALIGNMENT;

/// One texture plane.
pub struct Plane {
    texture: wgpu::Texture,
    view: wgpu::TextureView,
    width: u32,
    height: u32,
    format: wgpu::TextureFormat,
}

impl Plane {
    fn new(
        ctx: &GpuContext,
        width: u32,
        height: u32,
        format: wgpu::TextureFormat,
        storage: bool,
        label: &str,
    ) -> Self {
        let mut usage = wgpu::TextureUsages::TEXTURE_BINDING
            | wgpu::TextureUsages::COPY_DST
            | wgpu::TextureUsages::COPY_SRC;
        if storage {
            usage |= wgpu::TextureUsages::STORAGE_BINDING;
        }
        let texture = ctx.device().create_texture(&wgpu::TextureDescriptor {
            label: Some(label),
            size: wgpu::Extent3d {
                width,
                height,
                depth_or_array_layers: 1,
            },
            mip_level_count: 1,
            sample_count: 1,
            dimension: wgpu::TextureDimension::D2,
            format,
            usage,
            view_formats: &[],
        });
        let view = texture.create_view(&wgpu::TextureViewDescriptor::default());
        Plane {
            texture,
            view,
            width,
            height,
            format,
        }
    }

    /// A linear RGBA plane.
    pub fn color(ctx: &GpuContext, width: u32, height: u32, storage: bool) -> Self {
        Self::new(
            ctx,
            width,
            height,
            wgpu::TextureFormat::Rgba32Float,
            storage,
            "color",
        )
    }

    /// A single-channel float plane: depth, or a validity mask.
    pub fn scalar(ctx: &GpuContext, width: u32, height: u32, storage: bool) -> Self {
        Self::new(
            ctx,
            width,
            height,
            wgpu::TextureFormat::R32Float,
            storage,
            "scalar",
        )
    }

    /// A two-channel float plane: motion vectors.
    pub fn vec2(ctx: &GpuContext, width: u32, height: u32, storage: bool) -> Self {
        Self::new(
            ctx,
            width,
            height,
            wgpu::TextureFormat::Rg32Float,
            storage,
            "vec2",
        )
    }

    /// The bindable view.
    pub fn view(&self) -> &wgpu::TextureView {
        &self.view
    }

    /// Width in pixels.
    pub fn width(&self) -> u32 {
        self.width
    }

    /// Height in pixels.
    pub fn height(&self) -> u32 {
        self.height
    }

    fn components(&self) -> u32 {
        match self.format {
            wgpu::TextureFormat::Rgba32Float => 4,
            wgpu::TextureFormat::Rg32Float => 2,
            _ => 1,
        }
    }

    fn write(&self, ctx: &GpuContext, data: &[f32]) {
        let bytes: &[u8] = bytemuck::cast_slice(data);
        ctx.queue().write_texture(
            wgpu::TexelCopyTextureInfo {
                texture: &self.texture,
                mip_level: 0,
                origin: wgpu::Origin3d::ZERO,
                aspect: wgpu::TextureAspect::All,
            },
            bytes,
            wgpu::TexelCopyBufferLayout {
                offset: 0,
                bytes_per_row: Some(self.width * self.components() * 4),
                rows_per_image: Some(self.height),
            },
            wgpu::Extent3d {
                width: self.width,
                height: self.height,
                depth_or_array_layers: 1,
            },
        );
    }

    /// Upload linear RGBA.
    pub fn upload_color(&self, ctx: &GpuContext, img: &ImageBuffer) {
        assert_eq!((img.width(), img.height()), (self.width, self.height));
        let flat: Vec<f32> = img.as_slice().iter().flat_map(|c| *c).collect();
        self.write(ctx, &flat);
    }

    /// Upload a scalar plane.
    pub fn upload_scalar(&self, ctx: &GpuContext, g: &Grid<f32>) {
        assert_eq!((g.width(), g.height()), (self.width, self.height));
        self.write(ctx, g.as_slice());
    }

    /// Upload motion vectors.
    pub fn upload_vec2(&self, ctx: &GpuContext, g: &MotionBuffer) {
        assert_eq!((g.width(), g.height()), (self.width, self.height));
        let flat: Vec<f32> = g.as_slice().iter().flat_map(|c| *c).collect();
        self.write(ctx, &flat);
    }

    /// Copy another plane's contents into this one.
    ///
    /// # Panics
    /// If the two planes differ in size or format.
    pub fn copy_from(&self, ctx: &GpuContext, src: &Plane) {
        assert_eq!((src.width, src.height), (self.width, self.height));
        assert_eq!(src.format, self.format);
        let mut encoder = ctx
            .device()
            .create_command_encoder(&wgpu::CommandEncoderDescriptor {
                label: Some("plane copy"),
            });
        encoder.copy_texture_to_texture(
            wgpu::TexelCopyTextureInfo {
                texture: &src.texture,
                mip_level: 0,
                origin: wgpu::Origin3d::ZERO,
                aspect: wgpu::TextureAspect::All,
            },
            wgpu::TexelCopyTextureInfo {
                texture: &self.texture,
                mip_level: 0,
                origin: wgpu::Origin3d::ZERO,
                aspect: wgpu::TextureAspect::All,
            },
            wgpu::Extent3d {
                width: self.width,
                height: self.height,
                depth_or_array_layers: 1,
            },
        );
        ctx.run(encoder);
    }

    /// Read the whole plane back as floats, row-major, tightly packed.
    ///
    /// Copies stride the destination to [`wgpu::COPY_BYTES_PER_ROW_ALIGNMENT`],
    /// so the padding has to be stripped on the way out. Forgetting to is the
    /// classic readback bug: it looks correct at widths that happen to be
    /// aligned and shears the image at every other width.
    pub fn read(&self, ctx: &GpuContext) -> Vec<f32> {
        let comps = self.components();
        let unpadded = self.width * comps * 4;
        let padded = unpadded.div_ceil(COPY_ALIGN) * COPY_ALIGN;

        let staging = ctx.device().create_buffer(&wgpu::BufferDescriptor {
            label: Some("plane readback"),
            size: (padded * self.height) as u64,
            usage: wgpu::BufferUsages::COPY_DST | wgpu::BufferUsages::MAP_READ,
            mapped_at_creation: false,
        });

        let mut encoder = ctx
            .device()
            .create_command_encoder(&wgpu::CommandEncoderDescriptor {
                label: Some("readback"),
            });
        encoder.copy_texture_to_buffer(
            wgpu::TexelCopyTextureInfo {
                texture: &self.texture,
                mip_level: 0,
                origin: wgpu::Origin3d::ZERO,
                aspect: wgpu::TextureAspect::All,
            },
            wgpu::TexelCopyBufferInfo {
                buffer: &staging,
                layout: wgpu::TexelCopyBufferLayout {
                    offset: 0,
                    bytes_per_row: Some(padded),
                    rows_per_image: Some(self.height),
                },
            },
            wgpu::Extent3d {
                width: self.width,
                height: self.height,
                depth_or_array_layers: 1,
            },
        );
        ctx.run(encoder);

        let slice = staging.slice(..);
        slice.map_async(wgpu::MapMode::Read, |_| {});
        let _ = ctx.device().poll(wgpu::PollType::Wait);

        let mapped = slice.get_mapped_range();
        let mut out = Vec::with_capacity((self.width * self.height * comps) as usize);
        for row in 0..self.height {
            let start = (row * padded) as usize;
            let end = start + unpadded as usize;
            out.extend_from_slice(bytemuck::cast_slice::<u8, f32>(&mapped[start..end]));
        }
        drop(mapped);
        staging.unmap();
        out
    }

    /// Read back as an [`ImageBuffer`].
    pub fn read_color(&self, ctx: &GpuContext) -> ImageBuffer {
        let flat = self.read(ctx);
        let px: Vec<[f32; 4]> = flat.chunks_exact(4).map(|c| [c[0], c[1], c[2], c[3]]).collect();
        ImageBuffer::from_vec(self.width, self.height, px)
    }

    /// Read back as a scalar grid.
    pub fn read_scalar(&self, ctx: &GpuContext) -> Grid<f32> {
        Grid::from_vec(self.width, self.height, self.read(ctx))
    }
}

/// The three planes a renderer hands over, on the GPU.
pub struct GpuGBuffer {
    /// Linear RGBA.
    pub color: Plane,
    /// Linear view-space depth.
    pub depth: Plane,
    /// Offsets to the previous frame, in pixels.
    pub motion: Plane,
}

impl GpuGBuffer {
    /// Allocate.
    pub fn new(ctx: &GpuContext, width: u32, height: u32) -> Self {
        GpuGBuffer {
            color: Plane::color(ctx, width, height, false),
            depth: Plane::scalar(ctx, width, height, false),
            motion: Plane::vec2(ctx, width, height, false),
        }
    }

    /// Allocate and upload a CPU G-buffer.
    pub fn from_cpu(ctx: &GpuContext, g: &GBuffer) -> Self {
        let gpu = Self::new(ctx, g.width(), g.height());
        gpu.upload(ctx, g);
        gpu
    }

    /// Upload into existing planes.
    pub fn upload(&self, ctx: &GpuContext, g: &GBuffer) {
        self.color.upload_color(ctx, &g.color);
        self.depth.upload_scalar(ctx, depth_as_grid(&g.depth));
        self.motion.upload_vec2(ctx, &g.motion);
    }

    /// Width in pixels.
    pub fn width(&self) -> u32 {
        self.color.width()
    }

    /// Height in pixels.
    pub fn height(&self) -> u32 {
        self.color.height()
    }
}

fn depth_as_grid(d: &DepthBuffer) -> &Grid<f32> {
    d
}
