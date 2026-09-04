//! CLI for the FrameBoost simulator.
//!
//! ```text
//! cargo run --release -p frameboost-sim
//! cargo run --release -p frameboost-sim -- --scenario whip-turn --native-ms 90
//! cargo run --release -p frameboost-sim -- --scenario fence --csv
//! ```

use frameboost_sim::harness::{Config, Judgement, RunReport};
use frameboost_sim::scenario::{by_name, Scenario, CATALOGUE};

const TIMELINE_COLUMNS: usize = 78;

fn main() {
    let args: Vec<String> = std::env::args().skip(1).collect();
    let opts = match Options::parse(&args) {
        Ok(o) => o,
        Err(msg) => {
            eprintln!("frameboost-sim: {msg}\n");
            eprintln!("{}", usage());
            std::process::exit(2);
        }
    };

    if opts.help {
        println!("{}", usage());
        return;
    }

    let scenarios: Vec<&Scenario> = match &opts.scenario {
        Some(name) => match by_name(name) {
            Some(s) => vec![s],
            None => {
                eprintln!("frameboost-sim: no scenario named '{name}'\n");
                eprintln!("{}", usage());
                std::process::exit(2);
            }
        },
        None => CATALOGUE.iter().collect(),
    };

    let cfg = opts.config();
    if !opts.csv {
        // CSV goes to a parser, so it gets nothing but CSV.
        println!(
            "FrameBoost simulator — {}x{} output, {:.0} fps target, {:.1} ms native frame\n",
            cfg.output_width,
            cfg.output_height,
            cfg.target_fps,
            cfg.native_frame_ns as f64 / 1e6
        );
    }

    let mut reports = Vec::new();
    for s in scenarios {
        let mut scenario = *s;
        if let Some(frames) = opts.frames {
            scenario.frames = frames;
        }
        let report = frameboost_sim::run(&scenario, &cfg);
        if opts.csv {
            print_csv(&report, reports.is_empty());
        } else {
            print_report(&scenario, &report);
        }
        reports.push(report);
    }

    if !opts.csv && reports.len() > 1 {
        print_comparison(&reports);
    }
}

struct Options {
    scenario: Option<String>,
    frames: Option<u32>,
    native_ms: f64,
    width: u32,
    height: u32,
    fps: f32,
    no_pipeline: bool,
    csv: bool,
    help: bool,
}

impl Options {
    fn parse(args: &[String]) -> Result<Options, String> {
        let d = Config::default();
        let mut o = Options {
            scenario: None,
            frames: None,
            native_ms: d.native_frame_ns as f64 / 1e6,
            width: d.output_width,
            height: d.output_height,
            fps: d.target_fps,
            no_pipeline: false,
            csv: false,
            help: false,
        };

        let mut it = args.iter().peekable();
        while let Some(arg) = it.next() {
            let mut value = || {
                it.next()
                    .cloned()
                    .ok_or_else(|| format!("{arg} needs a value"))
            };
            match arg.as_str() {
                "-h" | "--help" => o.help = true,
                "--scenario" | "-s" => o.scenario = Some(value()?),
                "--frames" => o.frames = Some(parse(&value()?, arg)?),
                "--native-ms" => o.native_ms = parse(&value()?, arg)?,
                "--width" => o.width = parse(&value()?, arg)?,
                "--height" => o.height = parse(&value()?, arg)?,
                "--fps" => o.fps = parse(&value()?, arg)?,
                "--no-pipeline" => o.no_pipeline = true,
                "--csv" => o.csv = true,
                other => return Err(format!("unknown argument '{other}'")),
            }
        }
        Ok(o)
    }

    fn config(&self) -> Config {
        Config {
            output_width: self.width.max(8),
            output_height: self.height.max(8),
            target_fps: self.fps.max(1.0),
            native_frame_ns: (self.native_ms.max(0.1) * 1e6) as u64,
            full_pipeline: !self.no_pipeline,
        }
    }
}

fn parse<T: std::str::FromStr>(s: &str, arg: &str) -> Result<T, String> {
    s.parse()
        .map_err(|_| format!("{arg}: could not parse '{s}'"))
}

fn usage() -> String {
    let mut s = String::from(
        "usage: frameboost-sim [options]\n\n\
         options:\n  \
           -s, --scenario <name>   run one scenario (default: all)\n  \
               --frames <n>        override the scenario's length\n  \
               --native-ms <ms>    cost of an unboosted frame (default 70)\n  \
               --width/--height    output resolution (default 192x108)\n  \
               --fps <n>           display refresh to target (default 60)\n  \
               --no-pipeline       skip the spatial passes; faster\n  \
               --csv               per-frame CSV instead of a report\n\n\
         scenarios:\n",
    );
    for sc in CATALOGUE {
        s.push_str(&format!("  {:<16}{}\n", sc.name, first_sentence(sc.blurb)));
    }
    s
}

fn first_sentence(text: &str) -> String {
    let compact = text.split_whitespace().collect::<Vec<_>>().join(" ");
    match compact.find(". ") {
        Some(i) => compact[..=i].to_string(),
        None => compact,
    }
}

