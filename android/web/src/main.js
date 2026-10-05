import Anthropic from "@anthropic-ai/sdk";
import { marked } from "marked";
import DOMPurify from "dompurify";

import { Advisor } from "./agent.js";
import { Simulator } from "./sim.js";
import { mountLab } from "./lab/lab.js";

const $ = (id) => document.getElementById(id);
const log = $("log");
const input = $("input");
const send = $("send");

// ---- settings: per-device, so browser storage is the right home ----

const DEFAULTS = { apiKey: "", model: "claude-opus-5-5", effort: "high" };
const MODEL_NAMES = {
  "claude-opus-5-5": "Claude Opus 5.5",
  "claude-sonnet-5-5": "Claude Sonnet 5.5",
  "claude-haiku-4-5": "Claude Haiku 4.5",
};

function loadSettings() {
  try {
    return { ...DEFAULTS, ...JSON.parse(localStorage.getItem("settings") || "{}") };
  } catch {
    return { ...DEFAULTS };
  }
}

function saveSettings(s) {
  try {
    localStorage.setItem("settings", JSON.stringify(s));
  } catch {
    // Storage unavailable: the settings last for this session only.
  }
}

let settings = loadSettings();
let sim = null;
let advisor = null;
let busy = null; // AbortController while a question is running

function requestSettings() {
  // Streaming, so the output ceiling can be generous; Haiku 4.5 tops out at 64K.
  return { model: settings.model, effort: settings.effort, max_tokens: 64000 };
}

function makeClient() {
  return new Anthropic({
    apiKey: settings.apiKey,
    // The key belongs to the person holding the phone and never leaves it
    // except to api.anthropic.com.
    dangerouslyAllowBrowser: true,
    maxRetries: 3,
  });
}

let tab = "lab";

function refreshHeader() {
  if (tab === "lab") {
    $("model-label").textContent = "Simulador en tu teléfono · sin conexión";
    return;
  }
  const effort = settings.model.startsWith("claude-haiku") ? "" : ` · esfuerzo ${$("effort").querySelector(`[value="${settings.effort}"]`)?.textContent.toLowerCase()}`;
  $("model-label").textContent = `${MODEL_NAMES[settings.model] ?? settings.model}${effort}`;
}

function chatStatus() {
  const status = $("sim-status");
  if (!status || !sim) return;
  status.textContent = settings.apiKey
    ? "Listo."
    : "Para usar el chat, añade tu clave en Ajustes (el engranaje de arriba).";
}

function showTab(name) {
  tab = name;
  for (const b of document.querySelectorAll(".tabs button")) {
    const on = b.dataset.tab === name;
    b.classList.toggle("on", on);
    b.setAttribute("aria-selected", String(on));
  }
  $("lab").hidden = name !== "lab";
  $("chat").hidden = name !== "chat";
  $("new-chat").hidden = name !== "chat";
  refreshHeader();
  try {
    localStorage.setItem("tab", name);
  } catch {
    // Not remembered; the lab is the default anyway.
  }
}

document.querySelector(".tabs").addEventListener("click", (e) => {
  const b = e.target.closest("button[data-tab]");
  if (b) showTab(b.dataset.tab);
});

// ---- rendering ----

marked.use({ gfm: true, breaks: false });

function renderMarkdown(el, text) {
  // No images: nothing in an answer gets to make the phone fetch a URL.
  el.innerHTML = DOMPurify.sanitize(marked.parse(text), { FORBID_TAGS: ["img", "style", "form", "input"] });
  for (const a of el.querySelectorAll("a")) a.target = "_blank";
}

function scrollDown(force) {
  const near = log.scrollHeight - log.scrollTop - log.clientHeight < 160;
  if (force || near) log.scrollTop = log.scrollHeight;
}

function el(tag, cls, text) {
  const e = document.createElement(tag);
  if (cls) e.className = cls;
  if (text != null) e.textContent = text;
  return e;
}

function addUser(text) {
  $("welcome")?.remove();
  const m = el("div", "msg user");
  m.append(el("div", "bubble", text));
  log.append(m);
  scrollDown(true);
}

function describeArgs(name, input) {
  if (name !== "run_scenario") return "";
  const parts = [input?.scenario];
  if (input?.native_ms != null) parts.push(`native ${input.native_ms} ms`);
  if (input?.target_fps != null) parts.push(`${input.target_fps} fps`);
  if (input?.frames != null) parts.push(`${input.frames} frames`);
  for (const [k, v] of Object.entries(input?.quality ?? {})) parts.push(`${k} ${v}`);
  return parts.filter(Boolean).join(" · ");
}

