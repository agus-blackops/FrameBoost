//! Image, depth and motion buffers, and the sampling rules the rest of the
//! crate assumes.
//!
//! # Conventions
//!
//! - **Pixel space.** Pixel `(x, y)` has its *center* at `(x + 0.5, y + 0.5)`.
//!   Every continuous coordinate in this crate is in that space. Getting this
//!   wrong shifts reconstruction by half a texel, which reads as a persistent
//!   softness that is maddening to track down.
//! - **Color.** Linear RGBA, not sRGB. Reconstruction blends; blending in a
//!   non-linear space darkens edges.
//! - **Depth.** Linear view-space depth, increasing away from the camera. Not
//!   a post-projection `1/w`, whose precision distribution makes the relative
//!   comparisons in [`crate::recon`] meaningless at distance.
//! - **Motion.** For each pixel in the *current* frame, the offset in pixels to
//!   where that surface was in the *previous* frame. So the previous-frame
//!   position of pixel `p` is `p + mv(p)`. This matches what a renderer
//!   naturally produces from the previous frame's view-projection matrix, and
//!   it is a gather (each destination reads one source) rather than a scatter.
//! - **Edges.** All sampling clamps. Reconstruction near frame edges is already
//!   the worst case; wrapping would make it wrong as well as bad.

/// A value that can be filtered.
pub trait Texel: Copy {
    /// The additive identity.
    fn zero() -> Self;
    /// Multiply by a scalar.
    fn scale(self, k: f32) -> Self;
    /// Add another value.
    fn add(self, other: Self) -> Self;
}

impl Texel for f32 {
    #[inline]
    fn zero() -> Self {
        0.0
    }
    #[inline]
    fn scale(self, k: f32) -> Self {
        self * k
    }
    #[inline]
    fn add(self, other: Self) -> Self {
        self + other
    }
}

impl<const N: usize> Texel for [f32; N] {
    #[inline]
    fn zero() -> Self {
        [0.0; N]
    }
    #[inline]
    fn scale(mut self, k: f32) -> Self {
        for v in self.iter_mut() {
            *v *= k;
        }
        self
    }
    #[inline]
    fn add(mut self, other: Self) -> Self {
        for (a, b) in self.iter_mut().zip(other.iter()) {
            *a += *b;
        }
        self
    }
}

/// A dense 2-D buffer.
#[derive(Clone, Debug, PartialEq)]
pub struct Grid<T> {
    width: u32,
    height: u32,
    data: Vec<T>,
}

impl<T: Texel> Grid<T> {
    /// Allocate a buffer filled with `value`.
    ///
    /// # Panics
    /// If either dimension is zero.
    pub fn new(width: u32, height: u32, value: T) -> Self {
        assert!(width > 0 && height > 0, "grid dimensions must be non-zero");
        Grid {
            width,
            height,
            data: vec![value; (width as usize) * (height as usize)],
        }
    }

    /// Allocate a zeroed buffer.
    pub fn zeroed(width: u32, height: u32) -> Self {
        Self::new(width, height, T::zero())
    }

    /// Wrap existing data.
    ///
    /// # Panics
    /// If `data.len() != width * height`.
    pub fn from_vec(width: u32, height: u32, data: Vec<T>) -> Self {
        assert_eq!(
            data.len(),
            (width as usize) * (height as usize),
            "data length does not match dimensions"
        );
        Grid {
            width,
            height,
            data,
        }
    }

    /// Width in pixels.
    pub fn width(&self) -> u32 {
        self.width
    }

    /// Height in pixels.
    pub fn height(&self) -> u32 {
        self.height
    }

    /// Number of pixels.
    pub fn len(&self) -> usize {
        self.data.len()
    }

    /// Whether the buffer has no pixels. Always false: dimensions are non-zero.
    pub fn is_empty(&self) -> bool {
        self.data.is_empty()
    }

    /// The raw pixels, row-major.
    pub fn as_slice(&self) -> &[T] {
        &self.data
    }

    /// The raw pixels, mutably.
    pub fn as_mut_slice(&mut self) -> &mut [T] {
        &mut self.data
    }

    #[inline]
    fn index(&self, x: u32, y: u32) -> usize {
        (y.min(self.height - 1) as usize) * (self.width as usize) + x.min(self.width - 1) as usize
    }

    /// Read a pixel, clamping out-of-range coordinates to the edge.
    #[inline]
    pub fn at(&self, x: u32, y: u32) -> T {
        self.data[self.index(x, y)]
    }

    /// Read with signed coordinates, clamping to the edge.
    #[inline]
    pub fn at_clamped(&self, x: i32, y: i32) -> T {
        let cx = x.clamp(0, self.width as i32 - 1) as u32;
        let cy = y.clamp(0, self.height as i32 - 1) as u32;
        self.at(cx, cy)
    }

    /// Write a pixel. Out-of-range coordinates clamp to the edge.
    #[inline]
    pub fn set(&mut self, x: u32, y: u32, value: T) {
        let i = self.index(x, y);
        self.data[i] = value;
    }

