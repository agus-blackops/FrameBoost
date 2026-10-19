// Los 7 Pecados Capitales — poderes especiales de cada criatura.
import { world, system } from "@minecraft/server";

const NS = "pecados";
const DIMENSIONES = ["overworld", "nether", "the_end"];

function es(entidad, nombre) {
  return entidad?.typeId === `${NS}:${nombre}`;
}

function valida(entidad) {
  try {
    return entidad !== undefined && (typeof entidad.isValid === "function" ? entidad.isValid() : entidad.isValid);
  } catch {
    return false;
  }
}

function efecto(entidad, tipo, ticks, amplificador = 0) {
  try {
    entidad.addEffect(tipo, ticks, { amplifier: amplificador, showParticles: true });
  } catch {}
}

function distancia(a, b) {
  const dx = a.x - b.x, dy = a.y - b.y, dz = a.z - b.z;
  return Math.sqrt(dx * dx + dy * dy + dz * dz);
}

function curar(entidad, cantidad) {
  const vida = entidad.getComponent("minecraft:health");
  if (vida) vida.setCurrentValue(Math.min(vida.effectiveMax, vida.currentValue + cantidad));
}

function porcentajeVida(entidad) {
  const vida = entidad.getComponent("minecraft:health");
  return vida ? vida.currentValue / vida.effectiveMax : 1;
}

function aviso(jugador, texto) {
  try {
    jugador.sendMessage(texto);
  } catch {}
}

function sonido(entidad, id) {
  try {
    entidad.runCommandAsync(`playsound ${id} @a ~ ~ ~ 1 1`);
  } catch {}
}

function empujarHacia(victima, origen, horizontal, vertical) {
  let dx = origen.x - victima.location.x;
  let dz = origen.z - victima.location.z;
  const largo = Math.sqrt(dx * dx + dz * dz) || 1;
  try {
    victima.applyKnockback(dx / largo, dz / largo, horizontal, vertical);
  } catch {}
}

// ---------------------------------------------------------------------------
// Golpes de los pecados
// ---------------------------------------------------------------------------

function robarObjeto(avaro, jugador) {
  const inv = jugador.getComponent("minecraft:inventory")?.container;
  const bolsa = avaro.getComponent("minecraft:inventory")?.container;
  if (!inv || !bolsa || bolsa.emptySlotsCount === 0) return;
  const ranura = jugador.selectedSlotIndex ?? jugador.selectedSlot;
  const objeto = inv.getItem(ranura);
  if (!objeto) return;
  const sobrante = bolsa.addItem(objeto);
  inv.setItem(ranura, sobrante);
  efecto(avaro, "speed", 200, 2);
  sonido(avaro, "random.orb");
  aviso(jugador, "§6¡Avaricity te ha robado lo que tenías en la mano! §7Mátalo para recuperarlo.");
}

world.afterEvents.entityHitEntity.subscribe(({ damagingEntity: atacante, hitEntity: victima }) => {
  if (!valida(atacante) || !valida(victima)) return;
  const jugador = victima.typeId === "minecraft:player";

  if (es(atacante, "superbius")) {
    // Humillación: lanza a la víctima por los aires.
    empujarHacia(victima, {
      x: 2 * victima.location.x - atacante.location.x,
      z: 2 * victima.location.z - atacante.location.z,
    }, 0.6, 0.9);
    if (jugador) aviso(victima, "§5Superbius: §d«¡Arrodíllate ante tu rey!»");
  } else if (es(atacante, "iratus")) {
    try {
      victima.setOnFire(5, true);
    } catch {}
  } else if (es(atacante, "avaricity")) {
    if (jugador && Math.random() < 0.35) robarObjeto(atacante, victima);
  } else if (es(atacante, "envidium")) {
    // Roba tu fuerza y a veces aparece a tu espalda.
    efecto(atacante, "strength", 200, 0);
    if (Math.random() < 0.3) {
      try {
        const vista = victima.getViewDirection();
        const destino = {
          x: victima.location.x - vista.x * 2,
          y: victima.location.y,
          z: victima.location.z - vista.z * 2,
        };
        atacante.teleport(destino, { dimension: victima.dimension, facingLocation: victima.location });
        sonido(atacante, "mob.endermen.portal");
      } catch {}
    }
  } else if (es(atacante, "glotonix")) {
    curar(atacante, 3);
    if (jugador) aviso(victima, "§6Glotonix devora tu energía...");
  } else if (es(atacante, "lujurius")) {
    efecto(victima, "slowness", 60, 0);
  } else if (es(atacante, "perezoth")) {
    efecto(victima, "mining_fatigue", 120, 1);
  }
});

