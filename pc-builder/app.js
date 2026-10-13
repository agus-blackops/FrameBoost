(function () {
  const { MODES, recommend, minBudget, CATALOG } = window.PCEngine;
  const $ = (id) => document.getElementById(id);
  const clp = new Intl.NumberFormat("es-CL", { style: "currency", currency: "CLP", maximumFractionDigits: 0 });
  const COLORS = { "Procesador": "#7c5cff", "Placa madre": "#22d3ee", "Memoria RAM": "#34d399", "Tarjeta de video": "#f472b6",
    "Almacenamiento": "#fbbf24", "Fuente de poder": "#fb923c", "Gabinete": "#94a3b8", "Cooler CPU": "#60a5fa" };
  const CHIPS = [400000, 600000, 800000, 1000000, 1500000, 2000000, 3000000];

  const state = { mode: "gamer", budget: 800000, tab: 0, results: [] };
  try {
    const saved = JSON.parse(localStorage.getItem("pcbuilder") || "{}");
    if (saved.mode && MODES[saved.mode]) state.mode = saved.mode;
    if (saved.budget > 0) state.budget = saved.budget;
  } catch (e) {}

  $("upd").textContent = CATALOG.updated;
  CHIPS.forEach((v) => {
    const b = document.createElement("button");
    b.type = "button"; b.className = "chip"; b.textContent = clp.format(v);
    b.onclick = () => setBudget(v);
    $("chips").appendChild(b);
  });

  function setBudget(v) {
    state.budget = Math.max(0, Math.round(v));
    $("budgetNum").value = state.budget ? state.budget.toLocaleString("es-CL") : "";
    $("budgetRange").value = Math.min(Math.max(state.budget, 250000), 4000000);
    update();
  }

  document.querySelectorAll('input[name="mode"]').forEach((r) => {
    r.checked = r.value === state.mode;
    r.onchange = () => { state.mode = r.value; state.tab = 0; update(); };
  });
  $("budgetNum").oninput = (e) => {
    const n = parseInt(e.target.value.replace(/\D/g, ""), 10) || 0;
    state.budget = n;
    e.target.value = n ? n.toLocaleString("es-CL") : "";
    $("budgetRange").value = Math.min(Math.max(n, 250000), 4000000);
    update();
  };
  $("budgetRange").oninput = (e) => setBudget(+e.target.value);
  $("cpuBrand").onchange = $("gpuBrand").onchange = () => { state.tab = 0; update(); };

  function prefs() { return { cpuBrand: $("cpuBrand").value, gpuBrand: $("gpuBrand").value }; }
  const esc = (s) => String(s).replace(/[&<>"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c]));

  function update() {
    try { localStorage.setItem("pcbuilder", JSON.stringify({ mode: state.mode, budget: state.budget })); } catch (e) {}
    $("gpuBrandWrap").style.display = state.mode === "oficina" ? "none" : "";
    const p = prefs();
    const min = minBudget(state.mode, p);
    $("minHint").textContent = min ? "Con tus preferencias, el mínimo para este tipo de PC es " + clp.format(min) + "." : "";
    state.results = state.budget >= (min || Infinity) ? recommend(state.mode, state.budget, p) : [];
    if (state.tab >= state.results.length) state.tab = 0;
    render(min);
  }

  function render(min) {
    const el = $("results");
    if (!state.results.length) {
      el.innerHTML = '<div class="card empty"><p>Con <b>' + clp.format(state.budget) + '</b> todavía no alcanza para armar un ' +
        esc(MODES[state.mode].label) + ' completo (torre con todas sus piezas).</p>' +
        (min ? '<p>Prueba con al menos <b>' + clp.format(min) + '</b>.</p>' : "<p>Prueba cambiando las preferencias.</p>") + "</div>";
      return;
    }
    const tabs = state.results.length > 1
      ? '<div class="tabs" role="tablist">' + state.results.map((r, i) =>
          '<button class="tab" role="tab" aria-selected="' + (i === state.tab) + '" data-i="' + i + '">' + esc(r.variant.label) + "</button>").join("") + "</div>"
      : "";
    const r = state.results[state.tab];
    const total = r.build.total, left = state.budget - total;
    const rows = r.parts.map((x) =>
      "<tr><td class='cat'>" + esc(x.cat) + "</td><td>" + esc(x.name) +
      (x.price ? ' <a target="_blank" rel="noopener" href="https://www.google.com/search?q=' + encodeURIComponent(x.name + " precio Chile") + '">buscar ↗</a>' : "") +
      "</td><td class='p'>" + (x.price ? clp.format(x.price) : "Incluido") + "</td></tr>").join("");
    const seg = r.parts.filter((x) => x.price).map((x) =>
      '<i title="' + esc(x.cat) + '" style="width:' + (x.price / total * 100).toFixed(2) + "%;background:" + (COLORS[x.cat] || "#888") + '"></i>').join("");
    const leg = r.parts.filter((x) => x.price).map((x) =>
      '<span><b style="background:' + (COLORS[x.cat] || "#888") + '"></b>' + esc(x.cat) + " " + Math.round(x.price / total * 100) + "%</span>").join("");
    const notes = r.info.notes.slice();
    if (state.mode !== "gamer" && left > state.budget * 0.4)
      notes.unshift("WARN:Esta categoría no necesita tanto: te sobra bastante presupuesto. Considera subir de categoría (por ejemplo PC Gamer o PC de trabajo) para aprovecharlo.");
    el.innerHTML = tabs + '<div class="card build"><h3>' + esc(r.variant.label) + "</h3><p class='desc'>" + esc(r.variant.desc) + "</p>" +
      '<div class="perf">✅ ' + esc(r.info.perf) + "</div>" +
      "<table><thead><tr><th class='cat'>Pieza</th><th>Componente</th><th class='p'>Precio</th></tr></thead><tbody>" + rows +
      "</tbody><tfoot><tr><td class='cat'></td><td>Total</td><td class='p'>" + clp.format(total) + "</td></tr></tfoot></table>" +
      '<div class="bar">' + seg + '</div><div class="legend">' + leg + "</div>" +
      '<div class="sum"><div><small>Presupuesto</small><strong>' + clp.format(state.budget) + "</strong></div>" +
      "<div><small>Total armado</small><strong>" + clp.format(total) + "</strong></div>" +
      "<div><small>Te sobra</small><strong>" + clp.format(left) + "</strong></div></div>" +
      '<ul class="notes">' + notes.map((n) => n.startsWith("WARN:") ? '<li class="warn">' + esc(n.slice(5)) + "</li>" : "<li>" + esc(n) + "</li>").join("") + "</ul>" +
      '<div class="actions"><button class="btn" id="copy">📋 Copiar lista</button>' +
      (/; wv\)/.test(navigator.userAgent) ? "" : '<button class="btn" onclick="window.print()">🖨️ Imprimir</button>') + "</div></div>";
    el.querySelectorAll(".tab").forEach((b) => (b.onclick = () => { state.tab = +b.dataset.i; render(min); }));
    $("copy").onclick = async () => {
      const txt = MODES[state.mode].label + " — " + r.variant.label + "\n" +
        r.parts.map((x) => x.cat + ": " + x.name + " — " + (x.price ? clp.format(x.price) : "Incluido")).join("\n") +
        "\nTOTAL: " + clp.format(total) + " (presupuesto " + clp.format(state.budget) + ")";
      try { await navigator.clipboard.writeText(txt); $("copy").textContent = "✅ ¡Copiado!"; }
      catch (e) {
        const ta = document.createElement("textarea");
        ta.value = txt; document.body.appendChild(ta); ta.select();
        let ok = false;
        try { ok = document.execCommand("copy"); } catch (e2) {}
        document.body.removeChild(ta);
        $("copy").textContent = ok ? "✅ ¡Copiado!" : "No se pudo copiar";
      }
    };
  }

  setBudget(state.budget);
})();
