//! Device acquisition.

use std::fmt;

/// Why a GPU context could not be created.
#[derive(Debug)]
pub enum GpuError {
    /// No adapter at all. Headless CI without a software rasteriser, usually.
    NoAdapter,
    /// An adapter exists but would not hand over a device.
    Device(wgpu::RequestDeviceError),
}

impl fmt::Display for GpuError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            GpuError::NoAdapter => write!(f, "no wgpu adapter available"),
            GpuError::Device(e) => write!(f, "could not create device: {e}"),
        }
    }
}

impl std::error::Error for GpuError {}

/// A device and queue, plus what they turned out to be.
pub struct GpuContext {
    device: wgpu::Device,
    queue: wgpu::Queue,
    info: wgpu::AdapterInfo,
}

impl GpuContext {
    /// Acquire a device.
    ///
    /// Takes whatever adapter is offered, software rasterisers included — the
    /// reconstruction passes are ordinary compute and a software adapter runs
    /// them correctly, just slowly. That is what makes it possible to test the
    /// shaders against the CPU reference in CI, which is the difference between
    /// a shader that is believed to work and one that is known to.
    pub fn new() -> Result<Self, GpuError> {
        let instance = wgpu::Instance::new(&wgpu::InstanceDescriptor::default());
        let adapter = pollster::block_on(instance.request_adapter(&wgpu::RequestAdapterOptions {
            power_preference: wgpu::PowerPreference::HighPerformance,
            force_fallback_adapter: false,
            compatible_surface: None,
        }))
        .map_err(|_| GpuError::NoAdapter)?;

        let info = adapter.get_info();
        let (device, queue) = pollster::block_on(adapter.request_device(&wgpu::DeviceDescriptor {
            label: Some("frameboost"),
            required_features: wgpu::Features::empty(),
            required_limits: wgpu::Limits::downlevel_defaults(),
            memory_hints: wgpu::MemoryHints::Performance,
            trace: wgpu::Trace::Off,
        }))
        .map_err(GpuError::Device)?;

        Ok(GpuContext {
            device,
            queue,
            info,
        })
    }

    /// The device.
    pub fn device(&self) -> &wgpu::Device {
        &self.device
    }

    /// The queue.
    pub fn queue(&self) -> &wgpu::Queue {
        &self.queue
    }

    /// What the adapter is.
    pub fn adapter_info(&self) -> &wgpu::AdapterInfo {
        &self.info
    }

    /// Whether this is a CPU rasteriser. Correct, but far too slow to ship on.
    pub fn is_software(&self) -> bool {
        self.info.device_type == wgpu::DeviceType::Cpu
    }

    /// Submit work and block until the GPU has finished it.
    pub fn run(&self, encoder: wgpu::CommandEncoder) {
        self.queue.submit(Some(encoder.finish()));
        let _ = self.device.poll(wgpu::PollType::Wait);
    }
}