function describeResult(name, result) {
  const r = result?.result;
  if (name !== "run_scenario" || !r) return "";
  return `boost ${r.boost_ratio.toFixed(2)}× · coste ${r.relative_cost.toFixed(2)}× · rechazo ${r.rejected_pct.toFixed(1)}% · ${r.delivered_fps.toFixed(1)} fps`;
}

/** One assistant reply: reasoning, tool calls and answer, filled in as they stream. */
class Reply {
  constructor() {
    this.root = el("div", "msg ai");
    this.thinking = null;
    this.thinkingText = "";
    this.tools = null;
    this.answer = null;
    this.text = "";
    this.status = el("div", "status dots", "Pensando");
    this.root.append(this.status);
    this.toolEls = new Map();
    this.frame = 0;
    log.append(this.root);
    scrollDown(true);
  }

  #before(node) {
    this.root.insertBefore(node, this.status.isConnected ? this.status : null);
  }

  step() {
    // A new model turn starts a fresh block of text; earlier text stays put.
    if (this.answer) renderMarkdown(this.answer, this.text);
    this.answer = null;
  }

  onThinking(delta) {
    if (!this.thinking) {
      this.thinking = el("details", "thinking");
      this.thinking.append(el("summary", null, "Razonamiento"), el("div", "body"));
      this.#before(this.thinking);
    }
    this.thinkingText += delta;
    this.thinking.querySelector(".body").textContent = this.thinkingText;
  }

  #last() {
    return this.status.previousSibling;
  }

  onText(delta) {
    if (!this.answer || this.#last() !== this.answer) {
      this.answer = el("div", "answer");
      this.text = "";
      this.#before(this.answer);
    }
    this.text += delta;
    if (!this.frame) {
      this.frame = requestAnimationFrame(() => {
        this.frame = 0;
        renderMarkdown(this.answer, this.text);
        scrollDown();
      });
    }
    this.status.textContent = "Escribiendo";
  }

  onToolCall(id, name, input) {
    if (!this.tools || this.#last() !== this.tools) {
      this.tools = el("div", "tools");
      this.#before(this.tools);
    }
    const t = el("div", "tool run");
    const head = el("div", "head");
    head.append(el("span", "name", name === "run_scenario" ? "Simulación" : "Catálogo"), el("span", "state", "corriendo"));
    t.append(head);
    const args = describeArgs(name, input);
    if (args) t.append(el("div", "args", args));
    this.tools.append(t);
    this.toolEls.set(id, t);
    this.status.textContent = "Midiendo en el simulador";
    scrollDown();
  }

  onToolResult(id, name, ok, ms, result) {
    const t = this.toolEls.get(id);
    if (!t) return;
    t.className = `tool ${ok ? "ok" : "err"}`;
    t.querySelector(".state").textContent = ok ? `✓ ${(ms / 1000).toFixed(1)} s` : "✗ error";
    const res = describeResult(name, result);
    if (res) t.append(el("div", "res", res));
    this.status.textContent = "Pensando";
  }

  finish(message, isError) {
    if (this.frame) cancelAnimationFrame(this.frame);
    if (this.answer) renderMarkdown(this.answer, this.text);
    if (message) {
      this.status.className = `status${isError ? " err" : ""}`;
      this.status.textContent = message;
    } else {
      this.status.remove();
    }
    scrollDown();
  }
}

function explainError(err) {
  if (err instanceof Anthropic.AuthenticationError) return "La clave de API no es válida. Revísala en Ajustes.";
  if (err instanceof Anthropic.PermissionDeniedError) return "Esta clave no tiene acceso a ese modelo.";
  if (err instanceof Anthropic.RateLimitError) return "Límite de uso alcanzado. Espera un momento y vuelve a intentarlo.";
  if (err instanceof Anthropic.APIConnectionError) return "Sin conexión con api.anthropic.com. Revisa tu red.";
  if (err instanceof Anthropic.APIError) return `Error de la API (${err.status ?? "?"}): ${err.message}`;
  return `Error: ${err.message ?? err}`;
}

// ---- asking ----

