# FrameBoost

**Present more frames than you render — and know when to stop.**

FrameBoost sits between a renderer and a swapchain. It lets the renderer draw
fewer, smaller frames than the display consumes and reconstructs the
difference, spatially by upscaling and temporally by generating frames that
were never drawn.

The reconstruction kernels are the easy half. Every upscaler ships those. The
part this project is actually about is the loop that watches its own output,
notices when reconstruction has stopped being trustworthy, and hands the boost
back before anyone sees the artifact.

---

## So is it a loss scaling, or an FSR?

The question this repository started from, and it turns out to have a specific
answer: **it does what FSR does, and it is controlled the way loss scaling is
controlled.** The two halves are not interchangeable, and confusing them is how
boost systems ship ghosting.

### The difference that matters

**Loss scaling** is an *exact* trick. FP16 has a narrow dynamic range, so small
gradients fall into denormals or flush to zero. Multiply the loss by `S` before
the backward pass, the chain rule scales every gradient by `S` and lifts them
into representable range, divide by `S` before the optimizer step. The
resulting mathematics is unchanged; only the intermediate representation moved.
Nothing is lost.

**FSR** is an *approximate* trick. Render at 67% and reconstruct. FSR 1 is
purely spatial — edge-adaptive upsampling plus contrast-limited sharpening.
FSR 2 is temporal: motion vectors, sub-pixel jitter, a history buffer,
reprojection, rejection of samples in disoccluded regions. FSR 3 adds frame
generation over optical flow. The result is *not* what native rendering would
have produced. It is perceptually close, with a known artifact vocabulary:
ghosting, shimmer, smear across disocclusions.

Anything that generates frames is in the second family. There is no factor you
can divide back out at the end; you are producing pixels no renderer drew.
Calling it "lossless" is not a stretch of the word, it is the opposite of the
word.

### The half worth stealing

But the interesting thing about dynamic loss scaling was never the
multiplication. It is the **control loop around it**: start optimistic, watch
for `inf`/`NaN`, and when they appear, *throw away the step you already
computed*, halve the factor, and refuse to grow again until a run of clean
steps has accumulated. It fails safe and it recovers on its own.

That mechanism is exactly what most frame-boosting systems lack, and mapping it
across is what turns an analogy into a design:

| `GradScaler` | FrameBoost |
|---|---|
| scale factor `S` | rung on the boost ladder |
| gradients contain `inf`/`NaN` | confidence below the rejection threshold |
| **skip the optimizer step** | **discard the generated frame, present the real one** |
| `S *= 0.5` on overflow | step down the ladder |
| `S *= 2` after N clean steps | step up after N clean frames |
| growth interval | cooldown + clean streak |

The row in bold is the whole idea. Everything else is bookkeeping.

### Where the analogy has to break

Three places, and each one is a design decision rather than an imperfection in
the metaphor:

1. **The rejection test is statistical, not exact.** `inf` is a bit pattern.
   "This frame will look wrong" is a threshold on a weighted product of proxies
   — disocclusion coverage, motion-vector residual, luminance shift. So the
   threshold is tunable, the dominant signal is reported with every rejection,
   and both are treated as things a title will need to tune rather than
   constants someone got right once.

