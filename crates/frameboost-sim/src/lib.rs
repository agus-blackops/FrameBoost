//! A headless harness that drives the FrameBoost controller through scripted
//! scenarios.
//!
//! There is no GPU here and no display, so what this cannot do is tell you
//! whether reconstruction *looks* right — that needs eyes on a real frame. What
//! it can do is answer the questions that actually decide whether the system is
//! shippable, and answer them reproducibly:
//!
//! - Does the ladder settle, or does it fidget on content that never changes?
//! - When reconstruction genuinely fails, is the frame thrown away — and how
//!   long does it take to get the boost back?
//! - Does the quality veto hold when the frame budget is screaming?
//! - Do the two governors stay decoupled, or does load leak into quality?
//!
//! Every signal the controller sees here is measured from rendered pixels by
//! the same code a real integration would call. The one thing modelled is frame
//! time, and it is modelled in a closed loop, so boosting really does buy
//! headroom.

pub mod harness;
pub mod scenario;
pub mod scene;

pub use harness::{run, Config, FrameRecord, Judgement, RunReport};
pub use scenario::{by_name, Beat, Scenario, CATALOGUE};
pub use scene::{Frame, Scene};
