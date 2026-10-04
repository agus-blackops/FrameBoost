//! CLI for the FrameBoost advisor.
//!
//! ```text
//! frameboost-advisor "Tune the quality governor for whip-turn without raising rejections"
//! frameboost-advisor --model claude-sonnet-5-5 --effort medium
//! ```

use std::io::{self, BufRead, Write};

use frameboost_ai::{Advisor, Event, HttpTransport, Outcome, Settings, Transport};

fn main() {
    let args: Vec<String> = std::env::args().skip(1).collect();
    let opts = match Options::parse(&args) {
        Ok(o) => o,
        Err(msg) => {
            eprintln!("frameboost-advisor: {msg}\n\n{USAGE}");
            std::process::exit(2);
        }
    };
    if opts.help {
        println!("{USAGE}");
        return;
    }

    let transport = match HttpTransport::from_env() {
        Ok(t) => t,
        Err(e) => {
            eprintln!("frameboost-advisor: {e}");
            std::process::exit(2);
        }
    };
    let mut advisor = Advisor::new(transport, opts.settings).with_max_steps(opts.max_steps);

    let code = if opts.prompt.is_empty() {
        repl(&mut advisor, opts.verbose)
    } else {
        ask(&mut advisor, &opts.prompt.join(" "), opts.verbose)
    };
    if opts.verbose {
        let u = advisor.usage();
        eprintln!(
            "\n[tokens] input {} · cache read {} · cache write {} · output {}",
            u.input_tokens,
            u.cache_read_input_tokens,
            u.cache_creation_input_tokens,
            u.output_tokens
        );
    }
    std::process::exit(code);
}

fn repl<T: Transport>(advisor: &mut Advisor<T>, verbose: bool) -> i32 {
    eprintln!(
        "FrameBoost advisor ({}, effort {}). Ask about tuning; /reset clears, /exit quits.",
        advisor.settings().model,
        advisor.settings().effort
    );
    let stdin = io::stdin();
    loop {
        eprint!("\n> ");
        let _ = io::stderr().flush();
        let mut line = String::new();
        match stdin.lock().read_line(&mut line) {
            Ok(0) | Err(_) => return 0,
            Ok(_) => {}
        }
        match line.trim() {
            "" => continue,
            "/exit" | "/quit" => return 0,
            "/reset" => {
                advisor.reset();
                eprintln!("(conversation cleared)");
            }
            q => {
                ask(advisor, q, verbose);
            }
        }
    }
}

fn ask<T: Transport>(advisor: &mut Advisor<T>, question: &str, verbose: bool) -> i32 {
    let mut on_event = |e: Event<'_>| match e {
        Event::Thinking(t) if verbose => eprintln!("\x1b[2m{t}\x1b[0m"),
        Event::Thinking(_) => {}
        Event::Text(t) => {
            print!("{t}");
            let _ = io::stdout().flush();
        }
        Event::ToolCall { name, input } => eprintln!("\x1b[2m  ⚙ {name} {input}\x1b[0m"),
        Event::ToolResult { name, ok: false } => eprintln!("\x1b[2m  ✗ {name} failed\x1b[0m"),
        Event::ToolResult { .. } => {}
    };
    match advisor.ask(question, &mut on_event) {
        Ok(Outcome::Answered(_)) => {
            println!();
            0
        }
        Ok(Outcome::Refused { category }) => {
            eprintln!(
                "\nClaude declined this request{}.",
                category
                    .map(|c| format!(" (category: {c})"))
                    .unwrap_or_default()
            );
            1
        }
        Ok(Outcome::Truncated) => {
            eprintln!("\nThe response hit max_tokens; try again with a larger --max-tokens.");
            1
        }
        Ok(Outcome::StepLimit) => {
            eprintln!("\nStopped after the step limit without a final answer; raise --max-steps.");
            1
        }
        Err(e) => {
            eprintln!("\nframeboost-advisor: {e}");
            1
        }
    }
}

const USAGE: &str = "\
usage: frameboost-advisor [options] [question...]

Asks Claude to tune or explain the FrameBoost controller, using the simulator
as evidence. With no question, starts an interactive session.

options:
  --model <id>        Claude model (default claude-opus-5-5)
  --effort <level>    low | medium | high | xhigh | max (default high)
  --max-tokens <n>    output ceiling per model turn (default 16000)
  --max-steps <n>     model turns allowed per question (default 30)
  -v, --verbose       show reasoning summaries and token usage
  -h, --help

environment:
  ANTHROPIC_API_KEY   required (or ANTHROPIC_AUTH_TOKEN)
  ANTHROPIC_BASE_URL  optional";

struct Options {
    settings: Settings,
    max_steps: usize,
    verbose: bool,
    help: bool,
    prompt: Vec<String>,
}

impl Options {
    fn parse(args: &[String]) -> Result<Options, String> {
        let mut o = Options {
            settings: Settings::default(),
            max_steps: frameboost_ai::agent::DEFAULT_MAX_STEPS,
            verbose: false,
            help: false,
            prompt: Vec::new(),
        };
        let mut it = args.iter();
        while let Some(arg) = it.next() {
            let mut value = || {
                it.next()
                    .cloned()
                    .ok_or_else(|| format!("{arg} needs a value"))
            };
            match arg.as_str() {
                "-h" | "--help" => o.help = true,
                "-v" | "--verbose" => o.verbose = true,
                "--model" => o.settings.model = value()?,
                "--effort" => {
                    let v = value()?;
                    if !["low", "medium", "high", "xhigh", "max"].contains(&v.as_str()) {
                        return Err(format!("--effort: unknown level '{v}'"));
                    }
                    o.settings.effort = v;
                }
                "--max-tokens" => o.settings.max_tokens = parse(&value()?, arg)?,
                "--max-steps" => o.max_steps = parse(&value()?, arg)?,
                s if s.starts_with('-') && s.len() > 1 => {
                    return Err(format!("unknown argument '{s}'"))
                }
                _ => o.prompt.push(arg.clone()),
            }
        }
        Ok(o)
    }
}

fn parse<T: std::str::FromStr>(s: &str, arg: &str) -> Result<T, String> {
    s.parse()
        .map_err(|_| format!("{arg}: could not parse '{s}'"))
}