fn print_report(scenario: &Scenario, r: &RunReport) {
    let t = &r.telemetry;
    println!("▸ {}", scenario.name);
    for line in wrap(
        &scenario
            .blurb
            .split_whitespace()
            .collect::<Vec<_>>()
            .join(" "),
        76,
    ) {
        println!("  {line}");
    }
    println!();

    // Two aligned strips: the rung over time, and where frames were rejected.
    let (rungs, drops) = timeline(r);
    let per_col = (r.frames.len() as f64 / TIMELINE_COLUMNS as f64)
        .ceil()
        .max(1.0);
    println!("  rung   {rungs}");
    println!("  reject {drops}");
    println!(
        "         (one column ≈ {:.0} frames; digit = rung index, ! = frame(s) rejected)",
        per_col
    );
    println!();

    let rejections: Vec<_> = r.rejections().collect();
    if !rejections.is_empty() {
        println!("  first rejections");
        println!("    frame  rung                    conf  cause");
        for f in rejections.iter().take(6) {
            println!(
                "    {:>5}  {:<22} {:>5.2}  {}{}",
                f.index,
                frameboost_core::level::rung(f.level).name,
                f.confidence,
                f.dominant.map(|d| d.name()).unwrap_or("-"),
                if f.scene_cut { " (scene cut)" } else { "" }
            );
        }
        if rejections.len() > 6 {
            println!("    … {} more", rejections.len() - 6);
        }
        println!();
    }

    let not_judged = r
        .frames
        .iter()
        .filter(|f| f.judgement == Judgement::NotJudged)
        .count();

    println!("  result");
    println!("    frames            {}", r.frames.len());
    println!(
        "    boost             {:.2}x presented per rendered",
        t.boost_ratio()
    );
    println!(
        "    cost              {:.2}x native  ({:.1} ms mean, {:.0} ms target)",
        t.mean_relative_cost(),
        r.mean_frame_ns() / 1e6,
        1000.0 / r.config.target_fps
    );
    println!(
        "    delivered         {:.1} fps to the display",
        r.effective_fps()
    );
    println!(
        "    rejected          {:.1}%{}",
        t.discard_rate() * 100.0,
        match t.worst_signal() {
            Some(s) => format!("  (mostly {})", s.name()),
            None => String::new(),
        }
    );
    if t.scene_cuts > 0 {
        println!("    scene cuts        {}", t.scene_cuts);
    }
    if not_judged > 0 {
        println!("    not judged        {not_judged}  (history reset on a resolution change)");
    }
    println!("    time on rungs     {}", rung_histogram(r));
    println!();
}

/// One column per bucket of frames: the modal rung, and whether anything was
/// rejected in that bucket.
fn timeline(r: &RunReport) -> (String, String) {
    let n = r.frames.len();
    if n == 0 {
        return (String::new(), String::new());
    }
    let cols = TIMELINE_COLUMNS.min(n);
    let per = (n as f64 / cols as f64).ceil() as usize;

    let mut rungs = String::with_capacity(cols);
    let mut drops = String::with_capacity(cols);
    for c in 0..cols {
        let lo = c * per;
        let hi = ((c + 1) * per).min(n);
        if lo >= hi {
            break;
        }
        let slice = &r.frames[lo..hi];

        let mut counts = [0u32; 8];
        let mut rejected = false;
        for f in slice {
            counts[(f.level as usize).min(7)] += 1;
            if matches!(f.judgement, Judgement::Discarded | Judgement::Degraded) {
                rejected = true;
            }
        }
        let modal = counts
            .iter()
            .enumerate()
            .max_by_key(|(_, c)| **c)
            .map(|(i, _)| i)
            .unwrap_or(0);

        rungs.push(char::from_digit(modal as u32, 10).unwrap_or('?'));
        drops.push(if rejected { '!' } else { '·' });
    }
    (rungs, drops)
}

fn rung_histogram(r: &RunReport) -> String {
    let total = r.frames.len().max(1) as f32;
    let mut parts = Vec::new();
    for (level, count) in r.telemetry.frames_at_level.iter().enumerate() {
        if *count == 0 {
            continue;
        }
        parts.push(format!(
            "{} {:.0}%",
            frameboost_core::level::rung(level as u8).name,
            *count as f32 / total * 100.0
        ));
    }
    if parts.is_empty() {
        "—".to_string()
    } else {
        parts.join(" · ")
    }
}

fn print_comparison(reports: &[RunReport]) {
    println!("summary");
    println!(
        "  {:<16}{:>7}{:>9}{:>10}{:>11}",
        "scenario", "boost", "cost", "rejected", "delivered"
    );
    for r in reports {
        println!(
            "  {:<16}{:>6.2}x{:>8.2}x{:>9.1}%{:>8.1} fps",
            r.scenario,
            r.telemetry.boost_ratio(),
            r.telemetry.mean_relative_cost(),
            r.telemetry.discard_rate() * 100.0,
            r.effective_fps()
        );
    }
    println!();
}

fn print_csv(r: &RunReport, with_header: bool) {
    if with_header {
        println!(
            "scenario,frame,level,ceiling,confidence,judgement,dominant,scene_cut,\
         disocclusion,motion_residual,luma_shift,camera_motion,depth_complexity,\
         pacing_instability,frame_ms,presented"
        );
    }
    for f in &r.frames {
        println!(
            "{},{},{},{},{:.4},{:?},{},{},{:.4},{:.4},{:.4},{:.4},{:.4},{:.4},{:.3},{}",
            r.scenario,
            f.index,
            f.level,
            f.ceiling,
            f.confidence,
            f.judgement,
            f.dominant.map(|d| d.name()).unwrap_or(""),
            f.scene_cut,
            f.signals.disocclusion,
            f.signals.motion_residual,
            f.signals.luma_shift,
            f.signals.camera_motion,
            f.signals.depth_complexity,
            f.signals.pacing_instability,
            f.frame_ns as f64 / 1e6,
            f.presented
        );
    }
}

fn wrap(text: &str, width: usize) -> Vec<String> {
    let mut lines = Vec::new();
    let mut line = String::new();
    for word in text.split_whitespace() {
        if !line.is_empty() && line.len() + 1 + word.len() > width {
            lines.push(std::mem::take(&mut line));
        }
        if !line.is_empty() {
            line.push(' ');
        }
        line.push_str(word);
    }
    if !line.is_empty() {
        lines.push(line);
    }
    lines
}