2. **Rejection means different things at different rungs.** You can decline to
   present a frame you generated. You cannot un-upscale a frame you already
   rendered at 59% — it is a real frame and it is going on screen. Hence two
   verdicts: `Discard` (temporal; the frame is thrown away) and `Degrade`
   (spatial; the frame is shown, but the rung loses the controller's trust).

3. **A renderer also has a frame budget.** Loss scaling answers only to
   numerics. This needs a second governor, and a rule between them.

---

## Quality vetoes performance

```
active_rung = min(perf.requested, quality.ceiling)
```

Two governors. The **quality governor** is the loss-scaling-shaped one: it owns
a *ceiling*, the most aggressive rung reconstruction has currently earned. The
**perf governor** watches frame time and asks for whatever rung meets the
budget. The arbitration is one line, and it always resolves the same way: being
over budget never buys a rung that quality has withdrawn.

Missing frame budget is a worse frame. Shipping a broken reconstruction is a
worse game. When a boost system starts shipping artifacts, this line is where
to look first — the failure is nearly always that something let the frame
budget outvote the evidence.

---

## The ladder

Boost is not a switch. It is an ordered ladder, each rung cheaper and riskier
than the one below:

| rung | render scale | generated | cost / presented frame | risk |
|---|---|---|---|---|
| `native` | 1.00 | — | 1.04× | 0.00 |
| `ultra_quality` | 0.77 | — | 0.63× | 0.10 |
| `quality` | 0.67 | — | 0.49× | 0.18 |
| `balanced` | 0.59 | — | 0.39× | 0.28 |
| `performance` | 0.50 | — | 0.29× | 0.40 |
| `performance_gen` | 0.50 | 1 interpolated | 0.17× | 0.65 |
| `ultra_performance_gen` | 0.33 | 1 interpolated | 0.09× | 0.85 |

Two orthogonal levers folded into one ordering, deliberately. It means the
controller makes one decision per frame instead of searching a 2-D space, and
that backing off is always unambiguous: step down.

All the spatial rungs come first because **spatial reconstruction degrades
gracefully and temporal reconstruction degrades catastrophically.** A soft
frame is a soft frame; an invented frame straddling a scene cut is a dissolve
between two unrelated images. Given the choice, spend the last of the budget on
pixels, not on frames.

### The perf governor inverts the cost model

Adjacent rungs differ in cost by factors from 1.26 to 1.76. That rules out
stepping toward the budget with a deadband: any deadband narrow enough to be
useful is narrower than the step it is damping, so the governor overshoots
going up, undershoots coming down, and hunts between two rungs forever —
visibly, because every flip changes render resolution. Widening it past 1.76
instead would mean holding a rung until frame time fell below 57% of target,
wasting most of the headroom the boost just bought.

So it estimates what a native frame would cost (`measured / cost(active_rung)`)
and picks the *least* aggressive rung whose predicted cost fits. Systematic
error in the cost model largely cancels, because the estimate is measured
through the same model it is applied to — what has to be right is the ratios
between rungs, not their absolute values.

---

## The signals

Six proxies, each normalized to `0..=1`, each with a deadband below which it is
free — because every real frame carries some of all six, and a few percent of
the screen disoccluding whenever the camera moves is not a defect.

| signal | what it catches |
|---|---|
| **disocclusion** | Pixels with no source in either endpoint: the holes generation has to invent. The strongest single predictor of visible smear. |
| **motion residual** | Change the motion vectors do not explain — particles, alpha, shader animation, a light turning on. Exactly what frame generation destroys, and exactly what the G-buffer will never warn you about. |
| **luma shift** | Global brightness change: exposure adaptation, a muzzle flash, a cut. |
| **camera motion** | Screen-space velocity. Fast motion magnifies every other error. |
| **depth complexity** | Density of depth discontinuities. Foliage, fences, wires, hair. |
| **pacing instability** | Variance in render intervals. A generated frame shown at the wrong moment is worse than no generated frame. |

Penalties are **multiplicative**, because these are not nuisances that average
out — any one of them at full strength ruins the frame alone, and a weight
above 1.0 lets a single signal veto outright.

Weights depend on the rung. A spatial upscaler does not reproject, so scoring
it on disocclusion would make the controller abandon a perfectly healthy rung
during a fast pan. What actually breaks a spatial upscaler is thin geometry.

### Scene cuts get their own path

A cut is the one condition that bypasses the confidence score. Interpolating
across one does not produce a slightly wrong frame; it produces a dissolve
between two unrelated images, and there is no gradual response to that.

Detection requires **both** an unexplained image and a luminance jump. Either
alone is common and benign — a whip pan wrecks the residual without being a
cut, a fade wrecks the luma without being one either. That restraint is
deliberate, and it means a cut between two shots of identical average
luminance will not be flagged. It is caught anyway, one layer down, by
disocclusion saturating the ordinary confidence test. Defense in depth: same
discard, gentler response.

The retreat is to the **top spatial rung, not to native**. A cut breaks
reprojection and does nothing at all to an upscaler; surrendering the spatial
boost too would spike frame time at exactly the moment the renderer has a whole
new scene to draw.

---

## Pacing, and the latency interpolation actually costs

Frame generation that ignores pacing makes things worse while the counter says
they got better. Render at 60 Hz, generate one frame per rendered frame, hand
both to the swapchain as they appear: the display shows 120 distinct images per
second, spaced 0 ms and 16 ms apart. That is judder, and it is worse than the
60 Hz it replaced.

And to place an image between A and B you must already have B — so A cannot be
shown when it is ready. The entire presentation timeline slips by one rendered
interval:

```
  rendered:   A-------B-------C-------
  presented:  --------A---M---B---M---
                      ^ one full interval late
```

That delay is not an implementation shortcoming to optimize away. It is what
interpolation *is*, it is why extrapolation exists as an option despite being
less accurate, and it is why a doubled frame counter can make a game feel worse
to play.

---

## What the simulator shows

`frameboost-sim` runs the real controller over a synthetic three-layer parallax
renderer in a **closed loop**: every signal is measured from rendered pixels by
the same code a real integration calls, and frame time is fed back from the
rung the controller chose, so boosting genuinely buys headroom.

Six scenarios, at 192×108, targeting 60 fps, against a native frame that costs
70 ms:

| scenario | boost | cost | rejected | delivered |
|---|---|---|---|---|
| calm-pan | 1.92× | 0.20× | 0.0% | 137.6 fps |
| whip-turn | 1.78× | 0.21× | 0.1% | 119.5 fps |
| scene-cut | 1.48× | 0.25× | 0.1% | 85.1 fps |
| particle-burst | 1.52× | 0.25× | 0.2% | 87.3 fps |
| fence | 1.00× | 0.40× | 0.4% | 35.4 fps |
| load-swing | 1.47× | 0.30× | 0.0% | 77.3 fps |

The ladder over time is more informative than the totals (digit = rung,
`!` = a frame was rejected in that span):

```
calm-pan        012234555555555555555555555555555555555555555555555555555555
                ············································································
```
Climbs, arrives, stops. A system that fidgets on a slow pan will fidget on
everything.

```
whip-turn       012345555555555555555555544444444444455555555555555555555555
                ·························!··················································
```
The whip opens large disocclusions, the generated frame is discarded, the
ladder drops to spatial and climbs back once the pan settles.

```
particle-burst  012234555555555555555555555555555444444444444444444444444444
                ·································!·············!····························
```
Geometry is perfectly well behaved throughout this one. Depth and motion
vectors report a completely healthy frame; only photo-consistency notices. This
is the failure that makes shipped frame generation look broken.

```
fence           012233333333333333333333333333333333333333333333333333333333
                ·····!·············!·············!·············!·············!··············
```
Thin geometry a spatial upscaler cannot resolve. The controller parks at
`balanced`, probes `performance` every ~230 frames, is rejected on depth
complexity, and backs off — settling rather than thrashing, and never giving
up permanently, because content changes.

Results are identical at 192×108 and 96×54: the scenarios measure the
scenarios, not the resolution they were run at.

---

## Layout

```
crates/
  frameboost-core/   no dependencies. The ladder, signals, both governors,
                     pacing, and scalar reference implementations of every
                     reconstruction pass.
  frameboost-gpu/    wgpu + seven WGSL compute shaders. What ships.
  frameboost-sim/    headless closed-loop harness and scenario catalogue.
```

`core` keeps scalar versions of the same passes the shaders implement, and that
duplication is the point: `frameboost-gpu`'s tests run both over identical
inputs and compare pixel by pixel. Shader bugs are otherwise diagnosed by
staring at a screenshot and arguing about whether the smear looks wrong —
"the disocclusion mask is inverted along one edge" is not something anyone
finds that way.

## Running it

```sh
cargo test --workspace                       # 108 tests
cargo run --release -p frameboost-sim        # all six scenarios
cargo run --release -p frameboost-sim -- --scenario whip-turn --native-ms 90
cargo run --release -p frameboost-sim -- --scenario fence --csv > fence.csv
```

The GPU parity tests need a wgpu adapter and skip themselves without one. A
software rasteriser is sufficient — these are ordinary compute shaders, and the
suite runs green under Mesa's llvmpipe.

## Status, and what this is not

The control loop, the signal measurement, the pacing model and all seven
reconstruction passes are implemented and tested. What is not here:

- **No engine integration.** There is no swapchain, no presentation thread, no
  render graph. The passes take G-buffers and produce frames; wiring that into
  a renderer's frame loop is the next piece of work.
- **The spatial passes are faithful in structure, not bit-exact ports.** EASU
  here does what EASU does — direction from the structure tensor, anisotropic
  kernel, deringing clamp — but AMD's implementation is packed, approximated
  and hand-tuned in ways that make it fast and unreadable. RCAS is close to the
  published formulation.
- **The cost model is a model.** `relative_cost()` assumes cost scales with
  pixel count plus a fixed reconstruction pass. Real GPUs have bandwidth
  cliffs. The perf governor is deliberately built to depend on the *ratios*
  between rungs rather than absolute values, but the ratios still want
  measuring on real hardware.
- **Frame time in the simulator is modelled, not measured.** Everything else in
  it is measured.
- **No jittered accumulation.** A real FSR2-class upscaler accumulates
  sub-pixel-jittered samples across frames and resolves detail no single frame
  contained. The spatial path here reconstructs from one frame.

## License

MIT.
