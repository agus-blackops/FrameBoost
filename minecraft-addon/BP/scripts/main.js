import { world, system, EntityDamageCause, EquipmentSlot } from "@minecraft/server";

// ───────────── CONFIGURACIÓN ─────────────
// Jugadores que NO son "el elegido": los objetos no funcionan para ellos.
// (Todos los demás jugadores sí son elegidos.) También sirve el tag "fb_no_elegido".
const NO_ELEGIDOS = ["FaultierLocket0"];

// Dueño del addon: cuando él está en máximo peligro, aparece el Jefe Final (su guardián).
const DUENO = "DiamondHalo9031";
const BOSS = { PELIGRO_HP: 6, ESPERA: 20 * 60 * 5 };

const AIM = {
  RANGE: 14,          // bloques de alcance del aim
  ASSIST_CONE: 60,    // grados de cono (modo aim assist)
  ASSIST_SMOOTH: 0.6, // suavizado del aim assist (0-1)
  LOCK_SMOOTH: 1.0,   // 1 = aimbot instantáneo (agachado)
  TARGET_PLAYERS: true,
};

const WEAPONS = {
  "fb:titan_sword": { bonus: 80, smash: 2 },
  "fb:storm_axe": { bonus: 50, smash: 2, lightning: true },
  "minecraft:mace": { bonus: 40, smash: 3 },
};
const MAZO = "minecraft:mace"; // el mazo normal de Minecraft, con aimbot
const FB_ITEMS = [...Object.keys(WEAPONS), "fb:divine_heart", "fb:immortal_core", "fb:restoration_orb", "fb:antidote", "fb:supreme_staff", "fb:jump_orb", "fb:ally_book"];
const IGNORE_TYPES = new Set(["minecraft:item", "minecraft:xp_orb", "minecraft:armor_stand", "minecraft:arrow", "minecraft:lightning_bolt"]);

// ───────────── UTILIDADES ─────────────
const esElegido = (p) =>
  !NO_ELEGIDOS.some((n) => n.toLowerCase() === p.name.toLowerCase()) && !p.hasTag("fb_no_elegido");

function heldId(p) {
  const inv = p.getComponent("minecraft:inventory")?.container;
  return inv?.getItem(p.selectedSlotIndex)?.typeId;
}
function hasInInventory(p, id) {
  const c = p.getComponent("minecraft:inventory")?.container;
  if (!c) return false;
  for (let i = 0; i < c.size; i++) if (c.getItem(i)?.typeId === id) return true;
  return false;
}
function rechazar(p) {
  p.onScreenDisplay.setActionBar(`§c${p.name} no es el elegido. Este objeto no te obedece.`);
}
const cooldowns = new Map();
function enCooldown(p, key, ticks) {
  const k = p.id + key, now = system.currentTick;
  if ((cooldowns.get(k) ?? -Infinity) + ticks > now) return true;
  cooldowns.set(k, now);
  return false;
}
const wrap = (a) => ((a + 540) % 360) - 180;

