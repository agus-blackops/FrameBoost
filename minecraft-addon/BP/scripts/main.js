import { world, system, EntityDamageCause } from "@minecraft/server";

// ───────────── CONFIGURACIÓN ─────────────
// Jugadores que NO son "el elegido": los objetos no funcionan para ellos.
// (Todos los demás jugadores sí son elegidos.) También sirve el tag "fb_no_elegido".
const NO_ELEGIDOS = ["FaultierLocket0"];

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
const FB_ITEMS = [...Object.keys(WEAPONS), "fb:divine_heart", "fb:immortal_core"];
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

// ───────────── USO DE OBJETOS ─────────────
world.beforeEvents.itemUse.subscribe((ev) => {
  const id = ev.itemStack.typeId, p = ev.source;
  if (!FB_ITEMS.includes(id)) return;
  if (!esElegido(p)) { ev.cancel = true; system.run(() => rechazar(p)); return; }
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

// ───────────── KIT: /scriptevent fb:kit ─────────────
system.afterEvents.scriptEventReceive.subscribe((ev) => {
  if (ev.id !== "fb:kit" || ev.sourceType !== "Entity" || ev.sourceEntity?.typeId !== "minecraft:player") return;
  for (const id of FB_ITEMS) ev.sourceEntity.runCommand(`give @s ${id}`);
});