// ---------------------------------------------------------------------------
// Daño recibido por los pecados
// ---------------------------------------------------------------------------

world.afterEvents.entityHurt.subscribe(({ hurtEntity: herido, damage, damageSource }) => {
  if (!valida(herido)) return;
  const agresor = damageSource?.damagingEntity;

  if (es(herido, "iratus") && porcentajeVida(herido) < 0.5 && !herido.getEffect?.("strength")) {
    // Furia: más fuerte y rápido cuando está herido.
    efecto(herido, "strength", 300, 1);
    efecto(herido, "speed", 300, 1);
    sonido(herido, "mob.ravager.roar");
  } else if (es(herido, "superbius") && valida(agresor) && agresor.typeId === "minecraft:player") {
    // La soberbia devuelve parte de cada golpe.
    if (Math.random() < 0.3) {
      try {
        agresor.applyDamage(Math.max(1, Math.ceil(damage * 0.4)));
        aviso(agresor, "§5«¿Cómo te atreves a tocarme?»");
      } catch {}
    }
  } else if (es(herido, "envidium") && Math.random() < 0.25) {
    efecto(herido, "invisibility", 60, 0);
  }
});

// ---------------------------------------------------------------------------
// Auras (cada 2 segundos)
// ---------------------------------------------------------------------------

system.runInterval(() => {
  for (const id of DIMENSIONES) {
    let dim;
    try {
      dim = world.getDimension(id);
    } catch {
      continue;
    }
    for (const pecado of dim.getEntities({ families: ["pecado"] })) {
      try {
        aura(pecado, dim);
      } catch {}
    }
  }
}, 40);

function jugadoresCerca(dim, centro, radio) {
  return dim.getPlayers({ location: centro, maxDistance: radio });
}

function aura(pecado, dim) {
  const pos = pecado.location;

  if (es(pecado, "perezoth")) {
    for (const j of jugadoresCerca(dim, pos, 7)) {
      efecto(j, "slowness", 60, 1);
      efecto(j, "mining_fatigue", 60, 0);
    }
  } else if (es(pecado, "lujurius")) {
    for (const j of jugadoresCerca(dim, pos, 10)) {
      if (distancia(j.location, pos) > 2.5) empujarHacia(j, pos, 0.5, 0.05);
    }
  } else if (es(pecado, "glotonix")) {
    for (const objeto of dim.getEntities({ type: "minecraft:item", location: pos, maxDistance: 3 })) {
      objeto.kill();
      curar(pecado, 4);
      sonido(pecado, "random.burp");
    }
  } else if (es(pecado, "superbius")) {
    if (porcentajeVida(pecado) < 0.5) efecto(pecado, "regeneration", 50, 0);
  } else if (es(pecado, "envidium")) {
    if (Math.random() < 0.08) efecto(pecado, "invisibility", 60, 0);
  }
}

// ---------------------------------------------------------------------------
// Esencias: al beberlas otorgan el poder del pecado
// ---------------------------------------------------------------------------

const ESENCIAS = {
  soberbia: [["resistance", 600, 1], ["absorption", 600, 1]],
  avaricia: [["luck", 1200, 0], ["haste", 1200, 1]],
  lujuria: [["regeneration", 300, 1]],
  ira: [["strength", 600, 1], ["fire_resistance", 600, 0]],
  gula: [["saturation", 20, 4], ["health_boost", 1200, 1]],
  envidia: [["invisibility", 1200, 0], ["night_vision", 1200, 0]],
  pereza: [["slow_falling", 1200, 0], ["jump_boost", 1200, 1]],
};

world.afterEvents.itemCompleteUse.subscribe(({ itemStack, source }) => {
  const prefijo = `${NS}:esencia_`;
  if (!itemStack?.typeId.startsWith(prefijo)) return;
  for (const [tipo, ticks, amp] of ESENCIAS[itemStack.typeId.slice(prefijo.length)] ?? []) {
    efecto(source, tipo, ticks, amp);
  }
});