// ───────────── AIM ASSIST DEL MAZO ─────────────
function elegirObjetivo(p, cono, rango) {
  const head = p.getHeadLocation();
  const v = p.getViewDirection();
  let best, bestAng = Infinity;
  for (const e of p.dimension.getEntities({ location: head, maxDistance: rango })) {
    if (e.id === p.id || IGNORE_TYPES.has(e.typeId) || !e.getComponent("minecraft:health")) continue;
    if (!AIM.TARGET_PLAYERS && e.typeId === "minecraft:player") continue;
    if (e.hasTypeFamily("villager") || e.hasTypeFamily("pet") || e.hasTypeFamily("fb_ally")) continue; // ni aldeanos, mascotas ni aliados
    if (activos.some((a) => valida(a.e) && a.e.id === e.id)) continue;
    const t = e.location, h = e.getComponent("minecraft:health");
    if (h && h.currentValue <= 0) continue;
    const dx = t.x - head.x, dy = t.y + 0.9 - head.y, dz = t.z - head.z;
    const len = Math.hypot(dx, dy, dz) || 1;
    const ang = (Math.acos(Math.max(-1, Math.min(1, (dx * v.x + dy * v.y + dz * v.z) / len))) * 180) / Math.PI;
    if (ang <= cono && ang < bestAng) { best = e; bestAng = ang; }
  }
  return best;
}
function apuntar(p) {
  const cayendo = p.getVelocity().y < -0.3; // cayendo con el mazo = aimbot total hacia el objetivo (para el smash)
  const lock = p.isSneaking || cayendo;      // agachado/cayendo = aimbot total, de pie = aim assist
  const e = elegirObjetivo(p, lock ? 180 : AIM.ASSIST_CONE, cayendo ? 20 : AIM.RANGE);
  if (!e) return;
  const head = p.getHeadLocation();
  const dx = e.location.x - head.x, dy = e.location.y + 0.9 - head.y, dz = e.location.z - head.z;
  const yaw = (-Math.atan2(dx, dz) * 180) / Math.PI;
  const pitch = (-Math.atan2(dy, Math.hypot(dx, dz)) * 180) / Math.PI;
  const cur = p.getRotation(), k = lock ? AIM.LOCK_SMOOTH : AIM.ASSIST_SMOOTH;
  const rot = { x: Math.max(-90, Math.min(90, cur.x + (pitch - cur.x) * k)), y: cur.y + wrap(yaw - cur.y) * k };
  if (Math.abs(wrap(yaw - cur.y)) < 0.5 && Math.abs(pitch - cur.x) < 0.5) return; // ya apunta
  // teleport con rotación (setRotation suele ser ignorado en jugadores); pero teleport reinicia la caída
  // y rompería el smash, así que al caer se usa setRotation.
  try {
    if (cayendo) p.setRotation(rot);
    else p.teleport(p.location, { rotation: rot, keepVelocity: true });
  } catch {}
}

// ───────────── BUCLE PRINCIPAL ─────────────
system.runInterval(() => {
  for (const p of world.getAllPlayers()) {
    const held = heldId(p);
    if (held && FB_ITEMS.includes(held) && !esElegido(p)) {
      if (system.currentTick % 20 === 0) rechazar(p);
      continue;
    }
    if (!esElegido(p)) continue;
    if (held === MAZO) apuntar(p);

    // Núcleo Inmortal: pasivo desde el inventario
    if (system.currentTick % 5 === 0 && hasInInventory(p, "fb:immortal_core")) {
      const h = p.getComponent("minecraft:health");
      if (h && h.currentValue > 0 && h.currentValue <= 10 && !enCooldown(p, "core", 200)) {
        h.resetToMaxValue();
        for (const [fx, amp] of [["resistance", 4], ["regeneration", 4], ["fire_resistance", 0], ["absorption", 9]])
          p.addEffect(fx, 600, { amplifier: amp });
        p.onScreenDisplay.setActionBar("§5¡Núcleo Inmortal te salvó!");
        p.playSound("random.totem");
      }
    }
  }
}, 1);

// ───────────── DAÑO EXTRA DE ARMAS ─────────────
world.afterEvents.entityHitEntity.subscribe(({ damagingEntity: p, hitEntity: t }) => {
  if (p.typeId !== "minecraft:player") return;
  const w = WEAPONS[heldId(p)];
  if (!w || !esElegido(p)) return;
  const cayendo = p.getVelocity().y < -0.35;
  try {
    t.applyDamage(w.bonus * (cayendo ? w.smash : 1), { cause: EntityDamageCause.entityAttack, damagingEntity: p });
    if (cayendo && w.smash > 1) {
      t.dimension.spawnParticle("minecraft:huge_explosion_emitter", t.location);
      p.playSound("random.explode");
    }
    if (w.lightning) t.dimension.spawnEntity("minecraft:lightning_bolt", t.location);
  } catch {}
});

