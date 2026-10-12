import { world, system, EntityDamageCause, EquipmentSlot } from "@minecraft/server";

// ───────────── CONFIGURACIÓN ─────────────
// Jugadores que NO son "el elegido": los objetos no funcionan para ellos.
// (Todos los demás jugadores sí son elegidos.) También sirve el tag "fb_no_elegido".
const NO_ELEGIDOS = ["FaultierLocket0"];

// Dueño del addon: cuando él está en máximo peligro, aparece el Jefe Final (su guardián).
const DUENO = "DiamondHalo9031";
const BOSS = { PELIGRO_HP: 6, DURACION: 20 * 90, ESPERA: 20 * 60 * 5, DANO: 40, RADIO: 9 };

const AIM = {
  RANGE: 10,          // bloques de alcance del aim
  ASSIST_CONE: 40,    // grados de cono (modo aim assist)
  ASSIST_SMOOTH: 0.35,// suavizado del aim assist (0-1)
  LOCK_SMOOTH: 1.0,   // 1 = aimbot instantáneo (agachado)
  TARGET_PLAYERS: true,
};

const WEAPONS = {
  "fb:aim_mace": { bonus: 16, smash: 2.5 },
  "fb:titan_sword": { bonus: 34, smash: 1 },
  "fb:storm_axe": { bonus: 22, smash: 1, lightning: true },
};
const FB_ITEMS = [...Object.keys(WEAPONS), "fb:divine_heart", "fb:immortal_core", "fb:restoration_orb", "fb:antidote"];
const IGNORE_TYPES = new Set(["minecraft:item", "minecraft:xp_orb", "minecraft:armor_stand", "minecraft:arrow", "minecraft:lightning_bolt"]);

// ───────────── UTILIDADES ─────────────
const esElegido = (p) =>
  !NO_ELEGIDOS.some((n) => n.toLowerCase() === p.name.toLowerCase()) && !p.hasTag("fb_no_elegido");

