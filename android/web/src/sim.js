// The simulator: one instance on the UI thread for cheap calls (prompt, tool
// definitions, request shape) and a pool of workers for scenario runs.

import { instantiate } from "./wasm.js";

export class Simulator {
  static async load(wasmBytes, workerSource, size) {
    const module = await WebAssembly.compile(wasmBytes);
    const sim = new Simulator(await instantiate(module));
    const url = URL.createObjectURL(new Blob([workerSource], { type: "text/javascript" }));
    const n = Math.max(1, Math.min(size ?? 4, navigator.hardwareConcurrency || 2));
    await Promise.all(Array.from({ length: n }, () => sim.#spawn(url, module)));
    return sim;
  }

  #call;
  #idle = [];
  #queue = [];
  #pending = new Map();
  #nextId = 0;

  constructor(call) {
    this.#call = call;
  }

  systemPrompt() {
    return this.#call({ op: "system_prompt" });
  }

  buildRequest(settings, messages) {
    return this.#call({ op: "build_request", settings, messages });
  }

  /** Resolves to `{result}` or `{tool_error}`; never rejects for a tool failure. */
  execute(name, input) {
    return new Promise((resolve) => {
      this.#queue.push({ id: this.#nextId++, name, input, resolve });
      this.#pump();
    });
  }

  #spawn(url, module) {
    return new Promise((resolve) => {
      const w = new Worker(url, { type: "classic" });
      w.onmessage = ({ data }) => {
        if (data.type === "ready") {
          this.#idle.push(w);
          resolve();
          return;
        }
        this.#pending.get(data.id)?.(data.out);
        this.#pending.delete(data.id);
        this.#idle.push(w);
        this.#pump();
      };
      w.postMessage({ type: "init", module });
    });
  }

  #pump() {
    while (this.#idle.length && this.#queue.length) {
      const w = this.#idle.pop();
      const job = this.#queue.shift();
      this.#pending.set(job.id, job.resolve);
      w.postMessage({ id: job.id, name: job.name, input: job.input });
    }
  }
}