// ───────────── ANTIDOTE (un solo uso) ─────────────
// Si golpeas a un jugador "no elegido" (FaultierLocket0) con el Antidote:
// pierde toda su armadura y todos sus efectos (ventajas), y el Antidote se gasta.
world.afterEvents.entityHitEntity.subscribe(({ damagingEntity: p, hitEntity: t }) => {
  if (p.typeId !== "minecraft:player" || t.typeId !== "minecraft:player") return;
  if (heldId(p) !== "fb:antidote" || !esElegido(p) || esElegido(t)) return;
  const inv = p.getComponent("minecraft:inventory")?.container;
  inv?.setItem(p.selectedSlotIndex, undefined); // un solo uso
  for (const [slot, s] of foto.items) if (s.typeId === "fb:antidote") foto.items.delete(slot); // que el escudo no lo devuelva
  const eq = t.getComponent("minecraft:equippable");
  for (const s of [EquipmentSlot.Head, EquipmentSlot.Chest, EquipmentSlot.Legs, EquipmentSlot.Feet]) eq?.setEquipment(s, undefined);
  for (const fx of t.getEffects()) t.removeEffect(fx.typeId);
  t.dimension.spawnParticle("minecraft:totem_particle", t.location);
  p.playSound("random.break");
  p.onScreenDisplay.setActionBar(`§2Antidote usado: ${t.name} perdió su armadura y sus ventajas`);
  t.onScreenDisplay.setActionBar("§cEl Antidote te quitó la armadura y todas tus ventajas");
});

// ───────────── USO DE OBJETOS ─────────────
world.beforeEvents.itemUse.subscribe((ev) => {
  const id = ev.itemStack.typeId, p = ev.source;
  if (!FB_ITEMS.includes(id)) return;
  if (!esElegido(p)) { ev.cancel = true; system.run(() => rechazar(p)); return; }
  if (id === "fb:supreme_staff") {
    ev.cancel = true;
    system.run(() => { if (!enCooldown(p, "staff", 5)) rayoSupremo(p); });
    return;
  }
  if (id === "fb:jump_orb") {
    ev.cancel = true;
    system.run(() => { if (!enCooldown(p, "jump", 10)) saltoSupremo(p); });
    return;
  }
  if (id === "fb:ally_book") {
    ev.cancel = true;
    system.run(() => {
      if (!esDueno(p)) return p.onScreenDisplay.setActionBar(`§cSolo ${DUENO} puede abrir este libro.`);
      const falta = Math.ceil((proximosAliados - system.currentTick) / 20);
      if (falta > 0) return p.onScreenDisplay.setActionBar(`§7El libro se recarga: ${falta}s`);
      invocarTodos(p);
    });
    return;
  }
  if (id === "fb:restoration_orb") {
    ev.cancel = true;
    system.run(() => { if (!enCooldown(p, "orb", 40)) restaurarTodo(p); });
    return;
  }
  if (id === "fb:divine_heart") {
    ev.cancel = true;
    system.run(() => {
      if (enCooldown(p, "heart", 100)) return p.onScreenDisplay.setActionBar("§7Corazón Divino recargando...");
      const dur = 20 * 60 * 15;
      for (const [fx, amp] of [["regeneration", 9], ["strength", 9], ["resistance", 3], ["absorption", 9], ["speed", 2], ["saturation", 0], ["health_boost", 9]])
        p.addEffect(fx, dur, { amplifier: amp });
      p.getComponent("minecraft:health")?.resetToMaxValue();
      p.playSound("random.levelup");
      p.onScreenDisplay.setActionBar("§dCorazón Divino: regeneración y fuerza supremas (5 min)");
    });
  }
});

// ───────────── REGISTRO DE BORRADOS (para el Orbe de Restauración) ─────────────
// Se guarda una "foto" de items y mobs cerca de los jugadores. Si una entidad desaparece
// sin haber muerto, sin ser recogida y sin haber expirado, se anota como "borrada".
const NO_REGISTRAR = new Set([...IGNORE_TYPES, "minecraft:player", "minecraft:fireball", "minecraft:snowball", "minecraft:egg", "minecraft:ender_pearl", "minecraft:thrown_trident", "minecraft:tnt", "minecraft:falling_block"]);
const SNAP_RANGE = 48, MAX_LEDGER = 3000, ITEM_LIFE = 5800;
const snaps = new Map();   // id -> foto
const muertos = new Set(); // ids que murieron normalmente
const borrados = [];       // entidades borradas, restaurables

