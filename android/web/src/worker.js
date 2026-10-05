// Runs simulator tool calls off the UI thread. A run takes a second or more.

import { instantiate } from "./wasm.js";

let module = null;
let call = null;

self.onmessage = async ({ data }) => {
  if (data.type === "init") {
    module = data.module;
    call = await instantiate(module);
    self.postMessage({ type: "ready" });
    return;
  }
  const { id, name, input } = data;
  try {
    const out = call({ op: "execute", name, input });
    self.postMessage({ id, out });
  } catch (err) {
    // A trap leaves the instance unusable; start a fresh one.
    call = await instantiate(module);
    self.postMessage({ id, out: { tool_error: `the simulator crashed: ${err.message}` } });
  }
};
