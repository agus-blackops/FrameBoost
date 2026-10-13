// Motor de recomendación: dado un presupuesto en CLP y un tipo de PC, busca la
// combinación compatible de componentes con mejor puntaje que cabe en el monto.
(function (root) {
  const CATALOG = typeof module !== "undefined" && module.exports ? require("./catalog.js") : root.CATALOG;

  const RAM_GAMER  = { 8: -30, 16: 0, 32: 6, 64: 9 };
  const RAM_PRO    = { 8: -30, 16: 0, 32: 14, 64: 22 };
  const RAM_OFFICE = { 8: -6, 16: 0, 32: 3, 64: 3 };
  const SSD_BONUS  = { 500: -2, 1000: 3, 2000: 6, 4000: 8 };

  // Cada modo define qué se exige y cómo se puntúa una combinación.
  const MODES = {
    gamer: {
      label: "PC Gamer",
      gpu: "required", minRam: 16, minSsd: 500,
      variants: [
        { key: "balanced", label: "Equilibrada",     desc: "Reparte el presupuesto entre procesador y tarjeta de video para que ninguno frene al otro.", gm: 1,    em: 1,   cw: 0 },
        { key: "fps",      label: "Máximo FPS",      desc: "Prioriza la tarjeta de video; el resto queda lo justo.",                                     gm: 1.18, em: 0.3, cw: 0 },
        { key: "future",   label: "Pensada a futuro", desc: "Plataforma AM5 con DDR5: podrás cambiar procesador y memoria más adelante.",                gm: 1,    em: 1.2, cw: 0, filter: (p) => p.mobo.socket === "AM5" },
        { key: "saver",    label: "Mejor precio",    desc: "Busca el mejor rendimiento por peso, sin gastar de más.",                                    gm: 1,    em: 0.8, cw: 0.00014 },
      ],
      score(p, v) {
        const need = 30 + 0.7 * p.gpu.s;
        const perf = p.gpu.s * (0.55 + 0.45 * Math.min(1, p.cpu.g / need));
        return perf * v.gm + 0.1 * p.cpu.g + extras(p, RAM_GAMER, 1) * v.em;
      },
    },
    pro: {
      label: "PC de trabajo",
      gpu: "optional", minRam: 16, minSsd: 500,
      variants: [
        { key: "balanced", label: "Equilibrada",  desc: "Mucho procesador y memoria, con tarjeta de video si el presupuesto la justifica.", em: 1, cw: 0 },
        { key: "cpu",      label: "Máximo CPU",   desc: "Todo al procesador y la memoria: render, compilación, máquinas virtuales.",       em: 1, cw: 0, cpuMult: 1.4, gpuMult: 0.2 },
        { key: "saver",    label: "Mejor precio", desc: "Lo necesario para trabajar fluido, sin lujos.",                                   em: 0.8, cw: 0.00022 },
      ],
      score(p, v) {
        const gpuS = p.gpu ? p.gpu.s : 0;
        return (p.cpu.m * 0.55 * (v.cpuMult || 1)) + p.cpu.g * 0.1 + gpuS * 0.4 * (v.gpuMult || 1) + extras(p, RAM_PRO, 1.5) * v.em;
      },
    },
    oficina: {
      label: "PC de oficina / hogar",
      gpu: "none", minRam: 8, minSsd: 500, needIgpu: true,
      variants: [
        { key: "balanced", label: "Equilibrada",  desc: "Fluida para navegar, Office, clases online y multitarea.", em: 1,   cw: 0.00009 },
        { key: "saver",    label: "Mejor precio", desc: "La opción más económica que sigue siendo cómoda de usar.", em: 0.7, cw: 0.00030 },
        { key: "power",    label: "Más potencia", desc: "Más margen para años de uso y programas más pesados.",    em: 1,   cw: 0.00002 },
      ],
      score(p, v) {
        return Math.min(p.cpu.m, 95) * 0.4 + p.cpu.g * 0.15 + extras(p, RAM_OFFICE, 1) * v.em;
      },
    },
  };

  function extras(p, ramTable, ssdMult) {
    return (ramTable[p.ram.gb] || 0) + (SSD_BONUS[p.ssd.gb] || 0) * ssdMult + p.mobo.tier * 1.5 + p.case.tier;
  }

  function psuFor(cpu, gpu) {
    const need = Math.ceil(((gpu ? gpu.w : 0) + cpu.w + 120) * 1.25 / 50) * 50;
    return CATALOG.psus.find((x) => x.w >= need) || null;
  }

  function search(modeKey, budget, variant, prefs) {
    const mode = MODES[modeKey];
    prefs = prefs || {};
    let gpuList = [];
    if (mode.gpu === "none") gpuList = [null];
    else {
      gpuList = CATALOG.gpus.filter((g) => !prefs.gpuBrand || prefs.gpuBrand === "any" || g.brand === prefs.gpuBrand);
      if (mode.gpu === "optional") gpuList = [null].concat(gpuList);
    }
    const cpus = CATALOG.cpus.filter((c) => !prefs.cpuBrand || prefs.cpuBrand === "any" || c.brand === prefs.cpuBrand);

    let best = null;
    for (const cpu of cpus) {
      if (mode.needIgpu && !cpu.igpu) continue;
      for (const gpu of gpuList) {
        if (!gpu && !cpu.igpu) continue;
        const psu = psuFor(cpu, gpu);
        if (!psu) continue;
        const base = cpu.price + cpu.cooler + (gpu ? gpu.price : 0) + psu.price;
        if (base > budget) continue;
        for (const mobo of CATALOG.mobos) {
          if (mobo.socket !== cpu.socket || mobo.tier < cpu.minTier) continue;
          const b2 = base + mobo.price;
          if (b2 > budget) continue;
          for (const ram of CATALOG.rams) {
            if (ram.type !== mobo.ram || ram.gb < mode.minRam) continue;
            const b3 = b2 + ram.price;
            if (b3 > budget) continue;
            for (const ssd of CATALOG.ssds) {
              if (ssd.gb < mode.minSsd) continue;
              const b4 = b3 + ssd.price;
              if (b4 > budget) continue;
              for (const cs of CATALOG.cases) {
                const total = b4 + cs.price;
                if (total > budget) continue;
                const p = { cpu, gpu, mobo, ram, ssd, psu, case: cs };
                if (variant.filter && !variant.filter(p)) continue;
                const score = mode.score(p, variant) - total * variant.cw;
                if (!best || score > best.score || (score === best.score && total < best.total)) {
                  best = { ...p, total, score };
                }
              }
            }
          }
        }
      }
    }
    return best;
  }

  function describe(b, modeKey) {
    const notes = [];
    let perf = "";
    if (modeKey === "gamer") {
      const s = b.gpu.s;
      perf =
        s < 30 ? "1080p en calidad baja-media (eSports a 60+ FPS)" :
        s < 45 ? "1080p en calidad alta a 60 FPS" :
        s < 70 ? "1080p Ultra o 1440p en calidad media-alta" :
        s < 100 ? "1440p Ultra con muchos FPS (4K en calidad media)" :
        s < 150 ? "1440p extremo y 4K en calidad alta" :
        "4K Ultra con trazado de rayos";
      if (b.cpu.g < 30 + 0.7 * s - 12) notes.push("El procesador podría limitar a la tarjeta de video en juegos exigentes.");
      if (b.gpu.s < 25) notes.push("Tarjeta de entrada: ideal para eSports y juegos ligeros.");
    } else if (modeKey === "pro") {
      perf = b.gpu ? "Edición, diseño, programación y render con aceleración por GPU" : "Programación, ofimática avanzada y edición ligera";
      if (!b.gpu) notes.push("Usa los gráficos integrados del procesador; si editas video o 3D, sube el presupuesto para sumar una GPU dedicada.");
    } else {
      perf = "Navegación, Office, clases online, streaming y multitarea";
      notes.push("Usa los gráficos integrados del procesador. Para jugar en serio, elige \"PC Gamer\".");
    }
    if (b.cpu.cooler > 0) notes.push("Este procesador no trae disco de enfriamiento: se incluyó un cooler de torre en el precio.");
    if (b.mobo.ram === "DDR4") notes.push("Plataforma DDR4: económica, pero con menos opciones de actualización futura.");
    else notes.push("Plataforma DDR5: más fácil de actualizar en el futuro.");
    const used = b.total;
    return { perf, notes, used };
  }

  function recommend(modeKey, budget, prefs) {
    const mode = MODES[modeKey];
    const out = [];
    const seen = new Set();
    for (const v of mode.variants) {
      const b = search(modeKey, budget, v, prefs);
      if (!b) continue;
      const sig = [b.cpu.id, b.gpu && b.gpu.id, b.mobo.id, b.ram.id, b.ssd.id, b.psu.id, b.case.id].join("|");
      if (seen.has(sig)) continue;
      seen.add(sig);
      out.push({ variant: v, build: b, info: describe(b, modeKey), parts: partsOf(b) });
    }
    return out;
  }

  function partsOf(b) {
    const parts = [
      { cat: "Procesador", name: b.cpu.name, price: b.cpu.price },
      { cat: "Placa madre", name: b.mobo.name, price: b.mobo.price },
      { cat: "Memoria RAM", name: b.ram.name, price: b.ram.price },
    ];
    if (b.gpu) parts.push({ cat: "Tarjeta de video", name: b.gpu.name, price: b.gpu.price });
    else parts.push({ cat: "Tarjeta de video", name: "Gráficos integrados del procesador", price: 0 });
    parts.push({ cat: "Almacenamiento", name: b.ssd.name, price: b.ssd.price });
    parts.push({ cat: "Fuente de poder", name: b.psu.name, price: b.psu.price });
    parts.push({ cat: "Gabinete", name: b.case.name, price: b.case.price });
    if (b.cpu.cooler > 0) parts.push({ cat: "Cooler CPU", name: "Cooler de torre", price: b.cpu.cooler });
    return parts;
  }

  // Presupuesto mínimo con el que se puede armar el tipo de PC elegido.
  function minBudget(modeKey, prefs) {
    // Con puntaje constante y un costo por peso, gana la combinación más barata.
    const mode = MODES[modeKey];
    const orig = mode.score;
    mode.score = () => 0;
    const b = search(modeKey, Infinity, { cw: 1 }, prefs);
    mode.score = orig;
    return b ? b.total : null;
  }

  const api = { MODES, recommend, minBudget, search, CATALOG };
  if (typeof module !== "undefined" && module.exports) module.exports = api;
  else root.PCEngine = api;
})(typeof window !== "undefined" ? window : globalThis);
