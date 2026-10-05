// The JS advisor loop against a scripted client and the real wasm simulator.
// Needs the wasm build: cargo build -p frameboost-wasm --target wasm32-unknown-unknown --release

import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import Anthropic from "@anthropic-ai/sdk";

import { Advisor } from "../src/agent.js";
import { instantiate } from "../src/wasm.js";

const WASM = new URL("../../../target/wasm32-unknown-unknown/release/frameboost_wasm.wasm", import.meta.url);
const call = await instantiate(await WebAssembly.compile(readFileSync(WASM)));

// The Simulator interface, minus the worker pool.
const sim = {
  buildRequest: (settings, messages) => call({ op: "build_request", settings, messages }),
  execute: async (name, input) => call({ op: "execute", name, input }),
};

const settings = { model: "claude-opus-5-5", effort: "high", max_tokens: 64000 };

/** Plays back messages (or errors) and records each request. */
function scripted(replies) {
  const seen = [];
  return {
    seen,
    beta: {
      messages: {
        stream(params, opts) {
          seen.push(structuredClone({ params, signal: !!opts?.signal }));
          const next = replies.shift();
          const handlers = {};
          return {
            on(ev, fn) {
              handlers[ev] = fn;
              return this;
            },
            async finalMessage() {
              if (next instanceof Error) throw next;
              for (const b of next.content) {
                if (b.type === "text") handlers.text?.(b.text);
                if (b.type === "thinking" && b.thinking) handlers.thinking?.(b.thinking);
              }
              return next;
            },
          };
        },
      },
    },
  };
}

const msg = (stop_reason, content) => ({
  stop_reason,
  content,
  usage: { input_tokens: 10, output_tokens: 5, cache_read_input_tokens: 50 },
});

test("request comes from the Rust side, streamed with eager tool input and the fallback beta", async () => {
  const client = scripted([msg("end_turn", [{ type: "text", text: "hola" }])]);
  const a = new Advisor({ client, sim, settings });
  const out = await a.ask("¿qué tal?");
  assert.deepEqual(out, { type: "answered", text: "hola" });

  const { params } = client.seen[0];
  assert.equal(params.model, "claude-opus-5-5");
  assert.equal(params.max_tokens, 64000);
  assert.deepEqual(params.thinking, { type: "adaptive", display: "summarized" });
  assert.equal(params.output_config.effort, "high");
  assert.equal(params.fallbacks, "default");
  assert.deepEqual(params.betas, ["server-side-fallback-2026-07-01"]);
  assert.match(params.system, /FrameBoost advisor/);
  assert.ok(params.tools.length === 2 && params.tools.every((t) => t.eager_input_streaming === true));
  assert.equal(params.tool_choice, undefined);
});

test("parallel tool calls run on the simulator and come back in one message", async () => {
  const thinking = { type: "thinking", thinking: "comparar", signature: "sig" };
  const client = scripted([
    msg("tool_use", [
      thinking,
      { type: "tool_use", id: "a", name: "run_scenario", input: { scenario: "calm-pan", frames: 120 } },
      { type: "tool_use", id: "b", name: "run_scenario", input: { scenario: "calm-pan", frames: 120, quality: { max_level: 0 } } },
      { type: "tool_use", id: "c", name: "run_scenario", input: { scenario: "nope" } },
    ]),
    msg("end_turn", [{ type: "text", text: "Listo." }]),
  ]);
  const events = [];
  const a = new Advisor({ client, sim, settings });
  const out = await a.ask("compara", {
    toolCall: (id) => events.push(`call ${id}`),
    toolResult: (id, _n, ok) => events.push(`${ok ? "ok" : "err"} ${id}`),
  });
  assert.equal(out.type, "answered");
  assert.deepEqual(events.slice(0, 3), ["call a", "call b", "call c"]);
  assert.deepEqual(events.slice(3).sort(), ["err c", "ok a", "ok b"]);

  const msgs = client.seen[1].params.messages;
  assert.equal(msgs.length, 3);
  assert.deepEqual(msgs[1].content[0], thinking);
  const [ra, rb, rc] = msgs[2].content;
  assert.equal(JSON.parse(ra.content).result.boost_ratio > 1, true);
  assert.equal(JSON.parse(rb.content).result.max_level_reached, "native");
  assert.equal(rc.is_error, true);
  assert.match(rc.content, /calm-pan/);

  assert.equal(a.messages.length, 4);
  assert.equal(a.usage.cache_read_input_tokens, 100);
});

test("refusal, truncation and API errors roll back only the unfinished question", async () => {
  const client = scripted([
    msg("end_turn", [{ type: "text", text: "uno" }]),
    msg("tool_use", [{ type: "tool_use", id: "t", name: "list_scenarios", input: {} }]),
    { stop_reason: "refusal", content: [], stop_details: { type: "refusal", category: "cyber" } },
    msg("max_tokens", [{ type: "text", text: "a medias" }]),
    new Anthropic.RateLimitError(429, { error: { message: "slow down" } }, "slow down", new Headers()),
  ]);
  const a = new Advisor({ client, sim, settings });
  await a.ask("uno");
  const kept = structuredClone(a.messages);

  assert.deepEqual(await a.ask("dos"), { type: "refused", category: "cyber" });
  assert.deepEqual(a.messages, kept);
  assert.deepEqual(await a.ask("tres"), { type: "truncated" });
  assert.deepEqual(a.messages, kept);
  await assert.rejects(a.ask("cuatro"), Anthropic.RateLimitError);
  assert.deepEqual(a.messages, kept);
});

test("an unparseable tool input is retried, then gives up", async () => {
  const bad = () => new SyntaxError("Unexpected end of JSON input");
  const ok = scripted([bad(), msg("end_turn", [{ type: "text", text: "ok" }])]);
  assert.equal((await new Advisor({ client: ok, sim, settings }).ask("x")).type, "answered");

  const never = scripted([bad(), bad(), bad()]);
  await assert.rejects(new Advisor({ client: never, sim, settings }).ask("x"), SyntaxError);
});

test("the step limit stops a runaway loop", async () => {
  const loop = () => msg("tool_use", [{ type: "tool_use", id: "t", name: "list_scenarios", input: {} }]);
  const client = scripted([loop(), loop()]);
  const a = new Advisor({ client, sim, settings, maxSteps: 2 });
  assert.deepEqual(await a.ask("x"), { type: "step_limit" });
  assert.equal(a.messages.length, 0);
});
