//! Behavioural tests for the control loop, end to end.
//!
//! The unit tests in `frameboost-core` check each piece against signals handed
//! to it directly. These run the whole thing — synthetic renderer, real
//! reconstruction passes, measured signals, closed-loop frame time — and assert
//! on what the controller actually does with content it has never been told
//! anything about.
//!
//! They are written as properties rather than golden values, so retuning a
//! threshold does not mechanically break them; only a change in *behaviour*
//! does.

use frameboost_sim::harness::{Config, Judgement};
use frameboost_sim::{by_name, run, RunReport};
use frameboost_core::SignalKind;

fn go(name: &str) -> RunReport {
    let s = by_name(name).unwrap_or_else(|| panic!("no scenario '{name}'"));
    run(s, &Config::fast())
}

fn levels_in(r: &RunReport, range: std::ops::Range<u32>) -> Vec<u8> {
    r.frames
        .iter()
        .filter(|f| range.contains(&f.index))
        .map(|f| f.level)
        .collect()
}

#[test]
fn easy_content_settles_and_then_stops_moving() {
    // A boost system that fidgets on a slow pan will fidget on everything, and
    // every change of render resolution is visible.
    let r = go("calm-pan");
    let tail = levels_in(&r, 700..1200);
    let first = tail[0];
    assert!(first > 0, "never boosted on trivially easy content");
    assert!(
        tail.iter().all(|l| *l == first),
        "still moving late in an unchanging scene: {:?}",
        &tail[..30]
    );
    assert_eq!(r.rejections_in(200..1200), 0, "rejected a clean frame");
}

#[test]
fn boosting_actually_buys_frame_time() {
    let r = go("calm-pan");
    assert!(
        r.mean_frame_ns() < r.config.native_frame_ns as f64 * 0.5,
        "boost bought nothing: {:.1} ms mean vs {:.1} ms native",
        r.mean_frame_ns() / 1e6,
        r.config.native_frame_ns as f64 / 1e6
    );
    assert!(
        r.effective_fps() > r.config.target_fps as f64,
        "did not reach the target: {:.1} fps",
        r.effective_fps()
    );
}

#[test]
fn a_whip_pan_discards_generated_frames_and_is_recovered_from() {
    let r = go("whip-turn");
    let during: Vec<_> = r
        .rejections()
        .filter(|f| (480..760).contains(&f.index))
        .collect();
    assert!(!during.is_empty(), "sailed through a whip pan");
    assert!(
        during.iter().any(|f| f.dominant == Some(SignalKind::Disocclusion)),
        "rejected for the wrong reason: {:?}",
        during.iter().map(|f| f.dominant).collect::<Vec<_>>()
    );
    assert!(
        during.iter().all(|f| f.judgement == Judgement::Discarded),
        "a temporal rung should discard, not merely degrade"
    );

    // And it must climb back, or one whip pan costs the rest of the session.
    let before = r.typical_level(300..480);
    let after = r.typical_level(1000..1500);
    assert!(after >= before, "never recovered: {before} -> {after}");
}

#[test]
fn an_exposure_changing_cut_takes_the_hard_path() {
    let r = go("scene-cut");
    let cut = r
        .rejections()
        .find(|f| (500..505).contains(&f.index))
        .expect("missed the first cut entirely");
    assert!(cut.scene_cut, "not recognised as a cut");
    assert_eq!(cut.judgement, Judgement::Discarded);
    assert!(r.telemetry.scene_cuts >= 1);

    // The hard path means a long cooldown: the ladder must stay down for
    // meaningfully longer than an ordinary rejection would cost.
    let after = r.typical_level(505..760);
    let before = r.typical_level(300..495);
    assert!(after < before, "cut cost nothing: {before} -> {after}");
}

#[test]
fn a_cut_without_an_exposure_change_is_still_caught() {
    // Defense in depth. The cut detector deliberately abstains without a
    // luminance jump — that restraint is what keeps a whip pan from being
    // mistaken for a cut — so the ordinary confidence path has to catch this
    // one. It is still discarded; only the severity differs.
    let r = go("scene-cut");
    let second = r
        .rejections()
        .find(|f| (1000..1006).contains(&f.index))
        .expect("second cut went through undetected");
    assert!(!second.scene_cut, "cut detector should have abstained here");
    assert_eq!(second.judgement, Judgement::Discarded);
}

#[test]
fn content_the_gbuffer_never_mentions_is_caught() {
    // Geometry is perfectly well behaved throughout this scenario. Depth and
    // motion vectors alone would report a completely healthy frame; only
    // photo-consistency notices. This is the failure mode that makes shipped
    // frame generation look broken.
    let r = go("particle-burst");
    let during: Vec<_> = r
        .rejections()
        .filter(|f| (490..830).contains(&f.index))
        .collect();
    assert!(!during.is_empty(), "the burst went entirely unnoticed");
    assert!(
        during
            .iter()
            .any(|f| f.dominant == Some(SignalKind::MotionResidual)),
        "caught, but not by the signal that should have caught it: {:?}",
        during.iter().map(|f| f.dominant).collect::<Vec<_>>()
    );
    assert_eq!(
        r.rejections_in(0..450),
        0,
        "rejected frames before the burst started"
    );
}

#[test]
fn thin_geometry_caps_the_ladder_without_collapsing_it() {
    let r = go("fence");
    assert!(r.max_level() > 0, "gave up entirely on difficult content");
    let settled = r.typical_level(400..1200);
    assert!(settled > 0, "collapsed to native and stayed there");
    assert!(
        settled < 5,
        "boosted into content it cannot resolve: rung {settled}"
    );
    // Settling, not thrashing: it may probe the next rung and back off, but it
    // must not spend its life rejecting.
    assert!(
        r.telemetry.discard_rate() < 0.10,
        "thrashing: {:.1}% rejected",
        r.telemetry.discard_rate() * 100.0
    );
    assert_eq!(r.telemetry.worst_signal(), Some(SignalKind::DepthComplexity));
}

#[test]
fn render_load_alone_never_moves_the_quality_ceiling() {
    // The two governors answer to different things. If cost swings alone can
    // pull the quality ceiling around, they are coupled through something they
    // should not be — and the veto stops meaning anything.
    let r = go("load-swing");
    assert_eq!(r.telemetry.discard_rate(), 0.0, "load caused a rejection");

    let ceilings: Vec<u8> = r.frames.iter().map(|f| f.ceiling).collect();
    let top = *ceilings.iter().max().unwrap();
    assert!(
        ceilings.iter().all(|c| *c == top),
        "the quality ceiling moved in response to load alone"
    );

    // The perf governor, meanwhile, should be doing plenty.
    let mut seen: Vec<u8> = r.frames.iter().map(|f| f.level).collect();
    seen.sort_unstable();
    seen.dedup();
    assert!(
        seen.len() >= 3,
        "perf governor barely reacted to a large load swing: {seen:?}"
    );
}

#[test]
fn every_scenario_runs_the_full_pipeline_without_producing_garbage() {
    // The spatial passes are exercised on real pixels here, and their output is
    // range-checked inside the harness under debug assertions.
    let cfg = Config {
        full_pipeline: true,
        ..Config::fast()
    };
    for s in frameboost_sim::CATALOGUE {
        let mut short = *s;
        short.frames = 240;
        let r = run(&short, &cfg);
        assert_eq!(r.frames.len(), 240, "{} produced the wrong length", s.name);
        assert!(
            r.frames.iter().all(|f| f.confidence.is_finite()),
            "{} produced a non-finite confidence",
            s.name
        );
    }
}