function heldId(p) {
  const inv = p.getComponent("inventory")?.container;
  return inv?.getItem(p.selectedSlotIndex)?.typeId;
}
function hasInInventory(p, id) {
  const c = p.getComponent("inventory")?.container;
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
function elegirObjetivo(p, cono) {
  const head = p.getHeadLocation();
  const v = p.getViewDirection();
  let best, bestAng = Infinity;
  for (const e of p.dimension.getEntities({ location: head, maxDistance: AIM.RANGE })) {
    if (e.id === p.id || IGNORE_TYPES.has(e.typeId) || !e.getComponent("health")) continue;
    if (!AIM.TARGET_PLAYERS && e.typeId === "minecraft:player") continue;
    if (e.typeId === "minecraft:player" && e.getGameMode?.() === "spectator") continue;
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
  const lock = p.isSneaking; // agachado = aimbot total, de pie = aim assist
  const e = elegirObjetivo(p, lock ? 180 : AIM.ASSIST_CONE);
  if (!e) return;
  const head = p.getHeadLocation();
  const dx = e.location.x - head.x, dy = e.location.y + 0.9 - head.y, dz = e.location.z - head.z;
  const yaw = (-Math.atan2(dx, dz) * 180) / Math.PI;
  const pitch = (-Math.atan2(dy, Math.hypot(dx, dz)) * 180) / Math.PI;
  const cur = p.getRotation(), k = lock ? AIM.LOCK_SMOOTH : AIM.ASSIST_SMOOTH;
  p.setRotation({ x: Math.max(-90, Math.min(90, cur.x + (pitch - cur.x) * k)), y: cur.y + wrap(yaw - cur.y) * k });
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
    if (held === "fb:aim_mace") apuntar(p);

    // Núcleo Inmortal: pasivo desde el inventario
    if (system.currentTick % 5 === 0 && hasInInventory(p, "fb:immortal_core")) {
      const h = p.getComponent("health");
      if (h && h.currentValue > 0 && h.currentValue <= 6 && !enCooldown(p, "core", 600)) {
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
  const inv = p.getComponent("inventory")?.container;
  inv?.setItem(p.selectedSlotIndex, undefined); // un solo uso
  const eq = t.getComponent("equippable");
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
  if (id === "fb:restoration_orb") {
    ev.cancel = true;
    system.run(() => { if (!enCooldown(p, "orb", 40)) restaurarTodo(p); });
    return;
  }
  if (id === "fb:divine_heart") {
    ev.cancel = true;
    system.run(() => {
      if (enCooldown(p, "heart", 200)) return p.onScreenDisplay.setActionBar("§7Corazón Divino recargando...");
      const dur = 20 * 60 * 5;
      for (const [fx, amp] of [["regeneration", 9], ["strength", 9], ["resistance", 3], ["absorption", 9], ["speed", 2], ["saturation", 0], ["health_boost", 9]])
        p.addEffect(fx, dur, { amplifier: amp });
      p.getComponent("health")?.resetToMaxValue();
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
      if (e.typeId === "minecraft:item") s.stack = e.getComponent("item")?.itemStack;
    }
  }
  for (const [id, s] of snaps) if (ahora - s.seen > 600) snaps.delete(id); // limpieza
}, 10);

world.afterEvents.entityDie.subscribe((ev) => muertos.add(ev.deadEntity.id));

world.afterEvents.entityRemove.subscribe(({ removedEntityId: id }) => {
  const s = snaps.get(id); snaps.delete(id);
  if (!s) return;
  if (muertos.delete(id)) return;                                  // murió
  const ahora = system.currentTick;
  if (ahora - s.seen > 40) return;                                 // dato viejo / chunk descargado
  if (s.type === "minecraft:item" && ahora - s.first >= ITEM_LIFE) return; // expiró solo
  const cerca = world.getAllPlayers().filter((p) => p.dimension.id === s.dim);
  if (!cerca.length) return;                                       // nadie cerca: probable descarga de chunk
  if (cerca.some((p) => Math.hypot(p.location.x - s.loc.x, p.location.y - s.loc.y, p.location.z - s.loc.z) > SNAP_RANGE + 8)
      && cerca.every((p) => Math.hypot(p.location.x - s.loc.x, p.location.z - s.loc.z) > SNAP_RANGE)) return;
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

// ───────────── JEFE FINAL (guardián de DiamondHalo9031) ─────────────
// Aparece cuando el dueño queda con <= 3 corazones. Dura 90 s y reaparece tras 5 min.
// Es un Golem de Hierro hiper-potenciado que protege al dueño: castiga a los monstruos
// cercanos y a quien lo haya herido hace poco, y le da resistencia y regeneración.
let jefe = null, jefeFin = 0, proximoJefe = 0, ultimoAtacante = null, ultimoAtaque = 0;

const esDueno = (p) => p.name.toLowerCase() === DUENO.toLowerCase();

function invocarJefe(dueno) {
  const l = dueno.location, dim = dueno.dimension;
  jefe = dim.spawnEntity("minecraft:iron_golem", { x: l.x + 2, y: l.y, z: l.z + 2 });
  jefe.nameTag = "§4§lJefe Final";
  for (const [fx, amp] of [["health_boost", 49], ["instant_health", 20], ["resistance", 4], ["strength", 9], ["speed", 2], ["regeneration", 4]])
    jefe.addEffect(fx, BOSS.DURACION + 40, { amplifier: amp });
  jefeFin = system.currentTick + BOSS.DURACION;
  proximoJefe = system.currentTick + BOSS.ESPERA;
  dim.spawnParticle("minecraft:huge_explosion_emitter", jefe.location);
  world.playSound("mob.wither.spawn");
  world.sendMessage(`§4§l¡EL JEFE FINAL HA DESPERTADO! §r§7${dueno.name} está en máximo peligro.`);
  for (const [fx, amp] of [["resistance", 3], ["regeneration", 4], ["absorption", 4]]) dueno.addEffect(fx, 20 * 20, { amplifier: amp });
}

world.afterEvents.entityHurt.subscribe(({ hurtEntity: v, damageSource: d }) => {
  if (v.typeId !== "minecraft:player" || !esDueno(v) || !d.damagingEntity || d.damagingEntity.id === v.id) return;
  ultimoAtacante = d.damagingEntity; ultimoAtaque = system.currentTick;
});

system.runInterval(() => {
  const ahora = system.currentTick;
  const dueno = world.getAllPlayers().find(esDueno);
  if (!dueno) return;
  if (!jefe && ahora >= proximoJefe) {
    const h = dueno.getComponent("health");
    if (h && h.currentValue > 0 && h.currentValue <= BOSS.PELIGRO_HP) invocarJefe(dueno);
  }
  if (!jefe) return;
  let vivo = false; try { vivo = jefe.isValid; } catch {}
  if (!vivo) { jefe = null; return; }
  if (ahora >= jefeFin) {
    jefe.dimension.spawnParticle("minecraft:totem_particle", jefe.location);
    jefe.remove(); jefe = null;
    world.sendMessage("§7El Jefe Final se retira...");
    return;
  }
  const d = Math.hypot(jefe.location.x - dueno.location.x, jefe.location.z - dueno.location.z);
  if (jefe.dimension.id !== dueno.dimension.id || d > 14) jefe.teleport(dueno.location, { dimension: dueno.dimension });
  const enemigos = dueno.dimension.getEntities({ location: dueno.location, maxDistance: BOSS.RADIO, families: ["monster"] });
  if (ultimoAtacante && ahora - ultimoAtaque < 400) {
    try { if (ultimoAtacante.isValid && ultimoAtacante.dimension.id === dueno.dimension.id) enemigos.push(ultimoAtacante); } catch {}
  }
  for (const e of enemigos) {
    if (e.id === jefe.id || e.id === dueno.id) continue;
    try { e.applyDamage(BOSS.DANO, { cause: EntityDamageCause.entityAttack, damagingEntity: jefe }); } catch {}
  }
  dueno.onScreenDisplay.setActionBar(`§4Jefe Final §7— ${Math.ceil((jefeFin - ahora) / 20)}s`);
}, 10);

// ───────────── KIT: /scriptevent fb:kit ─────────────
system.afterEvents.scriptEventReceive.subscribe((ev) => {
  if (ev.id === "fb:boss" && ev.sourceEntity?.typeId === "minecraft:player") { if (!jefe) invocarJefe(ev.sourceEntity); return; } // prueba
  if (ev.id !== "fb:kit" || ev.sourceType !== "Entity" || ev.sourceEntity?.typeId !== "minecraft:player") return;
  for (const id of FB_ITEMS) ev.sourceEntity.runCommand(`give @s ${id}`);
});