async function ask(question) {
  if (!sim) return;
  if (!settings.apiKey) {
    openSettings();
    return;
  }
  addUser(question);
  const reply = new Reply();
  busy = new AbortController();
  setBusy(true);

  advisor.client = makeClient();
  advisor.settings = requestSettings();
  try {
    const out = await advisor.ask(
      question,
      {
        step: () => reply.step(),
        thinking: (d) => reply.onThinking(d),
        text: (d) => reply.onText(d),
        toolCall: (...a) => reply.onToolCall(...a),
        toolResult: (...a) => reply.onToolResult(...a),
      },
      busy.signal,
    );
    switch (out.type) {
      case "answered":
        reply.finish();
        break;
      case "refused":
        reply.finish(`Claude no puede responder a esto${out.category ? ` (${out.category})` : ""}.`, true);
        break;
      case "truncated":
        reply.finish("La respuesta se cortó por longitud. Pide algo más concreto.", true);
        break;
      default:
        reply.finish("Se alcanzó el límite de pasos sin una respuesta final.", true);
    }
  } catch (err) {
    reply.finish(busy.signal.aborted ? "Detenido. Esta pregunta no queda en la conversación." : explainError(err), !busy.signal.aborted);
  } finally {
    busy = null;
    setBusy(false);
  }
}

function setBusy(on) {
  send.classList.toggle("busy", on);
  send.setAttribute("aria-label", on ? "Detener" : "Enviar");
  for (const c of document.querySelectorAll(".chip")) c.disabled = on;
}

$("composer").addEventListener("submit", (e) => {
  e.preventDefault();
  if (busy) {
    busy.abort();
    return;
  }
  const q = input.value.trim();
  if (!q) return;
  input.value = "";
  autosize();
  ask(q);
});

input.addEventListener("keydown", (e) => {
  // Enter sends on a hardware keyboard; the soft keyboard's action key does too.
  if (e.key === "Enter" && !e.shiftKey && !e.isComposing) {
    e.preventDefault();
    $("composer").requestSubmit();
  }
});

function autosize() {
  input.style.height = "auto";
  input.style.height = `${Math.min(input.scrollHeight, 160)}px`;
}
input.addEventListener("input", autosize);

$("suggestions").addEventListener("click", (e) => {
  const chip = e.target.closest(".chip");
  if (chip && !busy) ask(chip.textContent);
});

$("new-chat").addEventListener("click", () => {
  if (busy) busy.abort();
  advisor?.reset();
  log.replaceChildren();
  const w = el("section", "welcome");
  w.id = "welcome";
  w.append(el("h2", null, "Nueva conversación"), el("p", null, "Pregunta lo que quieras sobre el ajuste de FrameBoost."));
  log.append(w);
});

// ---- settings sheet ----

function openSettings() {
  $("api-key").value = settings.apiKey;
  $("model").value = settings.model;
  $("effort").value = settings.effort;
  syncEffortField();
  const u = advisor?.usage;
  $("usage").textContent = u && (u.input_tokens || u.output_tokens)
    ? `Esta sesión: ${u.input_tokens + u.cache_read_input_tokens + u.cache_creation_input_tokens} tokens de entrada (${u.cache_read_input_tokens} desde caché), ${u.output_tokens} de salida.`
    : "";
  $("settings").showModal();
}

function syncEffortField() {
  const legacy = $("model").value.startsWith("claude-haiku");
  $("effort").disabled = legacy;
  $("effort-field").classList.toggle("disabled", legacy);
}

$("model").addEventListener("change", syncEffortField);
$("open-settings").addEventListener("click", openSettings);
$("toggle-key").addEventListener("click", () => {
  const k = $("api-key");
  k.type = k.type === "password" ? "text" : "password";
  $("toggle-key").textContent = k.type === "password" ? "Ver" : "Ocultar";
});

$("settings").addEventListener("close", () => {
  if ($("settings").returnValue !== "save") return;
  settings = {
    apiKey: $("api-key").value.trim(),
    model: $("model").value,
    effort: $("effort").value,
  };
  saveSettings(settings);
  refreshHeader();
  chatStatus();
});

// ---- boot ----

async function boot() {
  let saved = "lab";
  try {
    saved = localStorage.getItem("tab") === "chat" ? "chat" : "lab";
  } catch {
    // Default tab.
  }
  showTab(saved);
  try {
    const [wasm, worker] = await Promise.all([
      fetch("frameboost.wasm").then((r) => r.arrayBuffer()),
      fetch("worker.js").then((r) => r.text()),
    ]);
    sim = await Simulator.load(wasm, worker);
    advisor = new Advisor({ client: makeClient(), sim, settings: requestSettings() });
    $("lab-loading").remove();
    mountLab($("lab"), sim);
    chatStatus();
  } catch (err) {
    for (const id of ["lab-loading", "sim-status"]) {
      const status = $(id);
      if (!status) continue;
      status.textContent = `No se pudo cargar el simulador: ${err.message}`;
      status.style.color = "var(--bad)";
    }
  }
}

boot();