system.runInterval(() => {
  const ahora = system.currentTick;
  for (const p of world.getAllPlayers()) {
    for (const e of p.dimension.getEntities({ location: p.location, maxDistance: SNAP_RANGE })) {
      if (NO_REGISTRAR.has(e.typeId)) continue;
      let s = snaps.get(e.id);
      if (!s) { s = { type: e.typeId, first: ahora }; snaps.set(e.id, s); }
      s.dim = e.dimension.id; s.loc = e.location; s.seen = ahora; s.name = e.nameTag;
      if (e.typeId === "minecraft:item") s.stack = e.getComponent("minecraft:item")?.itemStack;
    }
  }
  for (const [id, s] of snaps) if (ahora - s.seen > 600) snaps.delete(id); // limpieza
}, 10);

world.afterEvents.entityDie.subscribe((ev) => muertos.add(ev.deadEntity.id));

world.afterEvents.entityRemove.subscribe(({ removedEntityId: id }) => {
  const s = snaps.get(id); snaps.delete(id);
  const murio = muertos.delete(id);
  if (!s || murio) return;                                         // sin foto o murió
  const ahora = system.currentTick;
  if (ahora - s.seen > 40) return;                                 // dato viejo / chunk descargado
  if (s.type === "minecraft:item" && ahora - s.first >= ITEM_LIFE) return; // expiró solo
  const cerca = world.getAllPlayers().filter((p) => p.dimension.id === s.dim);
  if (!cerca.length) return;                                       // nadie cerca: probable descarga de chunk
  if (cerca.every((p) => Math.hypot(p.location.x - s.loc.x, p.location.z - s.loc.z) > SNAP_RANGE + 8)) return; // lejos de todos: chunk descargado
  if (s.type === "minecraft:item" && cerca.some((p) => Math.hypot(p.location.x - s.loc.x, p.location.y - s.loc.y, p.location.z - s.loc.z) < 5)) return; // recogido
  borrados.push(s);
  if (borrados.length > MAX_LEDGER) borrados.shift();
});

function restaurarTodo(p) {
  let items = 0, mobs = 0;
  for (const s of borrados.splice(0)) {
    try {
      const dim = world.getDimension(s.dim);
      if (s.type === "minecraft:item") { if (s.stack) { dim.spawnItem(s.stack, s.loc); items++; } }
      else { const e = dim.spawnEntity(s.type, s.loc); if (s.name) e.nameTag = s.name; mobs++; }
    } catch {}
  }
  p.playSound("beacon.activate");
  p.onScreenDisplay.setActionBar(`§aRestauración: ${items} objetos y ${mobs} mobs de vuelta`);
}

// ───────────── BÁCULO SUPREMO y ORBE DE SALTO ─────────────
function rayoSupremo(p) {
  const dir = p.getViewDirection(), o = p.getHeadLocation();
  let alcance = 64;
  try { // el rayo se detiene en el primer bloque
    const b = p.getBlockFromViewDirection({ maxDistance: 64 });
    if (b) alcance = Math.hypot(b.block.location.x - o.x, b.block.location.y - o.y, b.block.location.z - o.z);
  } catch {}
  for (let d = 1; d < alcance; d += 1) {
    try { p.dimension.spawnParticle("minecraft:blue_flame_particle", { x: o.x + dir.x * d, y: o.y + dir.y * d, z: o.z + dir.z * d }); } catch {}
  }
  for (const { entity: e, distance } of p.getEntitiesFromViewDirection({ maxDistance: 64 })) {
    if (distance > alcance || e.id === p.id || e.hasTypeFamily("fb_ally")) continue;
    try { e.applyDamage(150, { cause: EntityDamageCause.entityAttack, damagingEntity: p }); e.dimension.spawnParticle("minecraft:huge_explosion_emitter", e.location); } catch {}
  }
  p.playSound("random.explode");
}
function saltoSupremo(p) {
  const dir = p.getViewDirection(), o = p.getHeadLocation();
  let dest;
  try {
    const b = p.getBlockFromViewDirection({ maxDistance: 60 });
    if (b) dest = { x: b.block.location.x + 0.5, y: b.block.location.y + 1, z: b.block.location.z + 0.5 };
  } catch {}
  dest ??= { x: o.x + dir.x * 40, y: o.y + dir.y * 40 - 1.6, z: o.z + dir.z * 40 };
  p.dimension.spawnParticle("minecraft:totem_particle", p.location);
  p.teleport(dest, { keepVelocity: false });
  p.addEffect("resistance", 60, { amplifier: 4, showParticles: false }); // sin daño de caída por el salto
  p.playSound("mob.endermen.portal");
}

