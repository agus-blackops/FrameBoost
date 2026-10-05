// The tool-use loop, streamed. Mirrors crates/frameboost-ai/src/agent.rs: the
// request shape, the system prompt and the tools all come from the Rust side
// (via wasm), so only the conversation bookkeeping lives here.
//
// History is append-only — thinking blocks are tied to the exact conversation
// that produced them — and a question that does not finish cleanly is cut off
// where it started, leaving earlier turns intact.

import Anthropic from "@anthropic-ai/sdk";

export const MAX_STEPS = 30;
const MAX_JSON_RETRIES = 2;

export class Advisor {
  /**
   * @param {object} o
   * @param {{beta: {messages: {stream: Function}}}} o.client an Anthropic client
   * @param {import("./sim.js").Simulator} o.sim
   * @param {{model: string, effort: string, max_tokens: number}} o.settings
   */
  constructor({ client, sim, settings, maxSteps = MAX_STEPS }) {
    this.client = client;
    this.sim = sim;
    this.settings = settings;
    this.maxSteps = maxSteps;
    this.messages = [];
    this.usage = { input_tokens: 0, output_tokens: 0, cache_read_input_tokens: 0, cache_creation_input_tokens: 0 };
  }

  reset() {
    this.messages = [];
  }

  /**
   * Ask one question; resolves to `{type: "answered", text}`, `{type: "refused",
   * category}`, `{type: "truncated"}` or `{type: "step_limit"}`. Rejects on API
   * errors and on abort.
   *
   * `on` receives: thinking(delta), text(delta), step(), toolCall(id, name,
   * input), toolResult(id, name, ok, ms).
   */
  async ask(question, on = {}, signal) {
    const checkpoint = this.messages.length;
    try {
      const outcome = await this.#run(question, on, signal);
      if (outcome.type !== "answered") this.messages.length = checkpoint;
      return outcome;
    } catch (err) {
      // Truncating a suffix never edits what came before.
      this.messages.length = checkpoint;
      throw err;
    }
  }

  async #run(question, on, signal) {
    this.messages.push({ role: "user", content: question });
    let jsonRetries = 0;

    for (let step = 0; step < this.maxSteps; step++) {
      on.step?.();
      const { body, betas } = this.sim.buildRequest(this.settings, this.messages);
      // Streamed, so tool inputs stream too. Every input is validated by the
      // Rust side before anything runs.
      body.tools = body.tools.map((t) => ({ ...t, eager_input_streaming: true }));

      let message;
      try {
        const stream = this.client.beta.messages.stream({ ...body, betas }, { signal });
        stream.on("thinking", (delta) => on.thinking?.(delta));
        stream.on("text", (delta) => on.text?.(delta));
        message = await stream.finalMessage();
        jsonRetries = 0;
      } catch (err) {
        // Only a tool input that could not be parsed at all is retried.
        if (err instanceof Anthropic.APIError || signal?.aborted || jsonRetries++ >= MAX_JSON_RETRIES) throw err;
        continue;
      }
      this.#addUsage(message.usage);

      if (message.stop_reason === "refusal") {
        return { type: "refused", category: message.stop_details?.category ?? null };
      }
      if (message.stop_reason === "max_tokens") return { type: "truncated" };

      const content = message.content;
      // The whole content array goes back verbatim, thinking included.
      if (content.length) this.messages.push({ role: "assistant", content });

      if (message.stop_reason === "tool_use") {
        const calls = content.filter((b) => b.type === "tool_use");
        const results = await Promise.all(calls.map((c) => this.#runTool(c, on)));
        // All results in one message.
        this.messages.push({ role: "user", content: results });
        continue;
      }
      // The server paused a long turn; sending the history back continues it.
      if (message.stop_reason === "pause_turn") continue;

      const text = content.filter((b) => b.type === "text").map((b) => b.text).join("");
      return { type: "answered", text };
    }
    return { type: "step_limit" };
  }

  async #runTool(call, on) {
    on.toolCall?.(call.id, call.name, call.input);
    const t0 = performance.now();
    const out = await this.sim.execute(call.name, call.input);
    const ok = "result" in out;
    on.toolResult?.(call.id, call.name, ok, performance.now() - t0, out.result);
    return ok
      ? { type: "tool_result", tool_use_id: call.id, content: JSON.stringify(out.result) }
      : { type: "tool_result", tool_use_id: call.id, content: out.tool_error, is_error: true };
  }

  #addUsage(u = {}) {
    for (const k of Object.keys(this.usage)) this.usage[k] += u[k] ?? 0;
  }
}
