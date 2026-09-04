//! The GPU path: WGSL implementations of the FrameBoost reconstruction passes,
//! wired up with wgpu.
//!
//! This is the half that ships. [`frameboost_core::recon`] holds scalar
//! implementations of the same passes, written to be read; these are what runs
//! inside a frame.
//!
//! Keeping both is not duplication for its own sake. Shader bugs are otherwise
//! diagnosed by staring at a screenshot and arguing about whether the smear
//! looks wrong, and "the disocclusion mask is inverted along one edge" is not a
//! thing anyone finds that way. With an oracle, the test says which line.
//!
//! The tests in `tests/parity.rs` run both implementations over identical
//! inputs and compare pixel by pixel. They need a working adapter and skip
//! themselves without one, but a software rasteriser is enough: these are
//! ordinary compute shaders, and llvmpipe runs them correctly, just slowly.
//!
//! # Usage
//!
//! ```no_run
//! use frameboost_gpu::{GpuContext, GpuGBuffer, ReconTarget, Reconstructor};
//! use frameboost_core::recon::InterpolateConfig;
//!
//! let ctx = GpuContext::new()?;
//! let recon = Reconstructor::new(&ctx);
//!
//! # let (prev_cpu, next_cpu) = (frameboost_core::GBuffer::blank(64, 64), frameboost_core::GBuffer::blank(64, 64));
//! let prev = GpuGBuffer::from_cpu(&ctx, &prev_cpu);
//! let next = GpuGBuffer::from_cpu(&ctx, &next_cpu);
//! let target = ReconTarget::new(&ctx, next.width(), next.height());
//!
//! // The generated frame, and the number the controller actually reacts to.
//! let disocclusion = recon.interpolate(
//!     &ctx, &prev, &next, 0.5, &target, InterpolateConfig::default(),
//! );
//!
//! let mut signals = recon.measure_pair(&ctx, &prev, &next, next.width() as f32 * 0.10);
//! signals.disocclusion = disocclusion;
//! // …then hand `signals` to `BoostController::resolve`.
//! # Ok::<(), frameboost_gpu::GpuError>(())
//! ```

#![forbid(unsafe_code)]
#![warn(missing_docs)]

pub mod context;
pub mod passes;
pub mod plane;

pub use context::{GpuContext, GpuError};
pub use passes::{ReconTarget, Reconstructor};
pub use plane::{GpuGBuffer, Plane};