// ───────────── ALIADOS DE DiamondHalo9031 ─────────────
// 5 aliados: Jefe Final, Warden Final, Clawd (mascota de Claude Code), La Convergencia y el Logo de ChatGPT.
// - El Jefe Final sale solo cuando el dueño queda con <= 3 corazones.
// - Los 5 salen juntos cuando FaultierLocket0 le da el "Golpe Supremo" al dueño llevando el Antidote.
// - Prueba rápida: /scriptevent fb:aliados   (los 5)  o  /scriptevent fb:boss  (solo el Jefe)
const ALIADOS = {
  jefe: { tipo: "minecraft:iron_golem", nombre: "§4§lJefe Final", dano: 100, radio: 16 },
  warden: { tipo: "fb:warden_final", nombre: "§3§lWarden Final", dano: 400, radio: 20, rompeArmadura: true, inmortal: true },
  clawd: { tipo: "fb:clawd", nombre: "§6Clawd", dano: 80, radio: 16, cura: true },
  convergencia: { tipo: "fb:convergencia", nombre: "§d§lLa Convergencia", dano: 200, radio: 24, debilita: true },
  chatgpt: { tipo: "fb:chatgpt_logo", nombre: "§aLogo de ChatGPT", dano: 100, radio: 20, marca: true },
};
const DURACION_ALIADOS = 20 * 300, ESPERA_ALIADOS = 20 * 90;
const ARMADURA = [EquipmentSlot.Head, EquipmentSlot.Chest, EquipmentSlot.Legs, EquipmentSlot.Feet];
const activos = []; // { kind, e, fin }
let proximoJefe = 0, proximosAliados = 0, ultimoAtacante = null, ultimoAtaque = 0;

const esDueno = (p) => p.name.toLowerCase() === DUENO.toLowerCase();
const valida = (e) => { try { return !!e && e.isValid; } catch { return false; } };

function invocar(kind, dueno, n, total) {
  const def = ALIADOS[kind], l = dueno.location, ang = (n / total) * Math.PI * 2;
  const pos = { x: l.x + Math.cos(ang) * 3, y: l.y, z: l.z + Math.sin(ang) * 3 };
  let e;
  try { e = dueno.dimension.spawnEntity(def.tipo, pos); } catch { e = dueno.dimension.spawnEntity(def.tipo, l); }
  e.nameTag = def.nombre;
  if (kind === "jefe") {
    for (const [fx, amp] of [["health_boost", 49], ["instant_health", 20], ["resistance", 4], ["strength", 9], ["speed", 2], ["regeneration", 4]])
      e.addEffect(fx, DURACION_ALIADOS + 40, { amplifier: amp });
  }
  if (def.inmortal) { e.addEffect("resistance", 20 * 60 * 10, { amplifier: 254, showParticles: false }); e.addEffect("fire_resistance", 20 * 60 * 10, { showParticles: false }); }
  dueno.dimension.spawnParticle("minecraft:huge_explosion_emitter", e.location);
  activos.push({ kind, e, fin: system.currentTick + DURACION_ALIADOS });
}
function invocarTodos(dueno) {
  const k = Object.keys(ALIADOS);
  k.forEach((kind, i) => invocar(kind, dueno, i, k.length));
  proximosAliados = system.currentTick + ESPERA_ALIADOS;
  world.playSound("mob.wither.spawn");
  world.sendMessage(`§6§l¡LOS 5 ALIADOS DE ${dueno.name} HAN LLEGADO!`);
  for (const [fx, amp] of [["resistance", 3], ["regeneration", 4], ["absorption", 4]]) dueno.addEffect(fx, 20 * 20, { amplifier: amp });
}

