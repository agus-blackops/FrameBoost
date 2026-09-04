//! CPU reference implementations of the reconstruction passes.
//!
//! These exist to be *read* and to be *checked against*, not to ship. A real
//! integration runs the WGSL in `frameboost-gpu`; these are the definition of
//! what that WGSL is supposed to compute, and the oracle its tests compare to.
//! Keeping a legible scalar version of every pass is worth the duplication:
//! shader bugs are otherwise diagnosed by staring at a screenshot.
//!
//! The four passes, in the order a frame moves through them:
//!
//! 1. [`reproject`] — warp the previous frame into the current one along the
//!    motion vectors, and report where that fails. The disocclusion mask it
//!    produces is what the controller ultimately reacts to.
//! 2. [`interpolate_midpoint`] — build a frame that was never rendered, from
//!    the two rendered frames on either side of it.
//! 3. [`easu`] — edge-adaptive spatial upsampling, for the rungs that render
//!    below output resolution.
//! 4. [`rcas`] — sharpening that will not clip, to put back the acutance
//!    upsampling costs.

mod easu;
mod interpolate;
mod rcas;
mod reproject;

pub use easu::{easu, EasuConfig};
pub use interpolate::{interpolate_midpoint, Interpolated, InterpolateConfig};
pub use rcas::rcas;
pub use reproject::{reproject, Reprojected, ReprojectConfig};

/// Relative difference between two depths, in `0..=1`.
///
/// Relative rather than absolute because a 10 cm discrepancy is a disocclusion
/// on a weapon held at arm's length and rounding error on a mountain 4 km away.
/// An absolute tolerance can be correct for one of those, never for both.
#[inline]
pub fn relative_depth_delta(a: f32, b: f32) -> f32 {
    let scale = a.abs().max(b.abs()).max(1e-4);
    ((a - b).abs() / scale).min(1.0)
}

/// Soft validity from a relative error and a tolerance.
///
/// Returns 1 below `tolerance`, falling to 0 at twice it. Soft rather than
/// binary because a hard mask stamps its own edges into the output: pixels
/// either side of the threshold get entirely different treatment, and the
/// boundary between them becomes a visible contour that moves with the camera.
#[inline]
pub fn soft_validity(error: f32, tolerance: f32) -> f32 {
    let t = tolerance.max(1e-6);
    if error <= t {
        1.0
    } else if error >= 2.0 * t {
        0.0
    } else {
        1.0 - (error - t) / t
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn depth_delta_is_scale_free() {
        // The same absolute gap must read as significant up close and
        // negligible far away.
        let near = relative_depth_delta(1.0, 1.1);
        let far = relative_depth_delta(4000.0, 4000.1);
        assert!(near > 0.05);
        assert!(far < 1e-3);
    }

    #[test]
    fn validity_ramps_instead_of_stepping() {
        assert_eq!(soft_validity(0.0, 0.1), 1.0);
        assert_eq!(soft_validity(0.1, 0.1), 1.0);
        assert!((soft_validity(0.15, 0.1) - 0.5).abs() < 1e-5);
        assert_eq!(soft_validity(0.2, 0.1), 0.0);
        assert_eq!(soft_validity(5.0, 0.1), 0.0);
    }
}
