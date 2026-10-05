// The rung-over-time chart: one series, stepped, on the ladder's 0..6 scale,
// with rejections in their own strip underneath so they never read as part of
// the line. Touch or hover snaps a crosshair to a column and reads it out.

import { RUNG_SHORT } from "./catalog.js";

const NS = "http://www.w3.org/2000/svg";
const W = 340;
const PLOT_H = 126;
const LEFT = 58;
const RIGHT = 8;
const TOP = 8;
const STRIP = 18;
const AXIS = 18;
const H = TOP + PLOT_H + 8 + STRIP + AXIS;

function svg(tag, attrs, parent) {
  const e = document.createElementNS(NS, tag);
  for (const [k, v] of Object.entries(attrs)) e.setAttribute(k, v);
  parent?.append(e);
  return e;
}

/** @param {{rung: string, reject: string, frames_per_column: number}} timeline */
export function timelineChart(timeline, totalFrames) {
  const rungs = [...timeline.rung].map(Number);
  const rejects = [...timeline.reject].map((c) => c === "!");
  const n = rungs.length;
  const plotW = W - LEFT - RIGHT;
  const x = (i) => LEFT + (i / n) * plotW;
  const y = (r) => TOP + PLOT_H - (r / 6) * PLOT_H;

  const wrap = document.createElement("figure");
  wrap.className = "chart";
  const root = svg("svg", { viewBox: `0 0 ${W} ${H}`, role: "img", "aria-label": "Rung a lo largo del tiempo" }, wrap);

  // Recessive grid and rung labels.
  for (let r = 0; r <= 6; r++) {
    svg("line", { x1: LEFT, x2: W - RIGHT, y1: y(r), y2: y(r), class: "grid" }, root);
    const t = svg("text", { x: LEFT - 6, y: y(r) + 3.5, class: "tick", "text-anchor": "end" }, root);
    t.textContent = RUNG_SHORT[r];
  }

  // The series: a stepped line.
  let d = `M${x(0)},${y(rungs[0])}`;
  for (let i = 0; i < n; i++) {
    if (i > 0 && rungs[i] !== rungs[i - 1]) d += `V${y(rungs[i])}`;
    d += `H${x(i + 1)}`;
  }
  svg("path", { d, class: "series" }, root);

  // Rejection strip: a glyph, not just a color.
  const stripY = TOP + PLOT_H + 8;
  const label = svg("text", { x: LEFT - 6, y: stripY + 12, class: "tick", "text-anchor": "end" }, root);
  label.textContent = "rechazos";
  svg("line", { x1: LEFT, x2: W - RIGHT, y1: stripY + STRIP / 2, y2: stripY + STRIP / 2, class: "grid" }, root);
  rejects.forEach((on, i) => {
    if (!on) return;
    const cx = (x(i) + x(i + 1)) / 2;
    svg("rect", { x: cx - 5, y: stripY + 1, width: 10, height: STRIP - 2, rx: 3, class: "reject" }, root);
    const bang = svg("text", { x: cx, y: stripY + 13, class: "reject-mark", "text-anchor": "middle" }, root);
    bang.textContent = "!";
  });

  // Frame axis.
  const axisY = stripY + STRIP + 13;
  for (const [pos, anchor, value] of [[LEFT, "start", 0], [W - RIGHT, "end", totalFrames]]) {
    const t = svg("text", { x: pos, y: axisY, class: "tick", "text-anchor": anchor }, root);
    t.textContent = value === 0 ? "frame 0" : `${value}`;
  }

  // Crosshair + readout.
  const cross = svg("line", { y1: TOP, y2: stripY + STRIP, class: "cross", visibility: "hidden" }, root);
  const dot = svg("circle", { r: 4, class: "dot", visibility: "hidden" }, root);
  const tip = document.createElement("div");
  tip.className = "tip";
  tip.hidden = true;
  wrap.append(tip);

  const per = timeline.frames_per_column;
  function show(i) {
    const cx = (x(i) + x(i + 1)) / 2;
    cross.setAttribute("x1", cx);
    cross.setAttribute("x2", cx);
    dot.setAttribute("cx", cx);
    dot.setAttribute("cy", y(rungs[i]));
    cross.setAttribute("visibility", "visible");
    dot.setAttribute("visibility", "visible");
    const from = i * per;
    const to = Math.min(totalFrames, (i + 1) * per) - 1;
    tip.replaceChildren();
    const v = document.createElement("strong");
    v.textContent = RUNG_SHORT[rungs[i]];
    const meta = document.createElement("span");
    meta.textContent = `frames ${from}–${to}${rejects[i] ? " · ! rechazo" : ""}`;
    tip.append(v, meta);
    tip.hidden = false;
    const left = (cx / W) * 100;
    tip.style.left = `${Math.min(Math.max(left, 18), 82)}%`;
  }
  function hide() {
    cross.setAttribute("visibility", "hidden");
    dot.setAttribute("visibility", "hidden");
    tip.hidden = true;
  }
  root.addEventListener("pointermove", (e) => {
    const box = root.getBoundingClientRect();
    const px = ((e.clientX - box.left) / box.width) * W;
    const i = Math.floor(((px - LEFT) / plotW) * n);
    if (i >= 0 && i < n) show(i);
    else hide();
  });
  root.addEventListener("pointerleave", hide);
  return wrap;
}
