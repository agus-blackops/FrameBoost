//! FrameBoost core: present more frames than you render, and know when to stop.
//!
//! # What this crate is
//!
//! FrameBoost sits between a renderer and a swapchain. It lets the renderer draw
//! fewer, smaller frames than the display consumes, and reconstructs the
//! difference — spatially (upscaling) and temporally (frame generation).
//!
//! Reconstruction is *approximate*. It is wrong sometimes, and when it is wrong
//! it is visibly wrong: ghosting, smear across disocclusions, a whole invented
//! frame straddling a scene cut. The part of this crate that matters is not the
//! resampling kernels — it is [`BoostController`], the loop that watches its own
//! output and gives back the boost when reconstruction stops being trustworthy.
//!
//! # The control loop
//!
//! That loop is lifted, almost structurally unchanged, from dynamic loss scaling
//! in mixed-precision training:
//!
//! | `GradScaler`                        | [`BoostController`]                       |
//! |-------------------------------------|-------------------------------------------|
//! | scale factor `S`                    | rung on the [`LADDER`]                    |
//! | check gradients for `inf`/`NaN`     | [`Signals`] → confidence below threshold  |
//! | **skip the optimizer step**         | **discard the frame, present the real one** |
//! | `S *= backoff` on overflow          | step down one or more rungs               |
//! | `S *= growth` after N clean steps   | step up after N clean frames              |
//!
//! The mechanism worth copying is not the scaling. It is the willingness to
//! throw away work that has already been done, the moment the cheap path stops
//! being equivalent to the honest one.
//!
//! Where FrameBoost has to diverge: loss scaling only answers to numerics, while
//! a renderer also has a frame budget to hit. So there are two governors —
//! [`QualityGovernor`] (loss-scaling shaped) and [`PerfGovernor`] — and one
//! resolution rule between them: **quality vetoes performance**. The perf
//! governor may ask for any rung up to the ceiling quality has established, and
//! never past it. Missing frame budget is a worse frame; shipping a broken
//! reconstruction is a worse *game*.
//!
//! # Layout
//!
//! - [`level`] — the boost ladder: what each rung costs and risks.
//! - [`signals`] — the per-frame evidence that reconstruction is going wrong.
//! - [`governor`] — the two governors and the controller that arbitrates them.
//! - [`pacing`] — present-time scheduling for generated frames.
//! - [`recon`] — CPU reference implementations of the reconstruction passes.
//! - [`telemetry`] — why the controller did what it did.
//!
//! The [`recon`] implementations are a readable reference and the ground truth
//! the WGSL ports in `frameboost-gpu` are tested against. They are not the
//! shipping path; a renderer runs the shaders and calls into [`governor`].
//!
//! # Example
//!
//! ```
//! use frameboost_core::{BoostController, ControllerConfig, Signals, Verdict};
//!
//! let mut ctl = BoostController::new(ControllerConfig::for_target_fps(3840, 2160, 60.0));
//!
//! // Rendering at 40 fps against a 60 fps target, reconstructing cleanly:
//! // the controller climbs the ladder to buy the frame time back.
//! for _ in 0..600 {
//!     let plan = ctl.plan(25_000_000);
//!     assert!(plan.render_width <= 3840);
//!     ctl.resolve(&Signals::clean());
//! }
//! assert!(ctl.level() > 0, "should have boosted on a clean, over-budget sequence");
//!
//! // A scene cut: the generated frame is thrown away rather than shown.
//! ctl.plan(25_000_000);
//! let outcome = ctl.resolve(&Signals::scene_cut());
//! assert!(matches!(outcome.verdict, Verdict::Discard { .. }));
//! assert_eq!(outcome.frames_presented, 1);
//! ```

#![forbid(unsafe_code)]
#![warn(missing_docs)]

pub mod frame;
pub mod governor;
pub mod level;
pub mod pacing;
pub mod recon;
pub mod signals;
pub mod telemetry;

pub use frame::{DepthBuffer, GBuffer, ImageBuffer, MotionBuffer};
pub use governor::{
    BoostController, ControllerConfig, DiscardReason, FrameOutcome, PerfConfig, PerfGovernor,
    QualityConfig, QualityGovernor, Verdict,
};
pub use level::{BoostPlan, BoostRung, GenMode, LADDER};
pub use pacing::{FramePacer, PresentSlot, SlotKind};
pub use signals::{Confidence, SignalKind, SignalWeights, Signals};
pub use telemetry::{Decision, Telemetry};
