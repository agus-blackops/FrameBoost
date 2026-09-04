# Wiring FrameBoost into a renderer

The per-frame contract, what the renderer has to provide, and the handful of
things that will bite.

## The frame loop

```rust
use frameboost_core::{BoostController, ControllerConfig, FramePacer, Signals, Verdict};
use frameboost_gpu::{GpuContext, GpuGBuffer, ReconTarget, Reconstructor};
use frameboost_core::recon::InterpolateConfig;

let mut ctl = BoostController::new(ControllerConfig::for_target_fps(3840, 2160, 60.0));
let mut pacer = FramePacer::for_target_fps(60.0);
let recon = Reconstructor::new(&ctx);

let mut history: Option<GpuGBuffer> = None;
let mut last_rung: Option<u8> = None;
let mut last_frame_ns = 16_666_667;

loop {
    // 1. Ask what to render. `last_frame_ns` is the GPU time of the previous
    //    frame; pass the target on the first call.
    let plan = ctl.plan(last_frame_ns);

    // 2. A rung change invalidates both histories. Skipping either is the
    //    subtlest bug in this whole system — see "Two resets" below.
    if last_rung != Some(plan.level) {
        pacer.reset();
        if history.as_ref().is_some_and(|h| h.width() != plan.render_width) {
            history = None;
        }
        last_rung = Some(plan.level);
    }

    // 3. Render at the requested size.
    let current = render_scene(plan.render_width, plan.render_height);

    // 4. Measure, reconstruct, and judge — but only with history to judge
    //    against.
    if let Some(prev) = &history {
        let mut signals = recon.measure_pair(
            &ctx, prev, &current, plan.render_width as f32 * 0.10,
        );
        signals.pacing_instability = pacer.instability();

        if plan.generate {
            let disocclusion = recon.interpolate(
                &ctx, prev, &current, 0.5, &target, InterpolateConfig::default(),
            );
            signals.disocclusion = disocclusion;
        }

        match ctl.resolve(&signals).verdict {
            Verdict::Present { .. } => present_all(&pacer.slots_for(plan.rung, now_ns())),
            // The generated frame is not shown. The newest rendered frame goes
            // in its slot instead.
            Verdict::Discard { .. } => present_rendered_only(),
            // The frame is real and is shown regardless; the ceiling has
            // already dropped for subsequent frames.
            Verdict::Degrade { .. } => present_all(&[]),
        }
    }

    last_frame_ns = measured_gpu_time();
    pacer.on_rendered(now_ns());
    history = Some(current);
}
```

## What the renderer must provide

Three planes, and the conventions are load-bearing. Getting any of them
subtly wrong produces output that looks *almost* right, which is much worse
than output that looks broken.

| plane | format | convention |
|---|---|---|
| colour | linear RGBA | **Linear, not sRGB.** Reconstruction blends; blending in a non-linear space darkens edges. |
| depth | linear view-space | Increasing away from camera. **Not** post-projection `1/w`, whose precision distribution makes the relative depth comparisons meaningless at distance. |
| motion | pixels | For each pixel in the *current* frame, the offset to where that surface was in the *previous* one. The previous position of `p` is `p + mv(p)`. |

Pixel `(x, y)` has its centre at `(x + 0.5, y + 0.5)`. Every continuous
coordinate in the codebase is in that space; being half a texel out reads as a
persistent softness that is miserable to track down.

RCAS assumes values in `0..=1` — the `1 - max` term in its limit computation is
where the clipping headroom comes from. On an HDR buffer, tonemap first, or the
limit is computed against a ceiling that is not where clipping actually
happens and the pass will ring.

## Two resets

Both of these were bugs found by the simulator, and neither is obvious.

**Reset the G-buffer history when render resolution changes.** The previous
frame is the wrong size to compare against. There is nothing to measure that
frame, so the controller should not be asked to judge it — skip `resolve()`
entirely rather than passing it a default `Signals`, which reads as a clean
frame it never verified.