    /// Whether a continuous pixel-space coordinate lands inside the buffer.
    #[inline]
    pub fn contains(&self, x: f32, y: f32) -> bool {
        x >= 0.0 && y >= 0.0 && x < self.width as f32 && y < self.height as f32
    }

    /// Bilinear sample at a continuous pixel-space coordinate.
    ///
    /// `(x, y)` is in pixel space, so the exact center of pixel `(0, 0)` is
    /// `(0.5, 0.5)` and sampling there returns that pixel unfiltered.
    pub fn sample_bilinear(&self, x: f32, y: f32) -> T {
        let fx = x - 0.5;
        let fy = y - 0.5;
        let x0 = fx.floor();
        let y0 = fy.floor();
        let tx = fx - x0;
        let ty = fy - y0;
        let (x0, y0) = (x0 as i32, y0 as i32);

        let c00 = self.at_clamped(x0, y0);
        let c10 = self.at_clamped(x0 + 1, y0);
        let c01 = self.at_clamped(x0, y0 + 1);
        let c11 = self.at_clamped(x0 + 1, y0 + 1);

        let top = c00.scale(1.0 - tx).add(c10.scale(tx));
        let bot = c01.scale(1.0 - tx).add(c11.scale(tx));
        top.scale(1.0 - ty).add(bot.scale(ty))
    }
}

/// Linear RGBA color.
pub type ImageBuffer = Grid<[f32; 4]>;
/// Linear view-space depth.
pub type DepthBuffer = Grid<f32>;
/// Per-pixel offset, in pixels, to the previous frame.
pub type MotionBuffer = Grid<[f32; 2]>;

/// One rendered frame: everything reconstruction needs from the renderer.
#[derive(Clone, Debug)]
pub struct GBuffer {
    /// Linear RGBA.
    pub color: ImageBuffer,
    /// Linear view-space depth.
    pub depth: DepthBuffer,
    /// Offsets to the previous frame, in pixels.
    pub motion: MotionBuffer,
}

impl GBuffer {
    /// Assemble a G-buffer.
    ///
    /// # Panics
    /// If the three planes disagree on dimensions. This is checked rather than
    /// assumed because a mismatched motion plane produces plausible-looking
    /// garbage instead of an obvious failure.
    pub fn new(color: ImageBuffer, depth: DepthBuffer, motion: MotionBuffer) -> Self {
        assert_eq!(
            (color.width(), color.height()),
            (depth.width(), depth.height()),
            "color and depth dimensions differ"
        );
        assert_eq!(
            (color.width(), color.height()),
            (motion.width(), motion.height()),
            "color and motion dimensions differ"
        );
        GBuffer {
            color,
            depth,
            motion,
        }
    }

    /// An empty G-buffer: black, at the far plane, with no motion.
    pub fn blank(width: u32, height: u32) -> Self {
        GBuffer {
            color: ImageBuffer::new(width, height, [0.0, 0.0, 0.0, 1.0]),
            depth: DepthBuffer::new(width, height, f32::MAX),
            motion: MotionBuffer::zeroed(width, height),
        }
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

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn sampling_a_pixel_center_is_exact() {
        // If this drifts, everything downstream is half a texel soft.
        let mut g = ImageBuffer::zeroed(4, 4);
        g.set(2, 1, [1.0, 0.5, 0.25, 1.0]);
        let s = g.sample_bilinear(2.5, 1.5);
        for (a, b) in s.iter().zip([1.0, 0.5, 0.25, 1.0].iter()) {
            assert!((a - b).abs() < 1e-6, "{s:?}");
        }
    }

    #[test]
    fn sampling_a_midpoint_averages_neighbours() {
        let mut g = DepthBuffer::zeroed(2, 1);
        g.set(0, 0, 1.0);
        g.set(1, 0, 3.0);
        assert!((g.sample_bilinear(1.0, 0.5) - 2.0).abs() < 1e-6);
    }

    #[test]
    fn sampling_outside_clamps_to_the_edge() {
        let mut g = DepthBuffer::zeroed(2, 2);
        g.set(0, 0, 7.0);
        assert_eq!(g.sample_bilinear(-100.0, -100.0), 7.0);
        assert_eq!(g.at_clamped(-5, -5), 7.0);
    }

    #[test]
    fn contains_matches_pixel_space() {
        let g = DepthBuffer::zeroed(4, 4);
        assert!(g.contains(0.0, 0.0));
        assert!(g.contains(3.99, 3.99));
        assert!(!g.contains(4.0, 2.0));
        assert!(!g.contains(-0.01, 2.0));
    }

    #[test]
    #[should_panic(expected = "motion")]
    fn mismatched_planes_are_rejected() {
        GBuffer::new(
            ImageBuffer::zeroed(4, 4),
            DepthBuffer::zeroed(4, 4),
            MotionBuffer::zeroed(2, 2),
        );
    }
}