world.afterEvents.entityHurt.subscribe(({ hurtEntity: v, damageSource: d }) => {
  if (v.typeId !== "minecraft:player" || !esDueno(v) || !d.damagingEntity || d.damagingEntity.id === v.id) return;
  ultimoAtacante = d.damagingEntity; ultimoAtaque = system.currentTick;
  if (d.damagingEntity.typeId === "minecraft:player" && !esElegido(d.damagingEntity)) golpeSupremo(v);
});

system.runInterval(() => {
  const ahora = system.currentTick;
  const dueno = world.getAllPlayers().find(esDueno);
  if (!dueno) return;
  if (ahora >= proximoJefe && !activos.some((a) => a.kind === "jefe")) {
    const h = dueno.getComponent("minecraft:health");
    if (h && h.currentValue > 0 && h.currentValue <= BOSS.PELIGRO_HP) {
      invocar("jefe", dueno, 0, 1); proximoJefe = ahora + BOSS.ESPERA;
      world.playSound("mob.wither.spawn");
      world.sendMessage(`§4§l¡EL JEFE FINAL HA DESPERTADO! §r§7${dueno.name} está en máximo peligro.`);
      for (const [fx, amp] of [["resistance", 3], ["regeneration", 4], ["absorption", 4]]) dueno.addEffect(fx, 20 * 20, { amplifier: amp });
    }
  }
  if (!activos.length) return;
  const ids = new Set(activos.filter((a) => valida(a.e)).map((a) => a.e.id));
  const monstruos = dueno.dimension.getEntities({ location: dueno.location, maxDistance: 26, families: ["monster"] });
  for (let i = activos.length - 1; i >= 0; i--) {
    const a = activos[i], def = ALIADOS[a.kind];
    if (!valida(a.e)) { activos.splice(i, 1); continue; }
    if (ahora >= a.fin) { a.e.dimension.spawnParticle("minecraft:totem_particle", a.e.location); a.e.remove(); activos.splice(i, 1); continue; }
    const d = Math.hypot(a.e.location.x - dueno.location.x, a.e.location.z - dueno.location.z);
    if (a.e.dimension.id !== dueno.dimension.id || d > 12) a.e.teleport(dueno.location, { dimension: dueno.dimension });
    if (def.inmortal) a.e.getComponent("minecraft:health")?.resetToMaxValue(); // vida infinita
    if (def.cura) dueno.addEffect("regeneration", 40, { amplifier: 4, showParticles: false });
    if (a.kind !== "jefe") dueno.addEffect("resistance", 40, { amplifier: 1, showParticles: false });
    const enemigos = monstruos.filter((e) => Math.hypot(e.location.x - dueno.location.x, e.location.z - dueno.location.z) <= def.radio);
    if (valida(ultimoAtacante) && ahora - ultimoAtaque < 400 && ultimoAtacante.dimension.id === dueno.dimension.id) enemigos.push(ultimoAtacante);
    for (const e of enemigos) {
      if (ids.has(e.id) || e.id === dueno.id || e.hasTypeFamily("fb_ally")) continue;
      try {
        if (def.rompeArmadura && e.typeId === "minecraft:player") { // fuerza que destruye una armadura completa de netherite
          const eq = e.getComponent("minecraft:equippable");
          for (const s of ARMADURA) eq?.setEquipment(s, undefined);
        }
        try {
          if (def.debilita) { e.addEffect("weakness", 100, { amplifier: 3 }); e.addEffect("slowness", 100, { amplifier: 2 }); }
          if (def.marca) { e.addEffect("weakness", 100, { amplifier: 2 }); e.addEffect("mining_fatigue", 100, { amplifier: 2 }); }
        } catch {}
        e.applyDamage(def.dano, { cause: EntityDamageCause.entityAttack, damagingEntity: a.e });
        e.dimension.spawnParticle("minecraft:critical_hit_emitter", e.location);
      } catch {}
    }
  }
  dueno.onScreenDisplay.setActionBar(`§6Aliados activos: ${activos.length}`);
}, 5);

