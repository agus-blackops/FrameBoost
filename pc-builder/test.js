// Pruebas del motor: node pc-builder/test.js
const E = require("./engine.js");
let bad = 0, n = 0;
const fail = (m) => { bad++; console.log("FALLA:", m); };
for (const mode of Object.keys(E.MODES))
  for (const cpuBrand of ["any", "AMD", "Intel"])
    for (const gpuBrand of ["any", "NVIDIA", "AMD"])
      for (let budget = 100000; budget <= 6000000; budget += 20000) {
        const min = E.minBudget(mode, { cpuBrand, gpuBrand });
        const res = E.recommend(mode, budget, { cpuBrand, gpuBrand });
        if (min !== null && budget >= min && !res.length) fail(`${mode} ${budget} sin resultado aunque min=${min}`);
        if (min !== null && budget < min && res.length) fail(`${mode} ${budget} resultado bajo el mínimo ${min}`);
        for (const r of res) {
          n++;
          const b = r.build;
          const sum = r.parts.reduce((a, p) => a + p.price, 0);
          if (sum !== b.total) fail(`suma partes ${sum} != total ${b.total}`);
          if (b.total > budget) fail(`${mode} excede presupuesto ${b.total} > ${budget}`);
          if (b.cpu.socket !== b.mobo.socket) fail("socket incompatible");
          if (b.ram.type !== b.mobo.ram) fail("RAM incompatible");
          if (b.mobo.tier < b.cpu.minTier) fail("placa insuficiente para CPU");
          if (!b.gpu && !b.cpu.igpu) fail("sin video");
          if (mode === "gamer" && !b.gpu) fail("gamer sin GPU");
          const need = (b.gpu ? b.gpu.w : 0) + b.cpu.w + 120;
          if (b.psu.w < need * 1.2) fail(`fuente corta ${b.psu.w}W para ${need}W`);
          if (cpuBrand !== "any" && b.cpu.brand !== cpuBrand) fail("marca CPU");
          if (gpuBrand !== "any" && b.gpu && b.gpu.brand !== gpuBrand) fail("marca GPU");
        }
      }
console.log(`${n} recomendaciones revisadas, ${bad} fallas`);
process.exit(bad ? 1 : 0);
