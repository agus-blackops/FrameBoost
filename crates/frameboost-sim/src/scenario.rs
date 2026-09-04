//! Scripted sequences, each aimed at one way reconstruction fails.
//!
//! A scenario is a function from frame index to [`Beat`] — what the camera is
//! doing, what the lighting is doing, and how much unexplained content is on
//! screen. The harness turns that into rendered frames and lets the controller
//! react to what it measures.

/// What is happening in one frame.
#[derive(Clone, Copy, Debug)]
pub struct Beat {
    /// Camera velocity in output pixels per frame, at parallax 1.
    pub velocity: [f32; 2],
    /// Additive luminance. Negative darkens — a cut to a dimmer shot.
    pub flash: f32,
    /// Fraction of pixels carrying content the motion vectors do not describe.
    pub particles: f32,
    /// Cut to an unrelated shot this frame.
    pub cut: bool,
    /// Multiplier on native render cost — scene complexity.
    pub load: f32,
}

impl Default for Beat {
    fn default() -> Self {
        Beat {
            velocity: [0.0, 0.0],
            flash: 0.0,
            particles: 0.0,
            cut: false,
            load: 1.0,
        }
    }
}

/// A named sequence.
#[derive(Clone, Copy)]
pub struct Scenario {
    /// Identifier, as passed on the command line.
    pub name: &'static str,
    /// What it is for, and what the controller ought to do.
    pub blurb: &'static str,
    /// Length in rendered frames.
    pub frames: u32,
    /// Use the thin-geometry scene instead of open terrain.
    pub fence: bool,
    /// The script.
    pub beat: fn(u32) -> Beat,
}

fn calm(_i: u32) -> Beat {
    Beat {
        velocity: [0.6, 0.0],
        ..Default::default()
    }
}

fn whip(i: u32) -> Beat {
    // Calm, a hard whip pan, then calm again.
    let v = if (500..700).contains(&i) { 16.0 } else { 0.6 };
    Beat {
        velocity: [v, 0.0],
        ..Default::default()
    }
}

fn cuts(i: u32) -> Beat {
    // Two cuts, deliberately different.
    //
    // The first darkens: unexplained image *and* a luminance jump, which is
    // what Signals::is_scene_cut requires, so it takes the hard path — collapse
    // to the top spatial rung and a long cooldown.
    //
    // The second keeps the same exposure. The cut detector abstains, correctly:
    // requiring both signals is what stops a whip pan from being mistaken for a
    // cut. It is caught anyway, one layer down, by disocclusion saturating the
    // ordinary confidence test. The frame is still discarded; only the severity
    // of the response differs.
    let dark = i >= 500;
    Beat {
        velocity: [0.6, 0.0],
        flash: if dark { -0.28 } else { 0.0 },
        cut: i == 500 || i == 1000,
        ..Default::default()
    }
}

fn particles(i: u32) -> Beat {
    // A burst of effects the G-buffer says nothing about: sparks, alpha, muzzle
    // flash. Geometry is perfectly well behaved throughout, which is the point
    // — depth and motion vectors alone would report a completely healthy frame.
    let p = if (500..800).contains(&i) {
        let t = (i - 500) as f32 / 300.0;
        0.45 * (t * std::f32::consts::PI).sin()
    } else {
        0.0
    };
    Beat {
        velocity: [0.6, 0.0],
        particles: p,
        ..Default::default()
    }
}

fn fence_pan(_i: u32) -> Beat {
    Beat {
        velocity: [1.2, 0.0],
        ..Default::default()
    }
}

fn load_swing(i: u32) -> Beat {
    // Render cost swings without reconstruction getting any harder — walking
    // into a dense area and back out. Only the perf governor should move.
    let phase = (i as f32 / 300.0 * std::f32::consts::TAU).sin();
    Beat {
        velocity: [0.6, 0.0],
        load: 1.0 + 0.55 * phase,
        ..Default::default()
    }
}

/// Every scenario the simulator knows.
pub const CATALOGUE: &[Scenario] = &[
    Scenario {
        name: "calm-pan",
        blurb: "A slow pan over open terrain. Nothing is wrong. The controller \
                should climb to the rung that meets the budget and then stop \
                moving — a system that fidgets on easy content will fidget on \
                everything.",
        frames: 1200,
        fence: false,
        beat: calm,
    },
    Scenario {
        name: "whip-turn",
        blurb: "A fast whip pan between calm stretches. Parallax opens large \
                disocclusions, so temporal reconstruction has nothing to work \
                from. Expect generated frames to be discarded during the whip \
                and the ladder to climb back after it, slowly.",
        frames: 1500,
        fence: false,
        beat: whip,
    },
    Scenario {
        name: "scene-cut",
        blurb: "Two cuts. The first changes exposure and takes the hard path: \
                collapse to spatial, long cooldown. The second does not, so the \
                cut detector abstains and disocclusion catches it instead — same \
                discard, gentler response. Defense in depth, on purpose.",
        frames: 1500,
        fence: false,
        beat: cuts,
    },
    Scenario {
        name: "particle-burst",
        blurb: "Effects the G-buffer never mentions. Geometry stays perfectly \
                well behaved, so depth and motion vectors report a healthy \
                frame throughout; only photo-consistency notices anything. This \
                is the failure that makes shipped frame generation look broken.",
        frames: 1200,
        fence: false,
        beat: particles,
    },
    Scenario {
        name: "fence",
        blurb: "A pan across thin geometry — bars a couple of pixels wide, \
                clean at native and gone by half resolution. Nothing here is a \
                temporal problem; it is spatial. The ladder should cap low and \
                stay there, probing the next rung now and then and backing off \
                each time, rather than trading detail it cannot get back.",
        frames: 1200,
        fence: true,
        beat: fence_pan,
    },
    Scenario {
        name: "load-swing",
        blurb: "Render cost rises and falls while reconstruction stays easy. \
                Only the perf governor has any business reacting. If the quality \
                ceiling moves here, the two governors are coupled through \
                something they should not be.",
        frames: 1200,
        fence: false,
        beat: load_swing,
    },
];

/// Look up a scenario by name.
pub fn by_name(name: &str) -> Option<&'static Scenario> {
    CATALOGUE.iter().find(|s| s.name == name)
}