// ───────────── ANTIDOTE: escudo contra el "Golpe Supremo" ─────────────
// El Golpe Supremo es el ataque de FaultierLocket0 que quita los poderes del addon.
// Con el Antidote en el inventario: se guarda una foto de tus efectos y objetos del addon;
// si FaultierLocket0 te golpea, durante 5 s todo se restaura al instante (no te afecta)
// y llegan los 5 aliados.
const foto = { efectos: [], items: new Map(), congelado: 0 };
let escudoHasta = 0;
const ES_MOD = (id) => id?.startsWith("fb:") || id === MAZO;

system.runInterval(() => {
  const ahora = system.currentTick;
  const dueno = world.getAllPlayers().find(esDueno);
  if (!dueno) return;
  const cont = dueno.getComponent("minecraft:inventory")?.container;
  if (!cont) return;
  if (ahora < foto.congelado) {
    if (ahora < escudoHasta) restaurar(dueno, cont, ahora);
    return;
  }
  foto.items.clear();
  for (let i = 0; i < cont.size; i++) { const s = cont.getItem(i); if (s && ES_MOD(s.typeId)) foto.items.set(i, s); }
  foto.efectos = dueno.getEffects().map((f) => ({ typeId: f.typeId, amplifier: f.amplifier, duration: f.duration, tick: ahora }));
}, 2);

function restaurar(dueno, cont, ahora) {
  for (const [slot, stack] of foto.items) {
    const cur = cont.getItem(slot);
    if (!cur || cur.typeId !== stack.typeId) cont.setItem(slot, stack);
  }
  for (const f of foto.efectos) {
    const resto = f.duration < 0 ? 20 * 600 : f.duration - (ahora - f.tick);
    if (resto > 20 && !dueno.getEffect(f.typeId)) dueno.addEffect(f.typeId, resto, { amplifier: f.amplifier });
  }
}

function golpeSupremo(dueno) {
  if (!hasInInventory(dueno, "fb:antidote")) return;
  const ahora = system.currentTick;
  foto.congelado = ahora + 120; escudoHasta = ahora + 100;
  dueno.onScreenDisplay.setActionBar("§2¡El Antidote bloqueó el Golpe Supremo!");
  dueno.playSound("random.totem");
  if (ahora >= proximosAliados) invocarTodos(dueno);
}

// ───────────── AURA DEL ELEGIDO ─────────────
// DiamondHalo9031, mientras lleve cualquier objeto del addon, recibe poderes permanentes.
const AURA = [["strength", 3], ["resistance", 2], ["regeneration", 2], ["speed", 2], ["jump_boost", 1], ["night_vision", 0], ["saturation", 0], ["fire_resistance", 0], ["water_breathing", 0], ["health_boost", 4]];
system.runInterval(() => {
  const p = world.getAllPlayers().find(esDueno);
  if (!p) return;
  const c = p.getComponent("minecraft:inventory")?.container;
  if (!c) return;
  let tiene = false;
  for (let i = 0; i < c.size && !tiene; i++) tiene = ES_MOD(c.getItem(i)?.typeId);
  if (!tiene) return;
  for (const [fx, amp] of AURA) { try { p.addEffect(fx, 20 * 30, { amplifier: amp, showParticles: false }); } catch {} }
}, 100);

// ───────────── KIT: /scriptevent fb:kit ─────────────
system.afterEvents.scriptEventReceive.subscribe((ev) => {
  if (ev.sourceEntity?.typeId === "minecraft:player") {
    if (ev.id === "fb:boss") { invocar("jefe", ev.sourceEntity, 0, 1); return; }          // prueba
    if (ev.id === "fb:aliados") { invocarTodos(ev.sourceEntity); return; }               // prueba
  }
  if (ev.id !== "fb:kit" || ev.sourceType !== "Entity" || ev.sourceEntity?.typeId !== "minecraft:player") return;
  for (const id of [MAZO, ...FB_ITEMS]) ev.sourceEntity.runCommand(`give @s ${id}`);
});