**Reset the pacer when the rung changes.** This one is worse, because it
creates a feedback loop that sustains itself. A rung change alters frame cost
by design; the pacer's interval window then holds a mix of the old rung's
intervals and the new one's, and the dispersion between them reads as
instability. The instability rejects the frame, the rejection changes the rung,
and the change is another interval step. Left alone, the controller oscillates
on nothing but its own decisions — and every scene looks the same, because it
never stays anywhere long enough for content to matter.

`FramePacer::instability` is meant to report *unexplained* variation. Variation
the controller itself caused is explained, and must not come back to it as
evidence.

## Where each signal comes from

| signal | source |
|---|---|
| `disocclusion` | `Reconstructor::interpolate` returns it — measured *before* hole filling. |
| `motion_residual`, `luma_shift`, `camera_motion`, `depth_complexity` | `Reconstructor::measure_pair` |
| `pacing_instability` | `FramePacer::instability` |

`measure_pair` runs on the GPU on purpose. Reading a full-resolution frame back
every frame to compute five numbers would cost more than the boost saves and
would stall the pipeline doing it; what comes back is a few floats per
workgroup.

The disocclusion figure is deliberately the pre-fill one. A filled hole still
shows content no rendered frame contained; reporting the post-fill number would
tell the controller everything is fine right up until a tester says otherwise.

## Tuning

Start by *logging*, not by turning knobs. Every rejection carries the signal
that dominated it, and `Telemetry::summary()` gives the shape of a run in one
line. An adaptive system you cannot interrogate is one you will end up
disabling.

| symptom | first thing to reach for |
|---|---|
| Artifacts are shipping | `QualityConfig::reject_below` — raise it. Then check the dominant signal on frames that should have been rejected and were not. |
| Rejects constantly on ordinary content | Check the dominant signal, then that signal's `deadband`. The defaults are anchored to measured values from the simulator's scenarios; a real renderer's motion vectors are worse than its exact ones, so residual deadbands in particular may want widening. |
| Resolution visibly fidgets | `PerfConfig::dwell_frames` — raise it. It is 20 by default, about a third of a second at 60 Hz. |
| Takes forever to recover after a difficult scene | `cooldown_frames` and `growth_interval`. Total recovery is their **sum**, not the larger of the two. |
| Frame counter doubled, feels worse | Pacing. Check `pacing_error_ns` against the slots, and remember interpolation costs a full rendered-frame of latency by construction. |
| Cuts are not detected | `cut_residual` and `cut_luma` both have to trip. A cut with no exposure change is caught by disocclusion instead, one layer down, with a gentler response — verify that is happening before loosening the cut thresholds, because loosening them is how a whip pan starts being treated as a cut. |

Two things to leave alone unless you have a specific reason:

- **`EasuConfig::deringing`.** The kernel's negative lobe is what makes the
  output look resolved rather than smeared, and it is also what overshoots into
  a bright halo along every high-contrast edge. The clamp keeps the first and
  discards the second. Turn it off to see what it is doing, then turn it back
  on.
- **The rung ordering.** All the spatial rungs come before all the temporal
  ones because spatial reconstruction degrades gracefully and temporal
  reconstruction degrades catastrophically. Interleaving them makes "step down
  to be safe" stop meaning what the controller assumes it means.

## Porting the shaders

The scalar implementations in `frameboost_core::recon` are the definition of
what the WGSL is supposed to compute, and `frameboost-gpu`'s parity tests
compare the two pixel by pixel over identical inputs. If you port these passes
to another API, port the tests with them — that comparison is what makes a
shader *known* to work rather than believed to.

Two places the parity tests earned their keep, both of which produced plausible
output while being wrong:

- The ping-pong copy-back after an odd number of hole-fill passes must be an
  actual texture copy. Running one more dilation pass instead silently performs
  one more fill than requested.
- Texture-to-buffer copies stride rows to `COPY_BYTES_PER_ROW_ALIGNMENT`. Strip
  the padding on readback, or the image is correct at aligned widths and
  sheared at every other one.

Comparison needs a *relative* tolerance, not just an absolute one. EASU with
deringing disabled legitimately produces values outside `0..=1`, and there the
kernel normalises by a small weight sum, which amplifies the difference between
the CPU's summation order and the GPU's fused multiply-adds to about 0.1% of
magnitude. An absolute bound tight enough for the `0..=1` range fails on
arithmetic that is entirely correct.
